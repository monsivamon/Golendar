package com.monsivamon.golender.viewmodel

import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.edit
import com.monsivamon.golender.data.CalendarRepository
import com.monsivamon.golender.data.Event
import com.monsivamon.golender.data.EventPhoto
import com.monsivamon.golender.data.LocalEvent
import com.monsivamon.golender.data.dataStore
import com.monsivamon.golender.data.prefs.SettingsKeys
import com.monsivamon.golender.data.util.PhotoStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.InputStream
import java.time.DayOfWeek
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

// 予定と設定のバックアップ／復元を担当するマネージャ（JSON / ZIP 両対応）。
class BackupManager(
    private val context: Context,
    private val repository: CalendarRepository,
) {
    // バックアップ／復元の結果を表す密封クラス。
    sealed class Result {
        data class Success(val message: String) : Result()
        data class Failure(val message: String) : Result()
    }

    private companion object {
        const val APP_MARKER = "golendar"
        const val FORMAT_VERSION = 1
        const val MANIFEST_ENTRY = "manifest.json"
        const val DATA_ENTRY = "data.json"
        const val PHOTO_DIR_PREFIX = "photos/"
    }

    // 予定と設定をエクスポートする（includePhotos=true でZIP、false でJSON）。
    suspend fun export(
        uri: Uri, calendarMode: CalendarMode, selectedAccount: String?, includePhotos: Boolean,
        onProgress: ((phase: String, current: Int, total: Int) -> Unit)? = null,
    ): Result = withContext(Dispatchers.IO) {
        try {
            if (includePhotos) {
                exportZip(uri, calendarMode, selectedAccount, onProgress)
            } else {
                onProgress?.invoke("バックアップを作成中", 0, 0)
                exportJson(uri, calendarMode, selectedAccount)
            }
        } catch (e: Exception) {
            Result.Failure("バックアップに失敗しました: ${e.message}")
        }
    }

    // 予定と設定をJSON形式でエクスポートする。
    private suspend fun exportJson(uri: Uri, calendarMode: CalendarMode, selectedAccount: String?): Result {
        val prefs = context.dataStore.data.first()
        val root = JSONObject()
        root.put("settings", buildSettingsJson(prefs))
        root.put("events", eventsToJson(collectEvents(calendarMode, selectedAccount), null))
        context.contentResolver.openOutputStream(uri)?.use { it.write(root.toString().toByteArray()) }
        return Result.Success("バックアップが完了しました")
    }

    // 予定と設定と写真をZIP形式でエクスポートする。
    private suspend fun exportZip(
        uri: Uri, calendarMode: CalendarMode, selectedAccount: String?,
        onProgress: ((phase: String, current: Int, total: Int) -> Unit)?,
    ): Result {
        if (calendarMode != CalendarMode.GOLENDAR) {
            return Result.Failure("写真付きバックアップは Golendar モードでのみ利用できます")
        }
        val prefs = context.dataStore.data.first()
        val events = collectEvents(calendarMode, selectedAccount)
        val photoMap = repository.getAllPhotosByEventId()

        val evPhotos = JSONObject()
        photoMap.forEach { (eventId, photos) ->
            val arr = JSONArray()
            photos.forEach { p ->
                arr.put(JSONObject().apply {
                    put("fileName", p.fileName); put("position", p.position)
                })
            }
            evPhotos.put(eventId.toString(), arr)
        }

        val data = JSONObject().apply {
            put("settings", buildSettingsJson(prefs))
            put("events", eventsToJson(events, evPhotos))
        }

        val photoCount = photoMap.values.sumOf { it.size }
        onProgress?.invoke("マニフェストを準備中", 0, photoCount)
        val manifest = JSONObject().apply {
            put("app", APP_MARKER)
            put("format_version", FORMAT_VERSION)
            put("has_photos", true)
            put("created_at", System.currentTimeMillis())
            put("event_count", events.size)
            put("photo_count", photoCount)
        }

        context.contentResolver.openOutputStream(uri)?.use { rawOut ->
            ZipOutputStream(BufferedOutputStream(rawOut)).use { zos ->
                onProgress?.invoke("マニフェストを書き込み中", 0, photoCount)
                zos.putNextEntry(ZipEntry(MANIFEST_ENTRY))
                zos.write(manifest.toString().toByteArray()); zos.closeEntry()

                onProgress?.invoke("データを書き込み中", 0, photoCount)
                zos.putNextEntry(ZipEntry(DATA_ENTRY))
                zos.write(data.toString().toByteArray()); zos.closeEntry()

                var donePhotos = 0
                photoMap.values.flatten().forEach { p ->
                    val f = PhotoStorage.getFile(context, p.fileName)
                    if (f.exists()) {
                        zos.putNextEntry(ZipEntry("photos/${p.fileName}"))
                        f.inputStream().use { it.copyTo(zos) }
                        zos.closeEntry()
                    }
                    donePhotos++
                    onProgress?.invoke("写真を書き出し中", donePhotos, photoCount)
                }
                zos.finish()
            }
        }
        return Result.Success("バックアップが完了しました（イベント ${events.size} 件 / 写真 $photoCount 枚）")
    }

    // ファイルをインポートする（ZIP / JSON を自動判定）。
    suspend fun import(
        uri: Uri, isAppend: Boolean, calendarMode: CalendarMode, selectedAccount: String?,
        onProgress: ((phase: String, current: Int, total: Int) -> Unit)? = null,
    ): Result = withContext(Dispatchers.IO) {
        try {
            val h = readHeaderBytes(uri, 4)
            val isZip = h != null && h.size >= 4 &&
                    h[0] == 0x50.toByte() && h[1] == 0x4B.toByte() &&
                    (h[2] == 0x03.toByte() || h[2] == 0x05.toByte() || h[2] == 0x07.toByte())
            if (isZip) importZip(uri, isAppend, calendarMode, selectedAccount, onProgress)
            else {
                onProgress?.invoke("インポート中", 0, 0)
                importJson(uri, isAppend, calendarMode, selectedAccount)
            }
        } catch (e: Exception) {
            Result.Failure("復元に失敗しました: 不正なファイルです")
        }
    }

    // JSONファイルから予定をインポートする。
    private suspend fun importJson(uri: Uri, isAppend: Boolean, calendarMode: CalendarMode, selectedAccount: String?): Result {
        if (calendarMode == CalendarMode.GOOGLE && !isAppend) {
            return Result.Failure("Googleモードでは誤削除防止のため、追記のみ可能です")
        }
        val json = context.contentResolver.openInputStream(uri)?.use {
            it.bufferedReader().use { r -> r.readText() }
        } ?: return Result.Failure("ファイルが開けませんでした")
        val root = JSONObject(json)
        if (root.has("settings")) applySettingsJson(root.getJSONObject("settings"))
        if (root.has("events")) {
            val imported = jsonToLocalEvents(root.getJSONArray("events"))
                .filter { it.description != LocalEvent.DESCRIPTION_HOLIDAY }
            if (calendarMode == CalendarMode.GOOGLE) {
                for (ev in imported) repository.insertEvent(
                    ev.title, ev.startTime, ev.endTime, ev.isAllDay,
                    ev.location, ev.description, ev.rrule, selectedAccount,
                )
            } else if (isAppend) repository.appendLocalEvents(imported)
            else repository.restoreLocalEvents(imported)
        }
        return Result.Success(if (isAppend) "追記が完了しました" else "復元が完了しました")
    }

    // ZIPファイルから予定と写真をインポートする。
    private suspend fun importZip(
        uri: Uri, isAppend: Boolean, calendarMode: CalendarMode, selectedAccount: String?,
        onProgress: ((phase: String, current: Int, total: Int) -> Unit)?,
    ): Result {
        if (calendarMode != CalendarMode.GOLENDAR) {
            return Result.Failure("写真付きバックアップは Golendar モードでのみ復元できます")
        }
        if (!isAppend) {
            PhotoStorage.deleteAllFiles(context)
            repository.deleteAllPhotoRecords()
        }

        var manifestOk = false
        var dataJson: String? = null
        val extracted = mutableMapOf<String, String>()
        var totalPhotos = 0
        var donePhotos = 0
        onProgress?.invoke("ファイルを準備中", 0, 0)

        context.contentResolver.openInputStream(uri)?.use { rawIn ->
            val zis = ZipInputStream(BufferedInputStream(rawIn))
            var entry: ZipEntry? = zis.nextEntry
            while (entry != null) {
                val name = entry.name
                when {
                    name == MANIFEST_ENTRY -> {
                        val m = JSONObject(zis.readBytes().toString(Charsets.UTF_8))
                        if (m.optString("app") != APP_MARKER) {
                            return Result.Failure("このファイルは Golendar のバックアップではありません")
                        }
                        manifestOk = true
                        totalPhotos = m.optInt("photo_count", 0)
                        onProgress?.invoke("写真を展開中", 0, totalPhotos)
                    }
                    name == DATA_ENTRY -> dataJson = zis.readBytes().toString(Charsets.UTF_8)
                    name.startsWith(PHOTO_DIR_PREFIX) && !entry.isDirectory -> {
                        val orig = name.removePrefix(PHOTO_DIR_PREFIX)
                        val newName = PhotoStorage.saveBytes(context, zis.readBytes())
                        if (newName != null) extracted[orig] = newName
                        donePhotos++
                        onProgress?.invoke("写真を展開中", donePhotos, totalPhotos)
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        if (!manifestOk) return Result.Failure("このファイルは Golendar のバックアップではありません")
        val json = dataJson ?: return Result.Failure("バックアップデータが破損しています")

        val root = JSONObject(json)
        if (root.has("settings")) applySettingsJson(root.getJSONObject("settings"))
        if (root.has("events")) {
            val imported = jsonToLocalEventsWithPhotos(root.getJSONArray("events"), extracted)
            if (!isAppend) repository.restoreLocalEvents(emptyList())
            for ((ev, photos) in imported) {
                val newId = repository.insertLocalEvent(
                    ev.title, ev.startTime, ev.endTime, ev.isAllDay,
                    ev.location, ev.description, ev.rrule,
                )
                photos.forEachIndexed { i, (fn, _) -> repository.importPhotoRecord(newId, fn, i) }
            }
        }
        return Result.Success(
            if (isAppend) "追記が完了しました（写真 ${extracted.size} 枚）"
            else "復元が完了しました（写真 ${extracted.size} 枚）"
        )
    }

    // 先頭Nバイトを読み取り、ファイル形式判定に使う。
    private fun readHeaderBytes(uri: Uri, count: Int): ByteArray? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { input: InputStream ->
                val buf = ByteArray(count)
                var read = 0
                while (read < count) {
                    val r = input.read(buf, read, count - read)
                    if (r <= 0) break
                    read += r
                }
                if (read <= 0) null else buf.copyOf(read)
            }
        } catch (_: Exception) { null }
    }

    // カレンダーモードに応じて予定一覧を収集する。
    private suspend fun collectEvents(calendarMode: CalendarMode, selectedAccount: String?): List<Event> =
        if (calendarMode == CalendarMode.GOOGLE) {
            repository.getAllGoogleEvents(selectedAccount).filter { !it.isHolidayCalendar }
        } else {
            repository.getAllLocalEvents()
                .filter { it.description != LocalEvent.DESCRIPTION_HOLIDAY }
                .map { Event(it.id, it.title, it.startTime, it.endTime, it.isAllDay,
                    LocalEvent.LOCAL_CALENDAR_ID, it.location, it.description, it.rrule) }
        }

    // DataStoreの設定をJSONオブジェクトに変換する。
    private fun buildSettingsJson(prefs: androidx.datastore.preferences.core.Preferences): JSONObject {
        val s = JSONObject()
        s.put("theme_mode", prefs[SettingsKeys.THEME] ?: "SYSTEM")
        s.put("week_start_day", prefs[SettingsKeys.WEEK_START] ?: "SUNDAY")
        s.put("calendar_mode", prefs[SettingsKeys.MODE] ?: "GOLENDAR")
        s.put("calendar_bg_color", prefs[SettingsKeys.BG_COLOR] ?: SettingsKeys.COLOR_UNSPECIFIED)
        val dc = JSONObject()
        DayOfWeek.values().forEach { d ->
            dc.put(d.name, prefs[SettingsKeys.dayColor(d)] ?: SettingsKeys.COLOR_UNSPECIFIED)
        }
        s.put("day_colors", dc)
        return s
    }

    // JSONの設定をDataStoreに反映する。
    private suspend fun applySettingsJson(s: JSONObject) {
        context.dataStore.edit { prefs ->
            if (s.has("theme_mode")) prefs[SettingsKeys.THEME] = s.getString("theme_mode")
            if (s.has("week_start_day")) prefs[SettingsKeys.WEEK_START] = s.getString("week_start_day")
            if (s.has("calendar_mode")) prefs[SettingsKeys.MODE] = s.getString("calendar_mode")
            if (s.has("calendar_bg_color")) prefs[SettingsKeys.BG_COLOR] = s.getString("calendar_bg_color")
            if (s.has("day_colors")) {
                val dc = s.getJSONObject("day_colors")
                DayOfWeek.values().forEach { d ->
                    if (dc.has(d.name)) prefs[SettingsKeys.dayColor(d)] = dc.getString(d.name)
                }
            }
        }
    }

    // 予定リストをJSON配列に変換する。
    private fun eventsToJson(events: List<Event>, evPhotos: JSONObject?): JSONArray {
        val arr = JSONArray()
        for (e in events) {
            val o = JSONObject()
            o.put("id", e.id); o.put("title", e.title)
            o.put("startTime", e.startTime); o.put("endTime", e.endTime)
            o.put("isAllDay", e.isAllDay); o.put("location", e.location)
            o.put("description", e.description); o.put("rrule", e.rrule ?: JSONObject.NULL)
            if (evPhotos != null && evPhotos.has(e.id.toString())) {
                o.put("photos", evPhotos.getJSONArray(e.id.toString()))
            }
            arr.put(o)
        }
        return arr
    }

    // JSON配列をLocalEventリストに変換する。
    private fun jsonToLocalEvents(arr: JSONArray): List<LocalEvent> {
        val list = mutableListOf<LocalEvent>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            list.add(LocalEvent(0, o.getString("title"), o.getLong("startTime"),
                o.getLong("endTime"), o.getBoolean("isAllDay"),
                o.optString("location", ""), o.optString("description", ""),
                if (o.isNull("rrule")) null else o.getString("rrule")))
        }
        return list
    }

    // ZIP用: 予定と写真メタ情報を同時に取り出す。
    private fun jsonToLocalEventsWithPhotos(
        arr: JSONArray, extracted: Map<String, String>,
    ): List<Pair<LocalEvent, List<Pair<String, Int>>>> {
        val list = mutableListOf<Pair<LocalEvent, List<Pair<String, Int>>>>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val ev = LocalEvent(0, o.getString("title"), o.getLong("startTime"),
                o.getLong("endTime"), o.getBoolean("isAllDay"),
                o.optString("location", ""), o.optString("description", ""),
                if (o.isNull("rrule")) null else o.getString("rrule"))
            val photos = mutableListOf<Pair<String, Int>>()
            if (o.has("photos")) {
                val pa = o.getJSONArray("photos")
                for (j in 0 until pa.length()) {
                    val p = pa.getJSONObject(j)
                    val orig = p.getString("fileName")
                    val nn = extracted[orig] ?: continue
                    photos.add(nn to p.optInt("position", j))
                }
            }
            list.add(ev to photos)
        }
        return list
    }
}