package com.monsivamon.golender.data

import androidx.room.Entity
import androidx.room.PrimaryKey

// 予定に紐づく拡張情報（メモなど）
@Entity(tableName = "event_extras")
data class EventExtra(
    @PrimaryKey
    val eventId: Long,
    val note: String? = null
)