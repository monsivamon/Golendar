package com.monsivamon.golender.viewmodel

import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.edit
import com.monsivamon.golender.R
import com.monsivamon.golender.data.CalendarRepository
import com.monsivamon.golender.data.Event
import com.monsivamon.golender.data.LocalCalendar
import com.monsivamon.golender.data.LocalEvent
import com.monsivamon.golender.data.SelectedCalendar
import com.monsivamon.golender.data.dataStore
import com.monsivamon.golender.data.LocalCalendarNames
import com.monsivamon.golender.data.prefs.LanguageSetting
import com.monsivamon.golender.data.prefs.SettingsKeys
import com.monsivamon.golender.data.util.PhotoStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.time.LocalDate
import java.time.ZoneId
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

// 予定と設定のバックアップ／復元を担うマネージャ（JSON / ZIP 両対応）
// 可視カレンダーを一括でバックアップし、復元時は「復元先 + 復元する元カレンダー」を選べる。
class BackupManager(
    private val context: Context,
    private val repository: CalendarRepository,
) {
    // ユーザー向け文字列の解決先 Context。
    // ViewModel にはシステムロケールの Application Context が渡り、Activity 再生成でも
    // ViewModel は破棄されないため、文字列の解決時に毎回アプリ内言語を差し込む
    // （DataStore は需求後にメモリキャッシュされるため低コスト）。
    private val uic: Context
        get() = LanguageSetting.wrap(context)

    sealed class Result {
        data class Success(val message: String) : Result()
        data class Failure(val message: String) : Result()
    }

    // バックアップファイルのメタ情報（復元前のプレビュー用）
    data class Metadata(
        val isZip: Boolean,
        val sourceCalendars: List<SourceCalendarInfo>,
        val eventCount: Int,
        val photoCount: Int,
        val isLegacyFormat: Boolean = false,
    )

    // バックアップに含まれる元カレンダー 1 件分の情報
    data class SourceCalendarInfo(
        val id: Long,
        val name: String,
        val colorArgb: Int,
    )

    // 復元時のユーザー選択
    data class RestoreOptions(
        val targetGoogleCalendarId: Long?,          // Google モード時
        val targetLocalCalendarId: Long?,           // Golendar モード時
        val selectedSourceCalendarIds: Set<Long>?,  // null = 全て選択
        // Golendar モード時のみ有効:
        // バックアップ内のカレンダー名でローカルカレンダーに振り分け、
        // 同名が無ければ新規作成する
        val autoCreateMissingCalendars: Boolean = false,
    )

    private companion object {
        const val APP_MARKER = "golendar"
        const val FORMAT_VERSION = 2
        const val MANIFEST_ENTRY = "manifest.json"
        const val DATA_ENTRY = "data.json"
        const val PHOTO_DIR_PREFIX = "photos/"
    }

    // ============================================================
    // エクスポート
    // ============================================================
    suspend fun export(
        uri: Uri, calendarMode: CalendarMode, selectedCalendars: List<SelectedCalendar>,
        includePhotos: Boolean,
        onProgress: ((phase: String, current: Int, total: Int) -> Unit)? = null,
    ): Result = withContext(Dispatchers.IO) {
        try {
            if (includePhotos) exportZip(uri, calendarMode, selectedCalendars, onProgress)
            else {
                onProgress?.invoke(uic.getString(R.string.vm_progress_creating_backup), 0, 0)
                exportJson(uri, calendarMode, selectedCalendars)
            }
        } catch (e: Exception) {
            Result.Failure(uic.getString(R.string.vm_backup_failed, e.message ?: uic.getString(R.string.vm_unknown_error)))
        }
    }

    private suspend fun exportJson(
        uri: Uri, calendarMode: CalendarMode, selectedCalendars: List<SelectedCalendar>,
    ): Result {
        val prefs = context.dataStore.data.first()
        val (events, sources) = collectEventsWithSources(calendarMode, selectedCalendars)
        val root = JSONObject().apply {
            put("schema_version", FORMAT_VERSION)
            put("settings", BackupJsonUtils.buildSettingsJson(prefs))
            put("source_calendars", sources)
            put("events", BackupJsonUtils.eventsToJson(events, null))
            if (calendarMode == CalendarMode.GOLENDAR) {
                put("local_calendars", BackupJsonUtils.localCalendarsToJson(repository.getAllLocalCalendars()))
            }
        }
        context.contentResolver.openOutputStream(uri)?.use { it.write(root.toString().toByteArray()) }
        return Result.Success(uic.getString(R.string.vm_backup_complete_calendars, sources.length(), events.size))
    }

    private suspend fun exportZip(
        uri: Uri, calendarMode: CalendarMode, selectedCalendars: List<SelectedCalendar>,
        onProgress: ((phase: String, current: Int, total: Int) -> Unit)?,
    ): Result {
        if (calendarMode != CalendarMode.GOLENDAR) {
            return Result.Failure(uic.getString(R.string.vm_backup_zip_google_only_export))
        }
        val prefs = context.dataStore.data.first()
        val (events, sources) = collectEventsWithSources(calendarMode, selectedCalendars)
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
            put("schema_version", FORMAT_VERSION)
            put("settings", BackupJsonUtils.buildSettingsJson(prefs))
            put("source_calendars", sources)
            put("events", BackupJsonUtils.eventsToJson(events, evPhotos))
            put("local_calendars", BackupJsonUtils.localCalendarsToJson(repository.getAllLocalCalendars()))
        }
        val photoCount = photoMap.values.sumOf { it.size }
        onProgress?.invoke(uic.getString(R.string.vm_progress_preparing_manifest), 0, photoCount)

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
                onProgress?.invoke(uic.getString(R.string.vm_progress_writing_manifest), 0, photoCount)
                zos.putNextEntry(ZipEntry(MANIFEST_ENTRY))
                zos.write(manifest.toString().toByteArray()); zos.closeEntry()

                onProgress?.invoke(uic.getString(R.string.vm_progress_writing_data), 0, photoCount)
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
                    onProgress?.invoke(uic.getString(R.string.vm_progress_writing_photos), donePhotos, photoCount)
                }
                zos.finish()
            }
        }
        return Result.Success(uic.getString(R.string.vm_backup_complete_calendars_photos, sources.length(), events.size, photoCount))
    }

    // 可視カレンダーのみを対象に、イベントと source_calendars を同時収集する
    private suspend fun collectEventsWithSources(
        calendarMode: CalendarMode,
        selectedCalendars: List<SelectedCalendar>,
    ): Pair<List<Event>, JSONArray> {
        val sources = JSONArray()
        if (calendarMode == CalendarMode.GOOGLE) {
            val visible = selectedCalendars.filter { it.isVisible }
            if (visible.isEmpty()) return emptyList<Event>() to sources
            for (cal in visible) {
                sources.put(JSONObject().apply {
                    put("id", cal.calendarId)
                    put("name", cal.displayName.ifBlank { uic.getString(R.string.vm_unnamed_calendar) })
                    put("colorArgb", cal.colorArgb)
                })
            }
            val start = LocalDate.of(1970, 1, 1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val end = LocalDate.of(2100, 12, 31).atTime(23, 59, 59).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val ids = visible.map { it.calendarId }
            val events = repository.getEventsForMonth(start, end, ids).filter { !it.isHolidayCalendar }
            return events to sources
        } else {
            val visibleCals = repository.getAllLocalCalendars().filter { it.isVisible }
            if (visibleCals.isEmpty()) return emptyList<Event>() to sources
            for (cal in visibleCals) {
                sources.put(JSONObject().apply {
                    put("id", cal.id)
                    // 既定カレンダーの空文字センチネルは locale 名に展開して書き出す
                    put("name", LocalCalendarNames.resolveExport(context, cal))
                    put("colorArgb", cal.colorArgb)
                })
            }
            val visibleIds = visibleCals.map { it.id }.toSet()
            val events = repository.getAllLocalEvents()
                .filter { it.description != LocalEvent.DESCRIPTION_HOLIDAY }
                .filter { it.calendarId in visibleIds }
                .map { Event(it.id, it.title, it.startTime, it.endTime, it.isAllDay,
                    it.calendarId, it.location, it.description, it.rrule,
                    colorArgb = it.colorArgb) }
            return events to sources
        }
    }

    // ============================================================
    // ピーク（復元前のプレビュー）
    // ============================================================
    suspend fun peek(uri: Uri): Metadata? = withContext(Dispatchers.IO) {
        try {
            val h = readHeaderBytes(uri, 4)
            val isZip = h != null && h.size >= 4 &&
                    h[0] == 0x50.toByte() && h[1] == 0x4B.toByte() &&
                    (h[2] == 0x03.toByte() || h[2] == 0x05.toByte() || h[2] == 0x07.toByte())
            if (isZip) peekZip(uri) else peekJson(uri)
        } catch (_: Exception) { null }
    }

    private fun peekJson(uri: Uri): Metadata? {
        val json = context.contentResolver.openInputStream(uri)?.use {
            it.bufferedReader().use { r -> r.readText() }
        } ?: return null
        val root = JSONObject(json)
        val events = root.optJSONArray("events") ?: JSONArray()
        val sources = extractSourceCalendars(root, events)
        val legacy = !hasSourceCalendars(root)
        return Metadata(isZip = false, sourceCalendars = sources, eventCount = events.length(), photoCount = 0, isLegacyFormat = legacy)
    }

    private fun peekZip(uri: Uri): Metadata? {
        var manifest: JSONObject? = null
        var data: JSONObject? = null
        context.contentResolver.openInputStream(uri)?.use { rawIn ->
            val zis = ZipInputStream(BufferedInputStream(rawIn))
            var entry: ZipEntry? = zis.nextEntry
            while (entry != null) {
                when (entry.name) {
                    MANIFEST_ENTRY -> manifest = JSONObject(zis.readBytes().toString(Charsets.UTF_8))
                    DATA_ENTRY -> data = JSONObject(zis.readBytes().toString(Charsets.UTF_8))
                }
                zis.closeEntry()
                entry = zis.nextEntry
                if (manifest != null && data != null) break
            }
        }
        if (manifest?.optString("app") != APP_MARKER) return null
        val root = data ?: return null
        val events = root.optJSONArray("events") ?: JSONArray()
        val sources = extractSourceCalendars(root, events)
        return Metadata(
            isZip = true,
            sourceCalendars = sources,
            eventCount = events.length(),
            photoCount = manifest.optInt("photo_count", 0),
            isLegacyFormat = !hasSourceCalendars(root),
        )
    }

    // source_calendars を読む。無ければ events の calendarId から合成する（1.1.6 互換）
    private fun extractSourceCalendars(root: JSONObject, events: JSONArray): List<SourceCalendarInfo> {
        val arr = root.optJSONArray("source_calendars")
        if (arr != null && arr.length() > 0) {
            val list = mutableListOf<SourceCalendarInfo>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list.add(SourceCalendarInfo(
                    id = o.optLong("id", 0L),
                    name = o.optString("name", uic.getString(R.string.vm_unnamed_calendar)),
                    colorArgb = o.optInt("colorArgb", 0),
                ))
            }
            return list
        }
        // 旧形式は常に「マイカレンダー（既定）」扱い。
        // 復元先は RestoreOptionsDialog を経由せず、BackupSection 側で
        // 既定ローカルカレンダーに固定される（isLegacyFormat=true）。
        return listOf(SourceCalendarInfo(0L, uic.getString(R.string.vm_my_calendar), 0))
    }

    // source_calendars を持つ新形式かどうかを判定する（旧形式なら false）
    private fun hasSourceCalendars(root: JSONObject): Boolean {
        val arr = root.optJSONArray("source_calendars")
        return arr != null && arr.length() > 0
    }

    // ============================================================
    // インポート
    // ============================================================
    suspend fun import(
        uri: Uri, isAppend: Boolean, calendarMode: CalendarMode,
        selectedCalendars: List<SelectedCalendar>,
        options: RestoreOptions,
        onProgress: ((phase: String, current: Int, total: Int) -> Unit)? = null,
    ): Result = withContext(Dispatchers.IO) {
        try {
            val h = readHeaderBytes(uri, 4)
            val isZip = h != null && h.size >= 4 &&
                    h[0] == 0x50.toByte() && h[1] == 0x4B.toByte() &&
                    (h[2] == 0x03.toByte() || h[2] == 0x05.toByte() || h[2] == 0x07.toByte())
            if (isZip) importZip(uri, isAppend, calendarMode, selectedCalendars, options, onProgress)
            else {
                onProgress?.invoke(uic.getString(R.string.vm_progress_importing), 0, 0)
                importJson(uri, isAppend, calendarMode, selectedCalendars, options, onProgress)
            }
        } catch (e: Exception) {
            Result.Failure(uic.getString(R.string.vm_restore_failed, e.message ?: uic.getString(R.string.vm_unknown_error)))
        }
    }

    private suspend fun importJson(
        uri: Uri, isAppend: Boolean, calendarMode: CalendarMode,
        selectedCalendars: List<SelectedCalendar>,
        options: RestoreOptions,
        onProgress: ((phase: String, current: Int, total: Int) -> Unit)?,
    ): Result {
        // Google モードでの上書き復元は対象カレンダーを選択できる場合のみ許可
        if (calendarMode == CalendarMode.GOOGLE && !isAppend && options.targetGoogleCalendarId == null) {
            return Result.Failure(uic.getString(R.string.vm_select_restore_target_google))
        }
        val json = context.contentResolver.openInputStream(uri)?.use {
            it.bufferedReader().use { r -> r.readText() }
        } ?: return Result.Failure(uic.getString(R.string.vm_file_not_opened))
        val root = JSONObject(json)

        // 復元時のみ設定を反映する（追記時は現状設定を保持）。
        // 旧形式バックアップでの calendar_mode の意図しない上書きを防ぐため、
        // 新形式（source_calendars 持ち）のときだけモードを復元する。
        if (!isAppend && root.has("settings")) {
            applySettingsJson(root.getJSONObject("settings"), hasSourceCalendars(root))
        }

        val eventsArr = root.optJSONArray("events") ?: JSONArray()
        val sources = extractSourceCalendars(root, eventsArr)
        val selected = options.selectedSourceCalendarIds ?: sources.map { it.id }.toSet()

        return applyEvents(
            events = BackupJsonUtils.jsonToLocalEvents(eventsArr),
            sources = sources,
            selectedSourceIds = selected,
            isAppend = isAppend,
            calendarMode = calendarMode,
            options = options,
        )
    }

    private suspend fun importZip(
        uri: Uri, isAppend: Boolean, calendarMode: CalendarMode,
        selectedCalendars: List<SelectedCalendar>,
        options: RestoreOptions,
        onProgress: ((phase: String, current: Int, total: Int) -> Unit)?,
    ): Result {
        if (calendarMode != CalendarMode.GOLENDAR) {
            return Result.Failure(uic.getString(R.string.vm_backup_zip_google_only_restore))
        }
        if (options.targetLocalCalendarId == null && !options.autoCreateMissingCalendars) {
            return Result.Failure(uic.getString(R.string.vm_select_restore_target))
        }
        // 上書き復元の既存写真の掃除はしない。
        // overwrite 対象カレンダーの既存予定は下の削除フェーズで deleteLocalEvent により
        // 写真ファイル・レコードごと消えるため、ここで全消去すると
        // 復元対象外カレンダーの写真まで失われる（全消去は撤去済み）。
        // 新規抽出ファイルの名前はユニークなので解析中に既存写真と衝突しない。
        var manifestOk = false
        var dataJson: String? = null
        val extracted = mutableMapOf<String, String>()
        var totalPhotos = 0
        var donePhotos = 0
        onProgress?.invoke(uic.getString(R.string.vm_progress_preparing_file), 0, 0)
        context.contentResolver.openInputStream(uri)?.use { rawIn ->
            val zis = ZipInputStream(BufferedInputStream(rawIn))
            var entry: ZipEntry? = zis.nextEntry
            while (entry != null) {
                val name = entry.name
                when {
                    name == MANIFEST_ENTRY -> {
                        val m = JSONObject(zis.readBytes().toString(Charsets.UTF_8))
                        if (m.optString("app") != APP_MARKER) {
                            return Result.Failure(uic.getString(R.string.vm_not_golendar_backup))
                        }
                        manifestOk = true
                        totalPhotos = m.optInt("photo_count", 0)
                        onProgress?.invoke(uic.getString(R.string.vm_progress_extracting_photos), 0, totalPhotos)
                    }
                    name == DATA_ENTRY -> dataJson = zis.readBytes().toString(Charsets.UTF_8)
                    name.startsWith(PHOTO_DIR_PREFIX) && !entry.isDirectory -> {
                        val orig = name.removePrefix(PHOTO_DIR_PREFIX)
                        val newName = PhotoStorage.saveBytes(context, zis.readBytes())
                        if (newName != null) extracted[orig] = newName
                        donePhotos++
                        onProgress?.invoke(uic.getString(R.string.vm_progress_extracting_photos), donePhotos, totalPhotos)
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        if (!manifestOk) return Result.Failure(uic.getString(R.string.vm_not_golendar_backup))
        val json = dataJson ?: return Result.Failure(uic.getString(R.string.vm_backup_data_corrupted))
        val root = JSONObject(json)

        if (!isAppend && root.has("settings")) {
            applySettingsJson(root.getJSONObject("settings"), hasSourceCalendars(root))
        }

        val eventsArr = root.optJSONArray("events") ?: JSONArray()
        val sources = extractSourceCalendars(root, eventsArr)
        val selected = options.selectedSourceCalendarIds ?: sources.map { it.id }.toSet()

        val pairs = BackupJsonUtils.jsonToLocalEventsWithPhotos(eventsArr, extracted)

        // ---- Golendar: 同名カレンダー自動作成モード ----
        if (options.autoCreateMissingCalendars) {
            val auto = buildAutoCreateMapping(sources, selected)
                ?: return Result.Failure(uic.getString(R.string.vm_my_calendar_not_found))
            if (!isAppend) {
                val ids = repository.getAllLocalEvents()
                    .filter { it.calendarId in auto.allTargetIds }
                    .map { it.id }
                repository.withTransaction {
                    ids.forEach { repository.deleteLocalEventRecordOnly(it) }
                }
            }
            val sigsByTarget = mutableMapOf<Long, MutableSet<String>>()
            if (isAppend) {
                for (tid in auto.allTargetIds) {
                    val sigs = mutableSetOf<String>()
                    repository.getAllLocalEvents().filter { it.calendarId == tid }.forEach { ex ->
                        sigs.add(eventSignature(ex.title, ex.startTime, ex.endTime, ex.isAllDay))
                    }
                    sigsByTarget[tid] = sigs
                }
            }
            var inserted = 0
            repository.withTransaction {
                for ((ev, photos) in pairs) {
                    if (ev.description == LocalEvent.DESCRIPTION_HOLIDAY) continue
                    if (ev.calendarId !in selected) continue
                    val targetId = auto.mapping[ev.calendarId] ?: auto.defaultTargetId
                    val sigs = sigsByTarget.getOrPut(targetId) { mutableSetOf() }
                    val sig = eventSignature(ev.title, ev.startTime, ev.endTime, ev.isAllDay)
                    if (isAppend && sig in sigs) continue
                    val newId = repository.insertLocalEvent(
                        ev.title, ev.startTime, ev.endTime, ev.isAllDay,
                        ev.location, ev.description, ev.rrule, targetId,
                        colorArgb = ev.colorArgb,
                    )
                    photos.forEachIndexed { i, (fn, _) -> repository.importPhotoRecord(newId, fn, i) }
                    sigs.add(sig)
                    inserted++
                }
            }
            val msg = if (auto.createdCount > 0) uic.getString(R.string.vm_msg_new_calendars, auto.createdCount) else ""
            return Result.Success(
                if (isAppend) uic.getString(R.string.vm_append_complete_calendars_photos_msg, inserted, extracted.size, msg)
                else uic.getString(R.string.vm_restore_complete_calendars_photos_msg, inserted, extracted.size, msg)
            )
        }

        // ---- 単一の復元先カレンダーへ書き込む（従来の動作） ----
        val target = options.targetLocalCalendarId ?: return Result.Failure(uic.getString(R.string.vm_restore_target_not_selected))
        if (!isAppend) {
            val ids = repository.getAllLocalEvents().filter { it.calendarId == target }.map { it.id }
            repository.withTransaction {
                ids.forEach { repository.deleteLocalEventRecordOnly(it) }
            }
        }

        // 追記時は既存予定からシグネチャ集合を作り、重複挿入を防ぐ
        val signatures = mutableSetOf<String>()
        if (isAppend) {
            repository.getAllLocalEvents().filter { it.calendarId == target }.forEach { ex ->
                signatures.add(eventSignature(ex.title, ex.startTime, ex.endTime, ex.isAllDay))
            }
        }

        var inserted = 0
        repository.withTransaction {
            for ((ev, photos) in pairs) {
                if (ev.description == LocalEvent.DESCRIPTION_HOLIDAY) continue
                if (ev.calendarId !in selected) continue
                val sig = eventSignature(ev.title, ev.startTime, ev.endTime, ev.isAllDay)
                if (isAppend && sig in signatures) continue
                val newId = repository.insertLocalEvent(
                    ev.title, ev.startTime, ev.endTime, ev.isAllDay,
                    ev.location, ev.description, ev.rrule, target,
                    colorArgb = ev.colorArgb,
                )
                photos.forEachIndexed { i, (fn, _) -> repository.importPhotoRecord(newId, fn, i) }
                signatures.add(sig)
                inserted++
            }
        }
        return Result.Success(
            if (isAppend) uic.getString(R.string.vm_append_complete_calendars_photos, inserted, extracted.size)
            else uic.getString(R.string.vm_restore_complete_calendars_photos, inserted, extracted.size)
        )
    }

    // イベントを選択ソースでフィルタし、復元先へ書き込む。
    // 追記(isAppend=true) のときは、既存予定と (title, start, end, isAllDay) が
    // 一致するものを重複とみなして挿入しない。
    private suspend fun applyEvents(
        events: List<LocalEvent>,
        sources: List<SourceCalendarInfo>,
        selectedSourceIds: Set<Long>,
        isAppend: Boolean,
        calendarMode: CalendarMode,
        options: RestoreOptions,
    ): Result {
        val filtered = events
            .filter { it.description != LocalEvent.DESCRIPTION_HOLIDAY }
            .filter { it.calendarId in selectedSourceIds }

        if (calendarMode == CalendarMode.GOOGLE) {
            val target = options.targetGoogleCalendarId
                ?: return Result.Failure(uic.getString(R.string.vm_restore_target_not_selected))

            // 追記時は既存予定からシグネチャ集合を作る
            val signatures = mutableSetOf<String>()
            if (isAppend && filtered.isNotEmpty()) {
                val minStart = filtered.minOf { it.startTime }
                val maxEnd = filtered.maxOf { it.endTime }
                repository.getEventsForMonth(minStart, maxEnd, listOf(target)).forEach { ex ->
                    signatures.add(eventSignature(ex.title, ex.startTime, ex.endTime, ex.isAllDay))
                }
            }

            var inserted = 0
            for (ev in filtered) {
                val sig = eventSignature(ev.title, ev.startTime, ev.endTime, ev.isAllDay)
                if (isAppend && sig in signatures) continue
                repository.insertEventWithCalendarId(
                    ev.title, ev.startTime, ev.endTime, ev.isAllDay,
                    ev.location, ev.description, ev.rrule, target,
                )
                signatures.add(sig)
                inserted++
            }
            val skipped = filtered.size - inserted
            return Result.Success(
                if (isAppend) uic.getString(R.string.vm_append_complete_skipped_duplicates, inserted, skipped)
                else uic.getString(R.string.vm_restore_complete_count, inserted)
            )
        } else {
            // Golendar モード: 同名カレンダー自動作成モード
            if (options.autoCreateMissingCalendars) {
                return applyEventsGolendarAutoCreate(filtered, sources, selectedSourceIds, isAppend)
            }

            val target = options.targetLocalCalendarId
                ?: return Result.Failure(uic.getString(R.string.vm_restore_target_not_selected))
            if (!isAppend) {
                val ids = repository.getAllLocalEvents().filter { it.calendarId == target }.map { it.id }
                repository.withTransaction {
                    ids.forEach { repository.deleteLocalEventRecordOnly(it) }
                }
            }

            // 追記時は既存予定からシグネチャ集合を作る
            val signatures = mutableSetOf<String>()
            if (isAppend) {
                repository.getAllLocalEvents().filter { it.calendarId == target }.forEach { ex ->
                    signatures.add(eventSignature(ex.title, ex.startTime, ex.endTime, ex.isAllDay))
                }
            }

            var inserted = 0
            repository.withTransaction {
                for (ev in filtered) {
                    val sig = eventSignature(ev.title, ev.startTime, ev.endTime, ev.isAllDay)
                    if (isAppend && sig in signatures) continue
                    repository.insertLocalEvent(
                        ev.title, ev.startTime, ev.endTime, ev.isAllDay,
                        ev.location, ev.description, ev.rrule, target,
                        colorArgb = ev.colorArgb,
                    )
                    signatures.add(sig)
                    inserted++
                }
            }
            val skipped = filtered.size - inserted
            return Result.Success(
                if (isAppend) uic.getString(R.string.vm_append_complete_skipped_duplicates, inserted, skipped)
                else uic.getString(R.string.vm_restore_complete_count, inserted)
            )
        }
    }

    // Golendar モード用: バックアップ内の各カレンダー名をローカルカレンダーに
    // マッピングして復元する。同名カレンダーが無ければ新規作成する。
    // 旧形式（source.id == 0）の予定は既定カレンダーへフォールバックする。
    private suspend fun applyEventsGolendarAutoCreate(
        filtered: List<LocalEvent>,
        sources: List<SourceCalendarInfo>,
        selectedSourceIds: Set<Long>,
        isAppend: Boolean,
    ): Result {
        val auto = buildAutoCreateMapping(sources, selectedSourceIds)
            ?: return Result.Failure(uic.getString(R.string.vm_my_calendar_not_found))

        if (!isAppend) {
            val ids = repository.getAllLocalEvents()
                .filter { it.calendarId in auto.allTargetIds }
                .map { it.id }
            repository.withTransaction {
                ids.forEach { repository.deleteLocalEventRecordOnly(it) }
            }
        }
        val sigsByTarget = mutableMapOf<Long, MutableSet<String>>()
        if (isAppend) {
            for (tid in auto.allTargetIds) {
                val sigs = mutableSetOf<String>()
                repository.getAllLocalEvents().filter { it.calendarId == tid }.forEach { ex ->
                    sigs.add(eventSignature(ex.title, ex.startTime, ex.endTime, ex.isAllDay))
                }
                sigsByTarget[tid] = sigs
            }
        }
        var inserted = 0
        repository.withTransaction {
            for (ev in filtered) {
                val targetId = auto.mapping[ev.calendarId] ?: auto.defaultTargetId
                val sigs = sigsByTarget.getOrPut(targetId) { mutableSetOf() }
                val sig = eventSignature(ev.title, ev.startTime, ev.endTime, ev.isAllDay)
                if (isAppend && sig in sigs) continue
                repository.insertLocalEvent(
                    ev.title, ev.startTime, ev.endTime, ev.isAllDay,
                    ev.location, ev.description, ev.rrule, targetId,
                    colorArgb = ev.colorArgb,
                )
                sigs.add(sig)
                inserted++
            }
        }
        val msg = if (auto.createdCount > 0) uic.getString(R.string.vm_msg_new_calendars, auto.createdCount) else ""
        return Result.Success(
            if (isAppend) uic.getString(R.string.vm_append_complete_msg, inserted, msg)
            else uic.getString(R.string.vm_restore_complete_msg, inserted, msg)
        )
    }

    // バックアップ内の各ソースカレンダーをローカルカレンダー ID にマッピングする。
    // 同名カレンダーが無ければ新規作成する。マイカレンダー（既定）が無い場合は null。
    private data class AutoCreateResult(
        val mapping: Map<Long, Long>,
        val defaultTargetId: Long,
        val createdCount: Int,
        val allTargetIds: Set<Long>,
    )

    private suspend fun buildAutoCreateMapping(
        sources: List<SourceCalendarInfo>,
        selectedSourceIds: Set<Long>,
    ): AutoCreateResult? {
        val localCalendars = repository.getAllLocalCalendars().toMutableList()
        val defaultCal = localCalendars.firstOrNull { it.isDefault }
            ?: localCalendars.firstOrNull()
            ?: return null
        val mapping = mutableMapOf<Long, Long>()
        var created = 0
        for (src in sources) {
            if (src.id !in selectedSourceIds) continue
            // id=0 は旧形式の「不明」センチネル。既定カレンダーへ送る。
            if (src.id == 0L) {
                mapping[0L] = defaultCal.id
                continue
            }
            val existing = localCalendars.firstOrNull { cal ->
                cal.name == src.name || (
                    // 既定カレンダーの空文字センチネルは locale 名としてバックアップに
                    // 書き出されるため、全ロケールの既定名のいずれかと一致する
                    // ケースも既定へ送る（他 locale 端末で書き出したバックアップ対応）
                    cal.isDefault && cal.name.isEmpty() && src.name in LocalCalendarNames.allDefaultNames(uic)
                )
            }
            val targetId: Long = if (existing != null) {
                existing.id
            } else {
                val maxSort = localCalendars.maxOfOrNull { it.sortOrder } ?: 0
                val newId = repository.insertLocalCalendar(
                    LocalCalendar(
                        name = src.name,
                        colorArgb = src.colorArgb,
                        isVisible = true,
                        sortOrder = maxSort + 1,
                        isDefault = false,
                    )
                )
                localCalendars.add(
                    LocalCalendar(
                        id = newId,
                        name = src.name,
                        colorArgb = src.colorArgb,
                        isVisible = true,
                        sortOrder = maxSort + 1,
                        isDefault = false,
                    )
                )
                created++
                newId
            }
            mapping[src.id] = targetId
        }
        return AutoCreateResult(mapping, defaultCal.id, created, mapping.values.toSet())
    }

    // 予定の重複判定に使うシグネチャを組み立てる
    private fun eventSignature(title: String, start: Long, end: Long, allDay: Boolean): String =
        "$title|$start|$end|$allDay"

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

    private suspend fun applySettingsJson(s: JSONObject, restoreMode: Boolean = true) {
        context.dataStore.edit { prefs ->
            if (s.has("theme_mode")) prefs[SettingsKeys.THEME] = s.getString("theme_mode")
            if (s.has("week_start_day")) prefs[SettingsKeys.WEEK_START] = s.getString("week_start_day")
            // calendar_mode は旧形式バックアップからの復元では上書きしない。
            // 復元をきっかけに Golendar / Google モードが意図せず切り替わるのを防ぐ
            // （新形式バックアップでは現在の設定をそのまま反映する）。
            if (restoreMode && s.has("calendar_mode")) prefs[SettingsKeys.MODE] = s.getString("calendar_mode")
            if (s.has("calendar_bg_color")) {
                val v = s.getString("calendar_bg_color")
                // 旧Verバックアップでは色が UNSPECIFIED で保存されていることがある。
                // その場合は現在の設定色を維持し、上書きしない（後方互換）。
                if (v != SettingsKeys.COLOR_UNSPECIFIED) prefs[SettingsKeys.BG_COLOR] = v
            }
            if (s.has("selected_calendars")) prefs[SettingsKeys.SELECTED_CALENDARS] = s.getString("selected_calendars")
            if (s.has("day_colors")) {
                val dc = s.getJSONObject("day_colors")
                java.time.DayOfWeek.values().forEach { d ->
                    if (dc.has(d.name)) {
                        val v = dc.getString(d.name)
                        // 曜日色も同様に UNSPECIFIED なら現在の設定を維持する（後方互換）。
                        if (v != SettingsKeys.COLOR_UNSPECIFIED) prefs[SettingsKeys.dayColor(d)] = v
                    }
                }
            }
        }
    }
}