package com.monsivamon.golender.data

import android.content.Context
import android.net.Uri
import com.monsivamon.golender.data.source.EventPhotoSource
import com.monsivamon.golender.data.source.HolidaySource
import com.monsivamon.golender.data.source.LocalEventSource
import com.monsivamon.golender.data.source.SystemCalendarSource
import com.monsivamon.golender.data.util.PhotoStorage
import com.monsivamon.golender.data.util.RruleExpander
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.ZoneId

// システムカレンダー・ローカルDB・祝日APIの各ソースを束ねるファサード。
class CalendarRepository(context: Context) {
    private val appContext = context.applicationContext
    private val systemSource = SystemCalendarSource(appContext)
    private val db = AppDatabase.getInstance(appContext)
    private val localSource = LocalEventSource(db.localEventDao())
    private val photoSource = EventPhotoSource(db.eventPhotoDao())
    private val holidaySource = HolidaySource()

    // 月表示用にシステムカレンダーの予定を取得する。
    fun getEventsForMonth(startMillis: Long, endMillis: Long, calendarIds: List<Long>? = null): List<Event> =
        systemSource.getEventsForMonth(startMillis, endMillis, calendarIds)

    // バックアップ用に指定アカウントの全イベントを取得する。
    fun getAllGoogleEvents(accountName: String?): List<Event> = systemSource.getAllGoogleEvents(accountName)
    // 利用可能なGoogleアカウント一覧を取得する。
    fun getAccountNames(): List<String> = systemSource.getAccountNames()
    // アカウント名に紐づくカレンダーIDリストを取得する。
    fun getCalendarIdsForAccount(accountName: String): List<Long> = systemSource.getCalendarIdsForAccount(accountName)
    // 祝日・誕生日カレンダーのIDのみを取得する。
    fun getSpecialCalendarIds(): List<Long> = systemSource.getSpecialCalendarIds()

    // システムカレンダーに予定を新規作成する。
    fun insertEvent(
        title: String, startMillis: Long, endMillis: Long, isAllDay: Boolean,
        location: String, description: String, rrule: String?, accountName: String? = null,
    ): Long? = systemSource.insertEvent(title, startMillis, endMillis, isAllDay, location, description, rrule, accountName)

    // システムカレンダーの予定を更新する。
    fun updateEvent(
        eventId: Long, title: String, startMillis: Long, endMillis: Long,
        isAllDay: Boolean, location: String, description: String, rrule: String?,
    ): Boolean = systemSource.updateEvent(eventId, title, startMillis, endMillis, isAllDay, location, description, rrule)

    // システムカレンダーの予定を削除する。
    fun deleteEvent(eventId: Long): Boolean = systemSource.deleteEvent(eventId)

    // ローカル予定を取得し、繰り返しルールを展開して期間内の Event を生成する。
    suspend fun getLocalEventsForMonth(startMillis: Long, endMillis: Long): List<Event> {
        val all = localSource.getEventsInRange(startMillis, endMillis)
        val zone = ZoneId.systemDefault()
        return all.flatMap { RruleExpander.expand(it, startMillis, endMillis, zone) }
    }

    // ローカルDBに予定を新規作成する。
    suspend fun insertLocalEvent(
        title: String, startMillis: Long, endMillis: Long, isAllDay: Boolean,
        location: String, description: String, rrule: String?,
    ): Long = localSource.insert(LocalEvent(0, title, startMillis, endMillis, isAllDay, location, description, rrule))

    // ローカルDBの予定を更新する。
    suspend fun updateLocalEvent(
        eventId: Long, title: String, startMillis: Long, endMillis: Long,
        isAllDay: Boolean, location: String, description: String, rrule: String?,
    ) {
        localSource.update(LocalEvent(eventId, title, startMillis, endMillis, isAllDay, location, description, rrule))
    }

    // ローカルDBの予定を削除する（添付写真も一緒に削除）。
    suspend fun deleteLocalEvent(eventId: Long) {
        val photos = photoSource.getByEventId(eventId)
        PhotoStorage.deleteAll(appContext, photos.map { it.fileName })
        photoSource.deleteByEventId(eventId)
        localSource.deleteById(eventId)
    }

    // ローカルDBの全予定を取得する（バックアップ用）。
    suspend fun getAllLocalEvents(): List<LocalEvent> = localSource.getAll()
    // ローカルDBを全削除してリストで復元する。
    suspend fun restoreLocalEvents(events: List<LocalEvent>) = localSource.restore(events)
    // ローカルDBにリストを追記する（重複は置き換え）。
    suspend fun appendLocalEvents(events: List<LocalEvent>) = localSource.append(events)

    // 外部APIから日本の祝日データを取得しローカルDBに保存する。
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

    // 予定に添付された写真一覧を取得する。
    suspend fun getPhotosForEvent(eventId: Long): List<EventPhoto> = photoSource.getByEventId(eventId)
    // 複数予定の写真を一括取得する。
    suspend fun getPhotosForEvents(eventIds: List<Long>): Map<Long, List<EventPhoto>> =
        photoSource.getByEventIds(eventIds).groupBy { it.eventId }
    // 予定の添付写真枚数を取得する。
    suspend fun getPhotoCountForEvent(eventId: Long): Int = photoSource.countByEventId(eventId)

    // URI から写真を圧縮保存して予定に添付する。
    suspend fun attachPhotoToEvent(eventId: Long, uri: Uri): EventPhoto? = withContext(Dispatchers.IO) {
        val cnt = photoSource.countByEventId(eventId)
        if (cnt >= EventPhoto.MAX_PHOTOS_PER_EVENT) return@withContext null
        val name = PhotoStorage.saveCompressed(appContext, uri) ?: return@withContext null
        val photo = EventPhoto(eventId = eventId, fileName = name, position = cnt)
        val newId = photoSource.insert(photo)
        photo.copy(id = newId)
    }

    // 写真を削除する（DB + ファイル）。
    suspend fun removePhoto(photo: EventPhoto) {
        PhotoStorage.delete(appContext, photo.fileName)
        photoSource.deleteById(photo.id)
    }

    // 予定の添付写真を位置順に再採番する。
    suspend fun reorderPhotos(eventId: Long, ordered: List<EventPhoto>) {
        photoSource.replaceForEvent(eventId, ordered.mapIndexed { i, p -> p.copy(position = i) })
    }

    // 全ローカル写真を eventId -> 写真リスト で返す（ZIP出力用）。
    suspend fun getAllPhotosByEventId(): Map<Long, List<EventPhoto>> {
        val all = localSource.getAll()
        return photoSource.getByEventIds(all.map { it.id }).groupBy { it.eventId }
    }

    // ZIPインポート時に写真レコードを保存する。
    suspend fun importPhotoRecord(eventId: Long, fileName: String, position: Int) {
        photoSource.insert(EventPhoto(eventId = eventId, fileName = fileName, position = position))
    }

    // 全ローカル写真レコードをクリアする。
    suspend fun deleteAllPhotoRecords() = photoSource.deleteAll()
}