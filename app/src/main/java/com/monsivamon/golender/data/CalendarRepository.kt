package com.monsivamon.golender.data

import android.content.Context
import android.net.Uri
import com.monsivamon.golender.data.source.EventPhotoSource
import com.monsivamon.golender.data.source.HolidaySource
import com.monsivamon.golender.data.source.LocalCalendarSource
import com.monsivamon.golender.data.source.LocalEventSource
import com.monsivamon.golender.data.source.SystemCalendarSource
import com.monsivamon.golender.data.source.CalendarMeta
import com.monsivamon.golender.data.util.PhotoStorage
import com.monsivamon.golender.data.util.RruleExpander
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.ZoneId

// システムカレンダー・ローカル DB・祝日 API の各ソースを束ねるファサード
class CalendarRepository(context: Context) {
    private val appContext = context.applicationContext
    private val systemSource = SystemCalendarSource(appContext)
    private val db = AppDatabase.getInstance(appContext)
    private val localSource = LocalEventSource(db.localEventDao())
    private val photoSource = EventPhotoSource(db.eventPhotoDao())
    private val localCalendarSource = LocalCalendarSource(db.localCalendarDao())
    private val holidaySource = HolidaySource()

    // 複数のローカル DB 書き込みを 1 つのトランザクションで実行する。
    // 復元処理のように「既存削除 → 一括挿入」を行う処理で、
    // 途中で失敗した場合に全体をロールバックできるようにする。
    suspend fun <T> withTransaction(block: suspend () -> T): T = db.withTransaction { block() }

    // ---------- ローカルカレンダー CRUD ----------
    // 全ローカルカレンダーを取得する
    suspend fun getAllLocalCalendars(): List<LocalCalendar> = localCalendarSource.getAll()
    // 可視ローカルカレンダーのみを取得する
    suspend fun getVisibleLocalCalendars(): List<LocalCalendar> = localCalendarSource.getVisible()
    // 既定（マイカレンダー）を取得する
    suspend fun getDefaultLocalCalendar(): LocalCalendar? = localCalendarSource.getDefault()
    // ローカルカレンダーを新規登録し、採番 ID を返す
    suspend fun insertLocalCalendar(calendar: LocalCalendar): Long = localCalendarSource.insert(calendar)
    // ローカルカレンダーを更新する
    suspend fun updateLocalCalendar(calendar: LocalCalendar) = localCalendarSource.update(calendar)

    // ローカルカレンダーを削除する。
    // 所属予定は移送せず、写真ごとまとめて削除する。
    // 既定カレンダーは DAO の `WHERE isDefault = 0` により削除されない。
    // ローカルカレンダーと所属予定・写真をまとめて削除する
    suspend fun deleteLocalCalendar(id: Long) {
        // 削除対象カレンダーに属する予定を取得して、写真ごと削除する
        val targetEvents = localSource.getAll().filter { it.calendarId == id }
        for (ev in targetEvents) {
            deleteLocalEvent(ev.id)
        }
        localCalendarSource.delete(id)
    }

    // 指定カレンダーに属する予定の件数を返す（削除確認ダイアログ用）
    // 指定カレンダーに属する予定の件数を返す
    suspend fun countEventsInLocalCalendar(id: Long): Int =
        localSource.getAll().count { it.calendarId == id }

    // ---------- システムカレンダー ----------
    // 端末上の全カレンダーのメタ情報を取得する
    fun getAllCalendars(): List<CalendarMeta> = systemSource.getAllCalendars()
    // 期間とカレンダー ID 群を指定して予定を取得する
    fun getEventsForMonth(startMillis: Long, endMillis: Long, calendarIds: List<Long>? = null): List<Event> =
        systemSource.getEventsForMonth(startMillis, endMillis, calendarIds)
    // バックアップ用に指定アカウントの全予定を取得する
    fun getAllGoogleEvents(accountName: String?): List<Event> = systemSource.getAllGoogleEvents(accountName)
    // 実アカウント（@ を含む）一覧を取得する
    fun getAccountNames(): List<String> = systemSource.getAccountNames()
    // アカウントに紐づくカレンダー ID 一覧を返す
    fun getCalendarIdsForAccount(accountName: String): List<Long> = systemSource.getCalendarIdsForAccount(accountName)
    // 祝日・誕生日カレンダーの ID 一覧を返す
    fun getSpecialCalendarIds(): List<Long> = systemSource.getSpecialCalendarIds()

    // アカウント指定で予定を新規作成する
    fun insertEvent(
        title: String, startMillis: Long, endMillis: Long, isAllDay: Boolean,
        location: String, description: String, rrule: String?, accountName: String? = null,
    ): Long? = systemSource.insertEvent(title, startMillis, endMillis, isAllDay, location, description, rrule, accountName)

    // 挿入先カレンダー ID を直接指定して予定を新規作成する
    fun insertEventWithCalendarId(
        title: String, startMillis: Long, endMillis: Long, isAllDay: Boolean,
        location: String, description: String, rrule: String?, calendarId: Long,
    ): Long? = systemSource.insertEventWithCalendarId(
        title, startMillis, endMillis, isAllDay,
        location, description, rrule, calendarId,
    )

    // システムカレンダーの予定を更新する
    fun updateEvent(
        eventId: Long, title: String, startMillis: Long, endMillis: Long,
        isAllDay: Boolean, location: String, description: String, rrule: String?,
    ): Boolean = systemSource.updateEvent(eventId, title, startMillis, endMillis, isAllDay, location, description, rrule)

    // システムカレンダーの予定を削除する
    fun deleteEvent(eventId: Long): Boolean = systemSource.deleteEvent(eventId)

    // ---------- ローカル予定 ----------
    // ローカル予定を取得し、繰り返しルールを期間内に展開して返す。
    // calendarIds が指定された場合は可視カレンダーでフィルタする。
    // ローカル予定を期間内で展開し、可視カレンダーでフィルタして返す
    suspend fun getLocalEventsForMonth(
        startMillis: Long,
        endMillis: Long,
        calendarIds: List<Long>? = null,
    ): List<Event> {
        val all = if (calendarIds != null) {
            if (calendarIds.isEmpty()) return emptyList()
            localSource.getEventsInRangeFiltered(startMillis, endMillis, calendarIds)
        } else {
            localSource.getEventsInRange(startMillis, endMillis)
        }
        val zone = com.monsivamon.golender.data.prefs.AppTimeZone.effective()
        return all.flatMap { RruleExpander.expand(it, startMillis, endMillis, zone) }
    }

    // ローカル予定を新規登録し、採番 ID を返す
    suspend fun insertLocalEvent(
        title: String, startMillis: Long, endMillis: Long, isAllDay: Boolean,
        location: String, description: String, rrule: String?, calendarId: Long = 0L,
    ): Long = localSource.insert(
        LocalEvent(0, title, startMillis, endMillis, isAllDay, location, description, rrule, calendarId)
    )

    // ローカル予定を更新する
    suspend fun updateLocalEvent(
        eventId: Long, title: String, startMillis: Long, endMillis: Long,
        isAllDay: Boolean, location: String, description: String, rrule: String?, calendarId: Long = 0L,
    ) {
        localSource.update(
            LocalEvent(eventId, title, startMillis, endMillis, isAllDay, location, description, rrule, calendarId)
        )
    }

    // ローカル予定と添付写真をまとめて削除する
    suspend fun deleteLocalEvent(eventId: Long) {
        val photos = photoSource.getByEventId(eventId)
        PhotoStorage.deleteAll(appContext, photos.map { it.fileName })
        photoSource.deleteByEventId(eventId)
        localSource.deleteById(eventId)
    }

    // 全ローカル予定を取得する（バックアップ用）
    suspend fun getAllLocalEvents(): List<LocalEvent> = localSource.getAll()
    // ローカル予定を全削除→再投入で復元する
    suspend fun restoreLocalEvents(events: List<LocalEvent>) = localSource.restore(events)
    // ローカル予定を既存に追記する
    suspend fun appendLocalEvents(events: List<LocalEvent>) = localSource.append(events)

    // ---------- 祝日 ----------
    // 祝日 API から取得してローカル DB を更新する
    suspend fun fetchAndSaveHolidays(): Boolean = withContext(Dispatchers.IO) {
        try {
            val holidays = holidaySource.fetchHolidays()
            if (holidays.isNotEmpty()) {
                localSource.replaceSystemHolidays(holidays)
                true
            } else false
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    // ---------- 写真 ----------
    // 指定予定の添付写真を表示順で取得する
    suspend fun getPhotosForEvent(eventId: Long): List<EventPhoto> = photoSource.getByEventId(eventId)
    // 複数予定の添付写真を一括取得する
    suspend fun getPhotosForEvents(eventIds: List<Long>): Map<Long, List<EventPhoto>> =
        photoSource.getByEventIds(eventIds).groupBy { it.eventId }
    // 指定予定の添付写真枚数を返す
    suspend fun getPhotoCountForEvent(eventId: Long): Int = photoSource.countByEventId(eventId)

    // 予定に写真を添付し、レコードを返す
    suspend fun attachPhotoToEvent(eventId: Long, uri: Uri): EventPhoto? = withContext(Dispatchers.IO) {
        val cnt = photoSource.countByEventId(eventId)
        if (cnt >= EventPhoto.MAX_PHOTOS_PER_EVENT) return@withContext null
        val name = PhotoStorage.saveCompressed(appContext, uri) ?: return@withContext null
        val photo = EventPhoto(eventId = eventId, fileName = name, position = cnt)
        val newId = photoSource.insert(photo)
        photo.copy(id = newId)
    }

    // 添付写真を実ファイルごと削除する
    suspend fun removePhoto(photo: EventPhoto) {
        PhotoStorage.delete(appContext, photo.fileName)
        photoSource.deleteById(photo.id)
    }

    // 指定予定の写真順序を並べ替えて保存する
    suspend fun reorderPhotos(eventId: Long, ordered: List<EventPhoto>) {
        photoSource.replaceForEvent(eventId, ordered.mapIndexed { i, p -> p.copy(position = i) })
    }

    // 全予定の写真を eventId 単位でまとめて取得する
    suspend fun getAllPhotosByEventId(): Map<Long, List<EventPhoto>> {
        val all = localSource.getAll()
        return photoSource.getByEventIds(all.map { it.id }).groupBy { it.eventId }
    }

    // 復元時に写真レコードを 1 件登録する
    suspend fun importPhotoRecord(eventId: Long, fileName: String, position: Int) {
        photoSource.insert(EventPhoto(eventId = eventId, fileName = fileName, position = position))
    }

    // 全写真レコードを削除する
}