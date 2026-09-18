package com.monsivamon.golender.data.source

import com.monsivamon.golender.data.LocalEvent
import com.monsivamon.golender.data.LocalEventDao

// ローカルDB（Room）のDAOを薄くラップするソース。
class LocalEventSource(private val dao: LocalEventDao) {

    // 指定期間内または繰り返し予定を取得する。
    suspend fun getEventsInRange(start: Long, end: Long): List<LocalEvent> =
        dao.getEventsInRange(start, end)

    // ローカルDBの全予定を取得する。
    suspend fun getAll(): List<LocalEvent> = dao.getAllEvents()

    // ローカルDBに予定を新規登録する。
    suspend fun insert(event: LocalEvent): Long = dao.insert(event)

    // ローカルDBの予定を更新する。
    suspend fun update(event: LocalEvent) = dao.update(event)

    // ローカルDBの予定をID指定で削除する。
    suspend fun deleteById(id: Long) = dao.deleteById(id)

    // 全件削除してリストで復元する（上書き復元用）。
    suspend fun restore(events: List<LocalEvent>) {
        dao.deleteAll()
        dao.insertAll(events)
    }

    // リストを追記する（追記復元用）。
    suspend fun append(events: List<LocalEvent>) = dao.insertAll(events)

    // 祝日データを原子的に置き換える。
    suspend fun replaceSystemHolidays(holidays: List<LocalEvent>) =
        dao.replaceSystemHolidays(holidays)
}