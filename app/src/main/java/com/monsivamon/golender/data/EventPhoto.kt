package com.monsivamon.golender.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// 予定に添付された写真のレコード（実ファイルは filesDir/event_photos/ に保存）
@Entity(tableName = "event_photos", indices = [Index("eventId")])
data class EventPhoto(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val eventId: Long,
    val fileName: String,
    val position: Int,
    val createdAt: Long = System.currentTimeMillis(),
) {
    companion object {
        // 1 予定あたりの最大添付枚数
        const val MAX_PHOTOS_PER_EVENT = 5
    }
}