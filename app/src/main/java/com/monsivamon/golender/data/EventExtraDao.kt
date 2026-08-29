package com.monsivamon.golender.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface EventExtraDao {

    // 拡張情報を保存（競合時は更新）
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(eventExtra: EventExtra)

    // 予定IDで拡張情報を取得
    @Query("SELECT * FROM event_extras WHERE eventId = :eventId")
    suspend fun getByEventId(eventId: Long): EventExtra?

    // 予定IDで拡張情報を削除
    @Query("DELETE FROM event_extras WHERE eventId = :eventId")
    suspend fun deleteByEventId(eventId: Long)

    // 全拡張情報を削除
    @Query("DELETE FROM event_extras")
    suspend fun deleteAll()
}