package com.monsivamon.golender.data

import androidx.room.Entity
import androidx.room.PrimaryKey

// ローカルDB保存用の予定エンティティ（自動生成ID）
@Entity(tableName = "local_events")
data class LocalEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String,
    val startTime: Long,
    val endTime: Long,
    val isAllDay: Boolean,
    val location: String,
    val description: String,
    val rrule: String? = null
)