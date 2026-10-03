package com.monsivamon.golender.data

import android.content.Context
import android.net.Uri
import com.monsivamon.golender.data.source.EventPhotoSource
import com.monsivamon.golender.data.source.HolidaySource
import com.monsivamon.golender.data.source.LocalEventSource
import com.monsivamon.golender.data.source.SystemCalendarSource
import com.monsivamon.golender.data.source.CalendarMeta
import com.monsivamon.golender.data.util.PhotoStorage
import com.monsivamon.golender.data.util.RruleExpander
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
    private val holidaySource = HolidaySource()

    // 全カレンダーのメタ情報を取得する
    fun getAllCalendars(): List<CalendarMeta> = systemSource.getAllCalendars()

    // 期間とカレンダー ID 群を指定してシステムカレンダーの予定を取得する
    fun getEventsForMonth(startMillis: Long, endMillis: Long, calendarIds: List<Long>? = null): List<Event> =
        systemSource.getEventsForMonth(startMillis, endMillis, calendarIds)

    // バックアップ用に指定アカウントの全 Google イベントを取得する
    fun getAllGoogleEvents(accountName: String?): List<Event> = systemSource.getAllGoogleEvents(accountName)

    // 利用可能な Google アカウント名一覧を取得する
    fun getAccountNames(): List<String> = systemSource.getAccountNames()

    // アカウントに紐づくカレンダー ID リストを取得する
    fun getCalendarIdsForAccount(accountName: String): List<Long> = systemSource.getCalendarIdsForAccount(accountName)

    // 祝日・誕生日カレンダーの ID のみを取得する
    fun getSpecialCalendarIds(): List<Long> = systemSource.getSpecialCalendarIds()

    // アカウント指定でシステムカレンダーに予定を挿入する
    fun insertEvent(
        title: String, startMillis: Long, endMillis: Long, isAllDay: Boolean,
        location: String, description: String, rrule: String?, accountName: String? = null,
    ): Long? = systemSource.insertEvent(title, startMillis, endMillis, isAllDay, location, description, rrule, accountName)

    // カレンダー ID を直接指定してシステムカレンダーに予定を挿入する
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

    // ローカル予定を取得し、繰り返しルールを期間内に展開して返す
    suspend fun getLocalEventsForMonth(startMillis: Long, endMillis: Long): List<Event> {
        val all = localSource.getEventsInRange(startMillis, endMillis)
        val zone = ZoneId.systemDefault()
        return all.flatMap { RruleExpander.expand(it, startMillis, endMillis, zone) }
    }

    // ローカル DB に予定を挿入する
    suspend fun insertLocalEvent(
        title: String, startMillis: Long, endMillis: Long, isAllDay: Boolean,
        location: String, description: String, rrule: String?,
    ): Long = localSource.insert(LocalEvent(0, title, startMillis, endMillis, isAllDay, location, description, rrule))

    // ローカル DB の予定を更新する
    suspend fun updateLocalEvent(
        eventId: Long, title: String, startMillis: Long, endMillis: Long,
        isAllDay: Boolean, location: String, description: String, rrule: String?,
    ) {
        localSource.update(LocalEvent(eventId, title, startMillis, endMillis, isAllDay, location, description, rrule))
    }

    // ローカル DB の予定とその添付写真をまとめて削除する
    suspend fun deleteLocalEvent(eventId: Long) {
        val photos = photoSource.getByEventId(eventId)
        PhotoStorage.deleteAll(appContext, photos.map { it.fileName })
        photoSource.deleteByEventId(eventId)
        localSource.deleteById(eventId)
    }

    // ローカル DB の全予定を取得する（バックアップ用）
    suspend fun getAllLocalEvents(): List<LocalEvent> = localSource.getAll()

    // ローカル DB を全削除してリストで復元する
    suspend fun restoreLocalEvents(events: List<LocalEvent>) = localSource.restore(events)

    // ローカル DB にリストを追記する（重複は置換）
    suspend fun appendLocalEvents(events: List<LocalEvent>) = localSource.append(events)

    // 外部 API から祝日データを取得してローカル DB に保存する
    suspend fun fetchAndSaveHolidays(): Boolean = withContext(Dispatchers.IO) {
        try {
            val holidays = holidaySource.fetchHolidays()
            if (holidays.isNotEmpty()) {
                localSource.replaceSystemHolidays(holidays)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    // 指定予定の添付写真一覧を取得する
    suspend fun getPhotosForEvent(eventId: Long): List<EventPhoto> = photoSource.getByEventId(eventId)

    // 複数予定の添付写真を一括取得する
    suspend fun getPhotosForEvents(eventIds: List<Long>): Map<Long, List<EventPhoto>> =
        photoSource.getByEventIds(eventIds).groupBy { it.eventId }

    // 指定予定の添付写真枚数を取得する
    suspend fun getPhotoCountForEvent(eventId: Long): Int = photoSource.countByEventId(eventId)

    // URI から写真を圧縮保存して予定に添付する
    suspend fun attachPhotoToEvent(eventId: Long, uri: Uri): EventPhoto? = withContext(Dispatchers.IO) {
        val cnt = photoSource.countByEventId(eventId)
        if (cnt >= EventPhoto.MAX_PHOTOS_PER_EVENT) return@withContext null
        val name = PhotoStorage.saveCompressed(appContext, uri) ?: return@withContext null
        val photo = EventPhoto(eventId = eventId, fileName = name, position = cnt)
        val newId = photoSource.insert(photo)
        photo.copy(id = newId)
    }

    // 写真を DB レコードと実ファイルの両方から削除する
    suspend fun removePhoto(photo: EventPhoto) {
        PhotoStorage.delete(appContext, photo.fileName)
        photoSource.deleteById(photo.id)
    }

    // 予定の添付写真を位置順に再採番する
    suspend fun reorderPhotos(eventId: Long, ordered: List<EventPhoto>) {
        photoSource.replaceForEvent(eventId, ordered.mapIndexed { i, p -> p.copy(position = i) })
    }

    // 全ローカル写真を eventId -> 写真リスト の形で返す（ZIP 出力用）
    suspend fun getAllPhotosByEventId(): Map<Long, List<EventPhoto>> {
        val all = localSource.getAll()
        return photoSource.getByEventIds(all.map { it.id }).groupBy { it.eventId }
    }

    // ZIP インポート時に写真レコードを保存する
    suspend fun importPhotoRecord(eventId: Long, fileName: String, position: Int) {
        photoSource.insert(EventPhoto(eventId = eventId, fileName = fileName, position = position))
    }

    // 全ローカル写真レコードを削除する
    suspend fun deleteAllPhotoRecords() = photoSource.deleteAll()
}