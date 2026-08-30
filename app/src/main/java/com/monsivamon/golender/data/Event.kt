package com.monsivamon.golender.data

// システムカレンダーとローカルで共通の予定データクラス
data class Event(
    val id: Long,
    val title: String,
    val startTime: Long,
    val endTime: Long,
    val isAllDay: Boolean,
    val calendarId: Long = 0L,
    val location: String = "",
    val description: String = "",
    val rrule: String? = null,
    val isReadOnly: Boolean = false,
    val isHolidayCalendar: Boolean = false,
    val isCulturalEvent: Boolean = false,
    val isBirthdayCalendar: Boolean = false
)