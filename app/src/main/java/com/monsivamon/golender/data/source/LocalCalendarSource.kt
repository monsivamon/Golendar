package com.monsivamon.golender.data.source

import com.monsivamon.golender.data.LocalCalendar
import com.monsivamon.golender.data.LocalCalendarDao

// Room の LocalCalendarDao を薄くラップするソース
class LocalCalendarSource(private val dao: LocalCalendarDao) {
    // 全ローカルカレンダーを並び順で取得する
    suspend fun getAll(): List<LocalCalendar> = dao.getAll()

    // 可視ローカルカレンダーのみを取得する
    suspend fun getVisible(): List<LocalCalendar> = dao.getVisible()

    // 既定（マイカレンダー）を取得する
    suspend fun getDefault(): LocalCalendar? = dao.getDefault()

    // ローカルカレンダーを挿入し採番 ID を返す
    suspend fun insert(calendar: LocalCalendar): Long = dao.insert(calendar)

    // ローカルカレンダーを更新する
    suspend fun update(calendar: LocalCalendar) = dao.update(calendar)

    // 既定以外のローカルカレンダーを削除する
    suspend fun delete(id: Long) = dao.delete(id)
}