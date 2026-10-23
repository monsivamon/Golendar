package com.monsivamon.golender.data

// システムカレンダーとローカル予定で共通に扱う予定データクラス
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
    val isBirthdayCalendar: Boolean = false,
    val calendarColorArgb: Int? = null,
    val calendarDisplayName: String = "",
    // 予定ごとの任意色（ARGB）。0 は未設定を表し、カレンダー色 → 既定色の順で解決する
    val colorArgb: Int = 0,
    // 祝日の対象国 ISO コード（同日に複数国の祝日がある場合は複数）
    val holidayCountries: List<String> = emptyList(),
)