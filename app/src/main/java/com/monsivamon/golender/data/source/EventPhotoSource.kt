package com.monsivamon.golender.data.source

import com.monsivamon.golender.data.EventPhoto
import com.monsivamon.golender.data.EventPhotoDao

// Room の EventPhotoDao を薄くラップするソース
class EventPhotoSource(private val dao: EventPhotoDao) {
    // 指定予定の写真一覧を取得する
    suspend fun getByEventId(eventId: Long): List<EventPhoto> = dao.getByEventId(eventId)
    // 複数予定の写真を一括取得する（空リストは早期 return）
    suspend fun getByEventIds(eventIds: List<Long>): List<EventPhoto> =
        if (eventIds.isEmpty()) emptyList() else dao.getByEventIds(eventIds)
    // 指定予定の写真枚数を取得する
    suspend fun countByEventId(eventId: Long): Int = dao.countByEventId(eventId)
    // 写真を 1 件登録する
    suspend fun insert(photo: EventPhoto): Long = dao.insert(photo)
    // 写真を一括登録する
    suspend fun insertAll(photos: List<EventPhoto>) = dao.insertAll(photos)
    // 写真を ID 指定で削除する
    suspend fun deleteById(id: Long) = dao.deleteById(id)
    // 指定予定に紐づく写真を全て削除する
    suspend fun deleteByEventId(eventId: Long) = dao.deleteByEventId(eventId)
    // 全写真レコードを削除する
    suspend fun deleteAll() = dao.deleteAll()
    // 指定予定の写真を置き換える
    suspend fun replaceForEvent(eventId: Long, photos: List<EventPhoto>) = dao.replaceForEvent(eventId, photos)
}