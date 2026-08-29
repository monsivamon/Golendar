package com.monsivamon.golender.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface LocalEventDao {
    // 指定期間内の予定を取得（繰り返しルールがあるものは常に含める）
    @Query("SELECT * FROM local_events WHERE (startTime <= :end AND endTime >= :start) OR rrule IS NOT NULL ORDER BY startTime ASC")
    suspend fun getEventsInRange(start: Long, end: Long): List<LocalEvent>

    // 全件取得（バックアップ用）
    @Query("SELECT * FROM local_events")
    suspend fun getAllEvents(): List<LocalEvent>

    // 新規登録
    @Insert
    suspend fun insert(event: LocalEvent): Long

    // 更新
    @Update
    suspend fun update(event: LocalEvent)

    // ID指定で削除
    @Query("DELETE FROM local_events WHERE id = :id")
    suspend fun deleteById(id: Long)

    // 全件削除（復元前の初期化用）
    @Query("DELETE FROM local_events")
    suspend fun deleteAll()

    // 一括挿入（競合時は置き換え）
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(events: List<LocalEvent>)
}