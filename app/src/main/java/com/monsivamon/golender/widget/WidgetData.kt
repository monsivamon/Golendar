package com.monsivamon.golender.widget

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

// ウィジェット表示用の予定データ（軽量版）
data class WidgetEvent(
    val id: Long,
    val title: String,
    val startTime: Long,
    val endTime: Long,
    val isAllDay: Boolean
)

// 日次ウィジェットの表示データ
data class DayWidgetData(
    val date: LocalDate,
    val events: List<WidgetEvent>,
    val bgColor: Int? = null
)

// 週次ウィジェットの表示データ
data class WeekWidgetData(
    val weekStart: LocalDate,
    val weekEnd: LocalDate,
    val events: Map<LocalDate, List<WidgetEvent>>,
    val weekStartDay: DayOfWeek,
    val bgColor: Int? = null
)

// 月次ウィジェットの表示データ
data class MonthWidgetData(
    val yearMonth: YearMonth,
    val events: Map<LocalDate, List<WidgetEvent>>,
    val weekStartDay: DayOfWeek,
    val bgColor: Int? = null
)