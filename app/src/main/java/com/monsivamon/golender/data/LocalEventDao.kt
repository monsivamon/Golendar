package com.monsivamon.golender.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update

// ローカル予定の CRUD を提供する DAO
@Dao
interface LocalEventDao {

    @Query("SELECT * FROM local_events WHERE (startTime <= :end AND endTime >= :start) OR rrule IS NOT NULL ORDER BY startTime ASC")
    // 期間内の予定を取得する
    suspend fun getEventsInRange(start: Long, end: Long): List<LocalEvent>

    @Query("SELECT * FROM local_events WHERE ((startTime <= :end AND endTime >= :start) OR rrule IS NOT NULL) AND (calendarId IN (:calendarIds) OR description = 'system_holiday') ORDER BY startTime ASC")
    // 期間内かつ指定カレンダーの予定を取得する（祝日は常に含む）
    suspend fun getEventsInRangeFiltered(start: Long, end: Long, calendarIds: List<Long>): List<LocalEvent>

    @Query("SELECT * FROM local_events")
    // 全予定を取得する
    suspend fun getAllEvents(): List<LocalEvent>

    @Insert
    // 予定を 1 件挿入し、採番 ID を返す
    suspend fun insert(event: LocalEvent): Long

    @Update
    // 予定を更新する
    suspend fun update(event: LocalEvent)

    @Query("DELETE FROM local_events WHERE id = :id")
    // ID 指定で予定を削除する
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM local_events WHERE description = :description")
    // 指定 description の予定をまとめて削除する
    suspend fun deleteByDescription(description: String)

    @Query("DELETE FROM local_events")
    // 全予定を削除する
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    // 予定を一括挿入する（REPLACE 戦略）
    suspend fun insertAll(events: List<LocalEvent>)

    @Transaction
    // 既存の祝日を全削除→再投入で置き換える
    suspend fun replaceSystemHolidays(holidays: List<LocalEvent>) {
        deleteByDescription(LocalEvent.DESCRIPTION_HOLIDAY)
        insertAll(holidays)
    }

    @Query("UPDATE local_events SET calendarId = :targetCalendarId WHERE calendarId = :sourceCalendarId")
    // 所属カレンダーを付け替える
    suspend fun moveEvents(sourceCalendarId: Long, targetCalendarId: Long)
}