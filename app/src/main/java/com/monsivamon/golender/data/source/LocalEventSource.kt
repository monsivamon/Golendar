package com.monsivamon.golender.data.source

import com.monsivamon.golender.data.LocalEvent
import com.monsivamon.golender.data.LocalEventDao

// ローカル DB（Room）の DAO を薄くラップするソース
class LocalEventSource(private val dao: LocalEventDao) {

    // 期間内の予定を取得する
    suspend fun getEventsInRange(start: Long, end: Long): List<LocalEvent> =
        dao.getEventsInRange(start, end)

    // 期間内かつ指定カレンダーの予定を取得する
    suspend fun getEventsInRangeFiltered(start: Long, end: Long, calendarIds: List<Long>): List<LocalEvent> =
        dao.getEventsInRangeFiltered(start, end, calendarIds)

    // 全予定を取得する
    suspend fun getAll(): List<LocalEvent> = dao.getAllEvents()

    // 予定を 1 件挿入し、採番 ID を返す
    suspend fun insert(event: LocalEvent): Long = dao.insert(event)

    // 予定を更新する
    suspend fun update(event: LocalEvent) = dao.update(event)

    // ID 指定で予定を削除する
    suspend fun deleteById(id: Long) = dao.deleteById(id)

    // 所属カレンダーを付け替える
    suspend fun moveEvents(sourceCalendarId: Long, targetCalendarId: Long) =
        dao.moveEvents(sourceCalendarId, targetCalendarId)

    // 全削除→再投入で復元する
    suspend fun restore(events: List<LocalEvent>) {
        dao.deleteAll()
        dao.insertAll(events)
    }

    // 既存に追記する
    suspend fun append(events: List<LocalEvent>) = dao.insertAll(events)

    // 指定国の祝日データを削除→再投入で置き換える
    suspend fun replaceHolidays(country: String, holidays: List<LocalEvent>) =
        dao.replaceHolidays(country, holidays)

    // 国コードを持たない旧形式の祝日を削除する
    suspend fun deleteLegacyHolidays() = dao.deleteLegacyHolidays()

    // 選択されていない国の祝日を削除する
    suspend fun deleteHolidaysNotIn(countries: List<String>) = dao.deleteHolidaysNotIn(countries)

    // すべての祝日を削除する
    suspend fun deleteAllSystemHolidays() = dao.deleteByDescription(LocalEvent.DESCRIPTION_HOLIDAY)
}