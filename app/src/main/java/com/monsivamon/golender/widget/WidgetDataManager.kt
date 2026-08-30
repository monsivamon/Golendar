package com.monsivamon.golender.widget

import android.content.Context
import androidx.datastore.preferences.core.stringPreferencesKey
import com.monsivamon.golender.data.CalendarRepository
import com.monsivamon.golender.data.Event
import com.monsivamon.golender.data.dataStore
import kotlinx.coroutines.flow.first
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset

// 各ウィジェットに表示するデータを収集・加工するオブジェクト
object WidgetDataManager {

    private fun getRepository(context: Context): CalendarRepository = CalendarRepository(context)

    private val WEEK_START_KEY = stringPreferencesKey("week_start_day")
    private val MODE_KEY = stringPreferencesKey("calendar_mode")
    private val ACCOUNT_KEY = stringPreferencesKey("selected_account")
    private val BG_COLOR_KEY = stringPreferencesKey("calendar_bg_color")

    // DataStoreから週の開始曜日を取得（デフォルトは日曜日）
    private suspend fun getWeekStartDay(context: Context): DayOfWeek {
        val prefs = context.dataStore.data.first()
        return prefs[WEEK_START_KEY]?.let { try { DayOfWeek.valueOf(it) } catch (e: Exception) { DayOfWeek.SUNDAY } } ?: DayOfWeek.SUNDAY
    }

    // DataStoreから背景色を取得（未設定の場合はnull）
    private suspend fun getBgColor(context: Context): Int? {
        val prefs = context.dataStore.data.first()
        val colorStr = prefs[BG_COLOR_KEY]
        return if (colorStr == null || colorStr == "UNSPECIFIED") null else colorStr.toIntOrNull()
    }

    // 指定期間の予定をカレンダーモードに応じて取得（GoogleモードはアカウントIDでフィルタ）
    private suspend fun getEventsForRange(context: Context, start: Long, end: Long): List<Event> {
        val repo = getRepository(context)
        val prefs = context.dataStore.data.first()
        val mode = prefs[MODE_KEY] ?: "GOLENDAR"

        return try {
            if (mode == "GOOGLE") {
                val account = prefs[ACCOUNT_KEY]
                val calendarIds = account?.let { repo.getCalendarIdsForAccount(it) }
                repo.getEventsForMonth(start, end, calendarIds)
            } else {
                repo.getLocalEventsForMonth(start, end)
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    // 日次ウィジェット用データを生成（今日の日付でフィルタ）
    suspend fun getDayWidgetData(context: Context): DayWidgetData {
        val today = LocalDate.now()
        val start = today.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val end = today.atTime(23, 59, 59).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        val events = getEventsForRange(context, start, end)
        // 終日予定はUTC、時間指定はシステムタイムゾーンで日付判定
        val filtered = events.filter { event ->
            val zone = if (event.isAllDay) ZoneOffset.UTC else ZoneId.systemDefault()
            val eventStart = LocalDateTime.ofInstant(Instant.ofEpochMilli(event.startTime), zone).toLocalDate()
            val adjustedEndTime = if (event.endTime > event.startTime) event.endTime - 1 else event.endTime
            val eventEnd = LocalDateTime.ofInstant(Instant.ofEpochMilli(adjustedEndTime), zone).toLocalDate()
            today in eventStart..eventEnd
        }

        return DayWidgetData(
            date = today,
            events = filtered.map { WidgetEvent(it.id, it.title, it.startTime, it.endTime, it.isAllDay) },
            bgColor = getBgColor(context)
        )
    }

    // 週次ウィジェット用データを生成（週の開始〜終了範囲でフィルタ）
    suspend fun getWeekWidgetData(context: Context): WeekWidgetData {
        val today = LocalDate.now()
        val weekStartDay = getWeekStartDay(context)

        val offset = (today.dayOfWeek.value - weekStartDay.value + 7) % 7
        val startOfWeek = today.minusDays(offset.toLong())
        val endOfWeek = startOfWeek.plusDays(6)

        val start = startOfWeek.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val end = endOfWeek.atTime(23, 59, 59).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        val events = getEventsForRange(context, start, end)
        // イベントを日付ごとに展開（複数日にまたがる場合は各日に分配）
        val eventsByDate = mutableMapOf<LocalDate, MutableList<WidgetEvent>>()

        events.forEach { event ->
            val zone = if (event.isAllDay) ZoneOffset.UTC else ZoneId.systemDefault()
            val eventStart = LocalDateTime.ofInstant(Instant.ofEpochMilli(event.startTime), zone).toLocalDate()
            val adjustedEndTime = if (event.endTime > event.startTime) event.endTime - 1 else event.endTime
            val eventEnd = LocalDateTime.ofInstant(Instant.ofEpochMilli(adjustedEndTime), zone).toLocalDate()

            val current = maxOf(eventStart, startOfWeek)
            val limit = minOf(eventEnd, endOfWeek)
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
            bgColor = getBgColor(context)
        )
    }

    // 月次ウィジェット用データを生成（カレンダーグリッド全体の範囲でフィルタ）
    suspend fun getMonthWidgetData(context: Context): MonthWidgetData {
        val today = LocalDate.now()
        val yearMonth = YearMonth.from(today)
        val weekStartDay = getWeekStartDay(context)

        val firstDayOfMonth = yearMonth.atDay(1)
        val daysInMonth = yearMonth.lengthOfMonth()
        val offset = (firstDayOfMonth.dayOfWeek.value - weekStartDay.value + 7) % 7
        val totalCells = ((daysInMonth + offset + 6) / 7) * 7

        // グリッド全体の日付範囲を計算（前月・次月の日付も含む）
        val gridStartDate = firstDayOfMonth.minusDays(offset.toLong())
        val gridEndDate = gridStartDate.plusDays((totalCells - 1).toLong())

        val start = gridStartDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val end = gridEndDate.atTime(23, 59, 59).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        val events = getEventsForRange(context, start, end)
        // イベントを日付ごとに展開（グリッド範囲内のみ）
        val eventsByDate = mutableMapOf<LocalDate, MutableList<WidgetEvent>>()

        events.forEach { event ->
            val zone = if (event.isAllDay) ZoneOffset.UTC else ZoneId.systemDefault()
            val eventStart = LocalDateTime.ofInstant(Instant.ofEpochMilli(event.startTime), zone).toLocalDate()
            val adjustedEndTime = if (event.endTime > event.startTime) event.endTime - 1 else event.endTime
            val eventEnd = LocalDateTime.ofInstant(Instant.ofEpochMilli(adjustedEndTime), zone).toLocalDate()

            val current = maxOf(eventStart, gridStartDate)
            val limit = minOf(eventEnd, gridEndDate)
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
            bgColor = getBgColor(context)
        )
    }
}