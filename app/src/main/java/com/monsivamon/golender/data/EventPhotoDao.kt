package com.monsivamon.golender.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction

// 添付写真のCRUDを提供するDAO
@Dao
interface EventPhotoDao {
    // 指定予定に紐づく写真を表示順で取得する。
    @Query("SELECT * FROM event_photos WHERE eventId = :eventId ORDER BY position ASC, id ASC")
    suspend fun getByEventId(eventId: Long): List<EventPhoto>

    // 複数予定の写真を一括取得する。
    @Query("SELECT * FROM event_photos WHERE eventId IN (:eventIds) ORDER BY eventId ASC, position ASC, id ASC")
    suspend fun getByEventIds(eventIds: List<Long>): List<EventPhoto>

    // 指定予定の添付写真枚数を取得する。
    @Query("SELECT COUNT(*) FROM event_photos WHERE eventId = :eventId")
    suspend fun countByEventId(eventId: Long): Int

    // 写真レコードを新規登録し、採番されたIDを返す。
    @Insert
    suspend fun insert(photo: EventPhoto): Long

    // 写真レコードを一括登録する。
    @Insert
    suspend fun insertAll(photos: List<EventPhoto>)

    // 写真レコードをID指定で削除する。
    @Query("DELETE FROM event_photos WHERE id = :id")
    suspend fun deleteById(id: Long)

    // 指定予定の写真レコードを全て削除する。
    @Query("DELETE FROM event_photos WHERE eventId = :eventId")
    suspend fun deleteByEventId(eventId: Long)

    // 全写真レコードを削除する。
    @Query("DELETE FROM event_photos")
    suspend fun deleteAll()

    // 指定予定の写真レコードを全削除して置き換える。
    @Transaction
    suspend fun replaceForEvent(eventId: Long, photos: List<EventPhoto>) {
        deleteByEventId(eventId)
        insertAll(photos)
    }
}
