package com.monsivamon.golender.data.source

import com.monsivamon.golender.data.LocalEvent
import com.monsivamon.golender.data.LocalEventDao

// ローカル DB（Room）の DAO を薄くラップするソース
class LocalEventSource(private val dao: LocalEventDao) {

    // 指定期間または繰り返し予定を取得する
    suspend fun getEventsInRange(start: Long, end: Long): List<LocalEvent> =
        dao.getEventsInRange(start, end)

    // ローカル DB の全予定を取得する
    suspend fun getAll(): List<LocalEvent> = dao.getAllEvents()

    // ローカル DB に予定を挿入する
    suspend fun insert(event: LocalEvent): Long = dao.insert(event)

    // ローカル DB の予定を更新する
    suspend fun update(event: LocalEvent) = dao.update(event)

    // ローカル DB の予定を ID 指定で削除する
    suspend fun deleteById(id: Long) = dao.deleteById(id)

    // 全削除→再挿入で上書き復元する
    suspend fun restore(events: List<LocalEvent>) {
        dao.deleteAll()
        dao.insertAll(events)
    }

    // リストを追記する（追記復元用）
    suspend fun append(events: List<LocalEvent>) = dao.insertAll(events)

    // 祝日データを原子的に置き換える
    suspend fun replaceSystemHolidays(holidays: List<LocalEvent>) =
        dao.replaceSystemHolidays(holidays)
}