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

    // 指定期間に該当する予定（または繰り返し予定）を開始時刻順で取得する
    @Query("SELECT * FROM local_events WHERE (startTime <= :end AND endTime >= :start) OR rrule IS NOT NULL ORDER BY startTime ASC")
    suspend fun getEventsInRange(start: Long, end: Long): List<LocalEvent>

    // 全予定を取得する（バックアップ用）
    @Query("SELECT * FROM local_events")
    suspend fun getAllEvents(): List<LocalEvent>

    // 予定を挿入し採番 ID を返す
    @Insert
    suspend fun insert(event: LocalEvent): Long

    // 予定を更新する
    @Update
    suspend fun update(event: LocalEvent)

    // ID 指定で予定を削除する
    @Query("DELETE FROM local_events WHERE id = :id")
    suspend fun deleteById(id: Long)

    // description 指定で予定を削除する（祝日更新用）
    @Query("DELETE FROM local_events WHERE description = :description")
    suspend fun deleteByDescription(description: String)

    // 全予定を削除する（復元前の初期化用）
    @Query("DELETE FROM local_events")
    suspend fun deleteAll()

    // 予定を一括挿入する（競合時は置換）
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(events: List<LocalEvent>)

    // 祝日データを原子的に置き換える（二重挿入防止）
    @Transaction
    suspend fun replaceSystemHolidays(holidays: List<LocalEvent>) {
        deleteByDescription(LocalEvent.DESCRIPTION_HOLIDAY)
        insertAll(holidays)
    }
}