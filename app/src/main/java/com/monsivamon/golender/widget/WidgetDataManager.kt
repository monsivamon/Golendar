package com.monsivamon.golender.widget

import android.content.Context
import com.monsivamon.golender.data.CalendarRepository
import com.monsivamon.golender.data.Event
import com.monsivamon.golender.data.dataStore
import com.monsivamon.golender.data.prefs.SettingsKeys
import com.monsivamon.golender.data.util.localEndDate
import com.monsivamon.golender.data.util.localStartDate
import com.monsivamon.golender.data.util.occursOn
import com.monsivamon.golender.viewmodel.ThemeMode
import kotlinx.coroutines.flow.first
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

// 各ウィジェットに表示するデータを収集・加工するオブジェクト
object WidgetDataManager {

    private fun getRepository(context: Context): CalendarRepository = CalendarRepository(context)

    // DataStoreからテーマモードを取得（デフォルトはSYSTEM）
    private suspend fun getThemeMode(context: Context): ThemeMode {
        val prefs = context.dataStore.data.first()
        return prefs[SettingsKeys.THEME]?.let {
            try { ThemeMode.valueOf(it) } catch (e: Exception) { ThemeMode.SYSTEM }
        } ?: ThemeMode.SYSTEM
    }

    // DataStoreから週の開始曜日を取得（デフォルトは日曜日）
    private suspend fun getWeekStartDay(context: Context): DayOfWeek {
        val prefs = context.dataStore.data.first()
        return prefs[SettingsKeys.WEEK_START]?.let {
            try { DayOfWeek.valueOf(it) } catch (e: Exception) { DayOfWeek.SUNDAY }
        } ?: DayOfWeek.SUNDAY
    }

    // DataStoreから背景色を取得（未設定はnull）
    private suspend fun getBgColor(context: Context): Int? {
        val prefs = context.dataStore.data.first()
        val colorStr = prefs[SettingsKeys.BG_COLOR]
        return if (colorStr == null || colorStr == SettingsKeys.COLOR_UNSPECIFIED) null
        else colorStr.toIntOrNull()
    }

    // 指定期間の予定をカレンダーモードに応じて取得
    private suspend fun getEventsForRange(context: Context, start: Long, end: Long): List<Event> {
        val repo = getRepository(context)
        val prefs = context.dataStore.data.first()
        val mode = prefs[SettingsKeys.MODE] ?: "GOLENDAR"

        return try {
            if (mode == "GOOGLE") {
                val account = prefs[SettingsKeys.ACCOUNT]
                val calendarIds = account?.let { repo.getCalendarIdsForAccount(it) }
                repo.getEventsForMonth(start, end, calendarIds)
            } else {
                repo.getLocalEventsForMonth(start, end)
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    // 日次ウィジェット用データ（今日の予定）
    suspend fun getDayWidgetData(context: Context): DayWidgetData {
        val today = LocalDate.now()
        val start = today.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val end = today.atTime(23, 59, 59).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        val events = getEventsForRange(context, start, end).filter { it.occursOn(today) }

        return DayWidgetData(
            date = today,
            events = events.map { WidgetEvent(it.id, it.title, it.startTime, it.endTime, it.isAllDay) },
            bgColor = getBgColor(context),
            themeMode = getThemeMode(context),
        )
    }

    // 週次ウィジェット用データ（週の日付ごとに予定をマッピング）
    suspend fun getWeekWidgetData(context: Context): WeekWidgetData {
        val today = LocalDate.now()
        val weekStartDay = getWeekStartDay(context)

        val offset = (today.dayOfWeek.value - weekStartDay.value + 7) % 7
        val startOfWeek = today.minusDays(offset.toLong())
        val endOfWeek = startOfWeek.plusDays(6)

        val start = startOfWeek.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val end = endOfWeek.atTime(23, 59, 59).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        val events = getEventsForRange(context, start, end)
        val eventsByDate = mutableMapOf<LocalDate, MutableList<WidgetEvent>>()

        // 複数日にまたがる予定は各日に分配してマッピング
        events.forEach { event ->
            val current = maxOf(event.localStartDate(), startOfWeek)
            val limit = minOf(event.localEndDate(), endOfWeek)
            var d = current
            while (!d.isAfter(limit)) {
                eventsByDate.getOrPut(d) { mutableListOf() }
                    .add(WidgetEvent(event.id, event.title, event.startTime, event.endTime, event.isAllDay))
                d = d.plusDays(1)
            }
        }

        return WeekWidgetData(
            weekStart = startOfWeek,
            weekEnd = endOfWeek,
            events = eventsByDate.mapValues { it.value.toList() },
            weekStartDay = weekStartDay,
            bgColor = getBgColor(context),
            themeMode = getThemeMode(context),
        )
    }

    // 月次ウィジェット用データ（カレンダーグリッド全体の予定）
    suspend fun getMonthWidgetData(context: Context): MonthWidgetData {
        val today = LocalDate.now()
        val yearMonth = YearMonth.from(today)
        val weekStartDay = getWeekStartDay(context)

        val firstDayOfMonth = yearMonth.atDay(1)
        val daysInMonth = yearMonth.lengthOfMonth()
        val offset = (firstDayOfMonth.dayOfWeek.value - weekStartDay.value + 7) % 7
        val totalCells = ((daysInMonth + offset + 6) / 7) * 7

        // グリッド全体の日付範囲（前月・翌月の日付も含む）
        val gridStartDate = firstDayOfMonth.minusDays(offset.toLong())
        val gridEndDate = gridStartDate.plusDays((totalCells - 1).toLong())

        val start = gridStartDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val end = gridEndDate.atTime(23, 59, 59).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        val events = getEventsForRange(context, start, end)
        val eventsByDate = mutableMapOf<LocalDate, MutableList<WidgetEvent>>()

        // 複数日にまたがる予定は各日に分配してマッピング
        events.forEach { event ->
            val current = maxOf(event.localStartDate(), gridStartDate)
            val limit = minOf(event.localEndDate(), gridEndDate)
            var d = current
            while (!d.isAfter(limit)) {
                eventsByDate.getOrPut(d) { mutableListOf() }
                    .add(WidgetEvent(event.id, event.title, event.startTime, event.endTime, event.isAllDay))
                d = d.plusDays(1)
            }
        }

        return MonthWidgetData(
            yearMonth = yearMonth,
            events = eventsByDate.mapValues { it.value.toList() },
            weekStartDay = weekStartDay,
            bgColor = getBgColor(context),
            themeMode = getThemeMode(context),
        )
    }
}