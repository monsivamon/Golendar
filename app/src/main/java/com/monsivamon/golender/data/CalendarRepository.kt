package com.monsivamon.golender.data

import android.content.Context
import com.monsivamon.golender.data.source.HolidaySource
import com.monsivamon.golender.data.source.LocalEventSource
import com.monsivamon.golender.data.source.SystemCalendarSource
import com.monsivamon.golender.data.util.RruleExpander
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.ZoneId

// システムカレンダー・ローカルDB・祝日APIの各ソースを束ねるファサード。
class CalendarRepository(context: Context) {

    private val systemSource = SystemCalendarSource(context)
    private val localSource = LocalEventSource(AppDatabase.getInstance(context).localEventDao())
    private val holidaySource = HolidaySource()

    // 月表示用にシステムカレンダーの予定を取得する。
    fun getEventsForMonth(startMillis: Long, endMillis: Long, calendarIds: List<Long>? = null): List<Event> =
        systemSource.getEventsForMonth(startMillis, endMillis, calendarIds)

    // バックアップ用に指定アカウントの全イベントを取得する。
    fun getAllGoogleEvents(accountName: String?): List<Event> =
        systemSource.getAllGoogleEvents(accountName)

    // 利用可能なGoogleアカウント一覧を取得する。
    fun getAccountNames(): List<String> = systemSource.getAccountNames()

    // アカウント名に紐づくカレンダーIDリストを取得する。
    fun getCalendarIdsForAccount(accountName: String): List<Long> =
        systemSource.getCalendarIdsForAccount(accountName)

    // 祝日・誕生日カレンダーのIDのみを取得する。
    fun getSpecialCalendarIds(): List<Long> = systemSource.getSpecialCalendarIds()

    // システムカレンダーに予定を新規作成する。
    fun insertEvent(
        title: String, startMillis: Long, endMillis: Long, isAllDay: Boolean,
        location: String, description: String, rrule: String?, accountName: String? = null,
    ): Long? = systemSource.insertEvent(title, startMillis, endMillis, isAllDay,
        location, description, rrule, accountName)

    // システムカレンダーの予定を更新する。
    fun updateEvent(
        eventId: Long, title: String, startMillis: Long, endMillis: Long,
        isAllDay: Boolean, location: String, description: String, rrule: String?,
    ): Boolean = systemSource.updateEvent(eventId, title, startMillis, endMillis,
        isAllDay, location, description, rrule)

    // システムカレンダーの予定を削除する。
    fun deleteEvent(eventId: Long): Boolean = systemSource.deleteEvent(eventId)

    // ローカル予定を取得し、繰り返しルールを展開して期間内の Event を生成する。
    suspend fun getLocalEventsForMonth(startMillis: Long, endMillis: Long): List<Event> {
        val allLocalEvents = localSource.getEventsInRange(startMillis, endMillis)
        val zone = ZoneId.systemDefault()
        return allLocalEvents.flatMap { RruleExpander.expand(it, startMillis, endMillis, zone) }
    }

    // ローカルDBに予定を新規作成する。
    suspend fun insertLocalEvent(
        title: String, startMillis: Long, endMillis: Long, isAllDay: Boolean,
        location: String, description: String, rrule: String?,
    ): Long = localSource.insert(
        LocalEvent(0, title, startMillis, endMillis, isAllDay, location, description, rrule)
    )

    // ローカルDBの予定を更新する。
    suspend fun updateLocalEvent(
        eventId: Long, title: String, startMillis: Long, endMillis: Long,
        isAllDay: Boolean, location: String, description: String, rrule: String?,
    ) {
        localSource.update(
            LocalEvent(eventId, title, startMillis, endMillis, isAllDay, location, description, rrule)
        )
    }

    // ローカルDBの予定を削除する。
    suspend fun deleteLocalEvent(eventId: Long) = localSource.deleteById(eventId)

    // ローカルDBの全予定を取得する（バックアップ用）。
    suspend fun getAllLocalEvents(): List<LocalEvent> = localSource.getAll()

    // ローカルDBを全削除してリストで復元する。
    suspend fun restoreLocalEvents(events: List<LocalEvent>) = localSource.restore(events)

    // ローカルDBにリストを追記する（重複は置き換え）。
    suspend fun appendLocalEvents(events: List<LocalEvent>) = localSource.append(events)

    // 外部APIから日本の祝日データを取得しローカルDBに保存する（既存祝日は原子的に置換）。
    suspend fun fetchAndSaveHolidays() = withContext(Dispatchers.IO) {
        try {
            val holidays = holidaySource.fetchHolidays()
            if (holidays.isNotEmpty()) {
                localSource.replaceSystemHolidays(holidays)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}