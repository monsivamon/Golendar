package com.monsivamon.golender.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// ローカル予定に添付された写真（ファイル実体は filesDir/event_photos/ に保存）
@Entity(tableName = "event_photos", indices = [Index("eventId")])
data class EventPhoto(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val eventId: Long,
    val fileName: String,
    val position: Int,
    val createdAt: Long = System.currentTimeMillis(),
) {
    companion object {
        const val MAX_PHOTOS_PER_EVENT = 5
    }
}