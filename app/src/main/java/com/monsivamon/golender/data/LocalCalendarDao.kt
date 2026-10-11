package com.monsivamon.golender.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

// ローカルカレンダーの CRUD を提供する DAO
@Dao
interface LocalCalendarDao {
    // 全ローカルカレンダーを並び順で取得する
    @Query("SELECT * FROM local_calendars ORDER BY sortOrder ASC, id ASC")
    suspend fun getAll(): List<LocalCalendar>

    // 可視ローカルカレンダーのみを取得する
    @Query("SELECT * FROM local_calendars WHERE isVisible = 1 ORDER BY sortOrder ASC, id ASC")
    suspend fun getVisible(): List<LocalCalendar>

    // 既定（マイカレンダー）を取得する
    @Query("SELECT * FROM local_calendars WHERE isDefault = 1 LIMIT 1")
    suspend fun getDefault(): LocalCalendar?

    // ローカルカレンダーを挿入し採番 ID を返す
    @Insert
    suspend fun insert(calendar: LocalCalendar): Long

    // ローカルカレンダーを更新する
    @Update
    suspend fun update(calendar: LocalCalendar)

    // 既定以外のローカルカレンダーを削除する
    @Query("DELETE FROM local_calendars WHERE id = :id AND isDefault = 0")
    suspend fun delete(id: Long)
}