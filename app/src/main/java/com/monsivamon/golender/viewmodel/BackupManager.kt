package com.monsivamon.golender.viewmodel

import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.edit
import com.monsivamon.golender.data.CalendarRepository
import com.monsivamon.golender.data.Event
import com.monsivamon.golender.data.LocalEvent
import com.monsivamon.golender.data.SelectedCalendar
import com.monsivamon.golender.data.SelectedCalendarJson
import com.monsivamon.golender.data.dataStore
import com.monsivamon.golender.data.prefs.SettingsKeys
import com.monsivamon.golender.data.util.PhotoStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.time.LocalDate
import java.time.ZoneId
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

// 予定と設定のバックアップ／復元を担うマネージャ（JSON / ZIP 両対応）
class BackupManager(
    private val context: Context,
    private val repository: CalendarRepository,
) {
    // バックアップ／復元の結果を表す密封クラス
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

    // 予定と設定をエクスポートする（includePhotos=true で ZIP、false で JSON）
    suspend fun export(
        uri: Uri, calendarMode: CalendarMode, selectedCalendars: List<SelectedCalendar>, includePhotos: Boolean,
        onProgress: ((phase: String, current: Int, total: Int) -> Unit)? = null,
    ): Result = withContext(Dispatchers.IO) {
        try {
            if (includePhotos) {
                exportZip(uri, calendarMode, selectedCalendars, onProgress)
            } else {
                onProgress?.invoke("バックアップを作成中", 0, 0)
                exportJson(uri, calendarMode, selectedCalendars)
            }
        } catch (e: Exception) {
            Result.Failure("バックアップに失敗しました: ${e.message}")
        }
    }

    // 予定と設定を JSON 形式でエクスポートする
    private suspend fun exportJson(uri: Uri, calendarMode: CalendarMode, selectedCalendars: List<SelectedCalendar>): Result {
        val prefs = context.dataStore.data.first()
        val root = JSONObject()
        root.put("settings", BackupJsonUtils.buildSettingsJson(prefs))
        root.put("events", BackupJsonUtils.eventsToJson(collectEvents(calendarMode, selectedCalendars), null))
        context.contentResolver.openOutputStream(uri)?.use { it.write(root.toString().toByteArray()) }
        return Result.Success("バックアップが完了しました")
    }

    // 予定・設定・写真を ZIP 形式でエクスポートする
    private suspend fun exportZip(
        uri: Uri, calendarMode: CalendarMode, selectedCalendars: List<SelectedCalendar>,
        onProgress: ((phase: String, current: Int, total: Int) -> Unit)?,
    ): Result {
        // 写真付きは Golendar モード専用
        if (calendarMode != CalendarMode.GOLENDAR) {
            return Result.Failure("写真付きバックアップは Golendar モードでのみ利用できます")
        }
        val prefs = context.dataStore.data.first()
        val events = collectEvents(calendarMode, selectedCalendars)
        val photoMap = repository.getAllPhotosByEventId()

        // eventId -> 写真メタ情報の JSON を組み立てる
        val evPhotos = JSONObject()
        photoMap.forEach { (eventId, photos) ->
            val arr = org.json.JSONArray()
            photos.forEach { p ->
                arr.put(JSONObject().apply {
                    put("fileName", p.fileName); put("position", p.position)
                })
            }
            evPhotos.put(eventId.toString(), arr)
        }

        // data.json 用のルート JSON を組み立てる
        val data = JSONObject().apply {
            put("settings", BackupJsonUtils.buildSettingsJson(prefs))
            put("events", BackupJsonUtils.eventsToJson(events, evPhotos))
        }
        val photoCount = photoMap.values.sumOf { it.size }
        onProgress?.invoke("マニフェストを準備中", 0, photoCount)

        // manifest.json を組み立てる
        val manifest = JSONObject().apply {
            put("app", APP_MARKER)
            put("format_version", FORMAT_VERSION)
            put("has_photos", true)
            put("created_at", System.currentTimeMillis())
            put("event_count", events.size)
            put("photo_count", photoCount)
        }

        // ZIP ストリームに順次書き込む
        context.contentResolver.openOutputStream(uri)?.use { rawOut ->
            ZipOutputStream(BufferedOutputStream(rawOut)).use { zos ->
                // 1) マニフェスト
                onProgress?.invoke("マニフェストを書き込み中", 0, photoCount)
                zos.putNextEntry(ZipEntry(MANIFEST_ENTRY))
                zos.write(manifest.toString().toByteArray()); zos.closeEntry()
                // 2) データ本体
                onProgress?.invoke("データを書き込み中", 0, photoCount)
                zos.putNextEntry(ZipEntry(DATA_ENTRY))
                zos.write(data.toString().toByteArray()); zos.closeEntry()
                // 3) 写真ファイル（実体があるものだけ）
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

    // ファイルをインポートする（ZIP / JSON を自動判定）
    suspend fun import(
        uri: Uri, isAppend: Boolean, calendarMode: CalendarMode, selectedCalendars: List<SelectedCalendar>,
        targetCalendarId: Long? = null,
        onProgress: ((phase: String, current: Int, total: Int) -> Unit)? = null,
    ): Result = withContext(Dispatchers.IO) {
        try {
            // 先頭バイトが PK なら ZIP と判定する
            val h = readHeaderBytes(uri, 4)
            val isZip = h != null && h.size >= 4 &&
                    h[0] == 0x50.toByte() && h[1] == 0x4B.toByte() &&
                    (h[2] == 0x03.toByte() || h[2] == 0x05.toByte() || h[2] == 0x07.toByte())
            if (isZip) importZip(uri, isAppend, calendarMode, selectedCalendars, onProgress)
            else {
                onProgress?.invoke("インポート中", 0, 0)
                importJson(uri, isAppend, calendarMode, selectedCalendars, targetCalendarId)
            }
        } catch (e: Exception) {
            Result.Failure("復元に失敗しました: 不正なファイルです")
        }
    }

    // JSON ファイルから予定をインポートする
    private suspend fun importJson(
        uri: Uri, isAppend: Boolean, calendarMode: CalendarMode,
        selectedCalendars: List<SelectedCalendar>,
        targetCalendarId: Long?,
    ): Result {
        // Google モードでの上書き復元はデータ保護のため禁止する
        if (calendarMode == CalendarMode.GOOGLE && !isAppend) {
            return Result.Failure("Googleモードでは誤削除防止のため、追記のみ可能です")
        }
        // JSON 全体を読み込む
        val json = context.contentResolver.openInputStream(uri)?.use {
            it.bufferedReader().use { r -> r.readText() }
        } ?: return Result.Failure("ファイルが開けませんでした")
        val root = JSONObject(json)

        // settings の復元（テーマ・曜日色・選択カレンダー・祝日表示など）
        if (root.has("settings")) {
            val settings = root.getJSONObject("settings")
            applySettingsJson(settings)
            if (settings.has("selected_calendars")) {
                val jsonStr = settings.getString("selected_calendars")
                context.dataStore.edit { it[SettingsKeys.SELECTED_CALENDARS] = jsonStr }
            }
            if (settings.has("show_holidays")) {
                val show = settings.getBoolean("show_holidays")
                context.dataStore.edit { it[SettingsKeys.SHOW_HOLIDAYS] = show }
            }
        }

        // events の復元（祝日データは除外する）
        if (root.has("events")) {
            val imported = BackupJsonUtils.jsonToLocalEvents(root.getJSONArray("events"))
                .filter { it.description != LocalEvent.DESCRIPTION_HOLIDAY }
            if (calendarMode == CalendarMode.GOOGLE) {
                // Google モードは追記のみ。復元先カレンダー ID が指定されていればそこへ挿入する
                for (ev in imported) {
                    if (targetCalendarId != null) {
                        repository.insertEventWithCalendarId(
                            ev.title, ev.startTime, ev.endTime, ev.isAllDay,
                            ev.location, ev.description, ev.rrule, targetCalendarId,
                        )
                    } else {
                        repository.insertEvent(
                            ev.title, ev.startTime, ev.endTime, ev.isAllDay,
                            ev.location, ev.description, ev.rrule, null,
                        )
                    }
                }
            } else if (isAppend) repository.appendLocalEvents(imported)
            else repository.restoreLocalEvents(imported)
        }
        return Result.Success(if (isAppend) "追記が完了しました" else "復元が完了しました")
    }

    // ZIP ファイルから予定と写真をインポートする
    private suspend fun importZip(
        uri: Uri, isAppend: Boolean, calendarMode: CalendarMode, selectedCalendars: List<SelectedCalendar>,
        onProgress: ((phase: String, current: Int, total: Int) -> Unit)?,
    ): Result {
        // 写真付きは Golendar モード専用
        if (calendarMode != CalendarMode.GOLENDAR) {
            return Result.Failure("写真付きバックアップは Golendar モードでのみ復元できます")
        }
        // 上書き復元のときは既存写真を全削除する
        if (!isAppend) {
            PhotoStorage.deleteAllFiles(context)
            repository.deleteAllPhotoRecords()
        }
        // ZIP を順に走査して manifest/data/photos を取り出す
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
                    // マニフェスト検証（他アプリの ZIP を弾く）
                    name == MANIFEST_ENTRY -> {
                        val m = JSONObject(zis.readBytes().toString(Charsets.UTF_8))
                        if (m.optString("app") != APP_MARKER) {
                            return Result.Failure("このファイルは Golendar のバックアップではありません")
                        }
                        manifestOk = true
                        totalPhotos = m.optInt("photo_count", 0)
                        onProgress?.invoke("写真を展開中", 0, totalPhotos)
                    }
                    // データ本体
                    name == DATA_ENTRY -> dataJson = zis.readBytes().toString(Charsets.UTF_8)
                    // 写真ファイル（新ファイル名でアプリ内に保存する）
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

        // settings の復元
        if (root.has("settings")) {
            val settings = root.getJSONObject("settings")
            applySettingsJson(settings)
            if (settings.has("selected_calendars")) {
                val jsonStr = settings.getString("selected_calendars")
                context.dataStore.edit { it[SettingsKeys.SELECTED_CALENDARS] = jsonStr }
            }
            if (settings.has("show_holidays")) {
                val show = settings.getBoolean("show_holidays")
                context.dataStore.edit { it[SettingsKeys.SHOW_HOLIDAYS] = show }
            }
        }

        // events の復元（写真メタも同時に取り出す）
        if (root.has("events")) {
            val imported = BackupJsonUtils.jsonToLocalEventsWithPhotos(root.getJSONArray("events"), extracted)
            if (!isAppend) repository.restoreLocalEvents(emptyList())
            for ((ev, photos) in imported) {
                // 1 件ずつ挿入して採番された ID に対して写真レコードを登録する
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

    // 先頭 N バイトを読み取り、ファイル形式判定に使う
    private fun readHeaderBytes(uri: Uri, count: Int): ByteArray? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { input: java.io.InputStream ->
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

    // モードに応じて予定一覧を収集する（バックアップ対象）
    private suspend fun collectEvents(calendarMode: CalendarMode, selectedCalendars: List<SelectedCalendar>): List<Event> {
        if (calendarMode == CalendarMode.GOOGLE) {
            // 可視カレンダー ID のみを対象にする（祝日は除外）
            val visibleIds = selectedCalendars.filter { it.isVisible }.map { it.calendarId }
            val idsToQuery = if (visibleIds.isEmpty()) listOf(-1L) else visibleIds
            // 1970〜2100 年を対象に全件取得する
            val start = LocalDate.of(1970, 1, 1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val end = LocalDate.of(2100, 12, 31).atTime(23, 59, 59).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            return repository.getEventsForMonth(start, end, idsToQuery).filter { !it.isHolidayCalendar }
        } else {
            // ローカル予定（祝日を除く）を Event に変換する
            return repository.getAllLocalEvents()
                .filter { it.description != LocalEvent.DESCRIPTION_HOLIDAY }
                .map { Event(it.id, it.title, it.startTime, it.endTime, it.isAllDay,
                    LocalEvent.LOCAL_CALENDAR_ID, it.location, it.description, it.rrule) }
        }
    }

    // settings JSON の内容を DataStore に反映する
    private suspend fun applySettingsJson(s: JSONObject) {
        context.dataStore.edit { prefs ->
            if (s.has("theme_mode")) prefs[SettingsKeys.THEME] = s.getString("theme_mode")
            if (s.has("week_start_day")) prefs[SettingsKeys.WEEK_START] = s.getString("week_start_day")
            if (s.has("calendar_mode")) prefs[SettingsKeys.MODE] = s.getString("calendar_mode")
            if (s.has("calendar_bg_color")) prefs[SettingsKeys.BG_COLOR] = s.getString("calendar_bg_color")
            // 曜日ごとの色を復元する
            if (s.has("day_colors")) {
                val dc = s.getJSONObject("day_colors")
                java.time.DayOfWeek.values().forEach { d ->
                    if (dc.has(d.name)) prefs[SettingsKeys.dayColor(d)] = dc.getString(d.name)
                }
            }
        }
    }
}