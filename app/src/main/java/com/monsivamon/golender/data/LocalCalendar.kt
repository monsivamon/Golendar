package com.monsivamon.golender.data

import androidx.room.Entity
import androidx.room.PrimaryKey

// Golendar モード専用のローカルカレンダー
@Entity(tableName = "local_calendars")
data class LocalCalendar(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,                         // 表示名（既定カレンダーは変更不可）
    val colorArgb: Int,                       // 0 = テーマ連動の自動割り当て
    val isVisible: Boolean = true,            // 表示 ON/OFF
    val sortOrder: Int = 0,                   // 並び順
    val isDefault: Boolean = false,           // 既定（マイカレンダー）かどうか
    val createdAt: Long = System.currentTimeMillis()
)