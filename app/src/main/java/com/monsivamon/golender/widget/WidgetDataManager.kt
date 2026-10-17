package com.monsivamon.golender.widget

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import com.monsivamon.golender.data.CalendarRepository
import com.monsivamon.golender.data.Event
import com.monsivamon.golender.data.SelectedCalendarJson
import com.monsivamon.golender.data.dataStore
import com.monsivamon.golender.data.prefs.SettingsKeys
import com.monsivamon.golender.data.util.occursOn
import com.monsivamon.golender.viewmodel.ThemeMode
import kotlinx.coroutines.flow.first
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

// 各ウィジェットに表示するデータを収集・加工するオブジェクト
object WidgetDataManager {

    // 日次ウィジェット用データ（今日の予定一覧）を組み立てる
    suspend fun getDayWidgetData(context: Context): DayWidgetData {
        // DataStore からテーマと背景色を取得する
        val prefs = context.dataStore.data.first()
        val themeMode = resolveThemeMode(prefs)
        val bgColor = resolveBgColor(prefs)
        // 今日に該当する予定を抽出し、開始時刻順で軽量イベントに変換する
        val today = LocalDate.now()
        val events = getEventsForRange(context, prefs, today, today)
            .filter { it.occursOn(today) }
            .sortedBy { it.startTime }
            .map { it.toWidgetEvent() }
        return DayWidgetData(date = today, events = events, bgColor = bgColor, themeMode = themeMode)
    }

    // 週次ウィジェット用データ（7 日分の日別予定マップ）を組み立てる
    suspend fun getWeekWidgetData(context: Context): WeekWidgetData {
        // DataStore からテーマ・背景色・週開始曜日を取得する
        val prefs = context.dataStore.data.first()
        val themeMode = resolveThemeMode(prefs)
        val bgColor = resolveBgColor(prefs)
        val weekStartDay = resolveWeekStartDay(prefs)
        // 週の開始日と終了日を算出する
        val today = LocalDate.now()
        val offset = (today.dayOfWeek.value - weekStartDay.value + 7) % 7
        val weekStart = today.minusDays(offset.toLong())
        val weekEnd = weekStart.plusDays(6)
        // 週内の予定を取得し、日付ごとにグループ化する
        val events = getEventsForRange(context, prefs, weekStart, weekEnd)
        val grouped = mutableMapOf<LocalDate, List<WidgetEvent>>()
        for (d in 0..6) {
            val date = weekStart.plusDays(d.toLong())
            grouped[date] = events.filter { it.occursOn(date) }
                .sortedBy { it.startTime }
                .map { it.toWidgetEvent() }
        }
        return WeekWidgetData(
            weekStart = weekStart, weekEnd = weekEnd, events = grouped,
            weekStartDay = weekStartDay, bgColor = bgColor, themeMode = themeMode,
            dayColors = resolveDayColors(prefs),
        )
    }

    // 月次ウィジェット用データ（グリッド全セルの日別予定マップ）を組み立てる
    suspend fun getMonthWidgetData(context: Context): MonthWidgetData {
        val prefs = context.dataStore.data.first()
        val themeMode = resolveThemeMode(prefs)
        val bgColor = resolveBgColor(prefs)
        val weekStartDay = resolveWeekStartDay(prefs)
        // 月グリッドの前後月セル分も含めた日付範囲を算出する
        val yearMonth = YearMonth.now()
        val firstDayOfMonth = yearMonth.atDay(1)
        val daysInMonth = yearMonth.lengthOfMonth()
        val offset = (firstDayOfMonth.dayOfWeek.value - weekStartDay.value + 7) % 7
        val totalCells = ((daysInMonth + offset + 6) / 7) * 7
        val gridStartDate = firstDayOfMonth.minusDays(offset.toLong())
        val gridEndDate = gridStartDate.plusDays((totalCells - 1).toLong())
        // グリッド全体（前月・翌月セル含む）の予定を取得する
        val events = getEventsForRange(context, prefs, gridStartDate, gridEndDate)
        // グリッドの全セル（前後月含む）に対して日付ごとの予定をマッピングする
        val grouped = mutableMapOf<LocalDate, List<WidgetEvent>>()
        for (i in 0 until totalCells) {
            val date = gridStartDate.plusDays(i.toLong())
            grouped[date] = events.filter { it.occursOn(date) }
                .sortedBy { it.startTime }
                .map { it.toWidgetEvent() }
        }
        return MonthWidgetData(
            yearMonth = yearMonth, events = grouped,
            weekStartDay = weekStartDay, bgColor = bgColor, themeMode = themeMode,
            dayColors = resolveDayColors(prefs),
        )
    }

    private fun resolveThemeMode(prefs: Preferences): ThemeMode {
        return try { ThemeMode.valueOf(prefs[SettingsKeys.THEME] ?: "SYSTEM") }
        catch (_: Exception) { ThemeMode.SYSTEM }
    }

    private fun resolveBgColor(prefs: Preferences): Int? {
        val bgStr = prefs[SettingsKeys.BG_COLOR]
        if (bgStr == null || bgStr == SettingsKeys.COLOR_UNSPECIFIED) return null
        return try { bgStr.toInt() } catch (_: Exception) { null }
    }

    private fun resolveWeekStartDay(prefs: Preferences): DayOfWeek {
        return try { DayOfWeek.valueOf(prefs[SettingsKeys.WEEK_START] ?: "SUNDAY") }
        catch (_: Exception) { DayOfWeek.SUNDAY }
    }

    // ユーザー設定の曜日別文字色を読み込む（null =「標準」を明示選択）。
    // 日曜・土曜は未設定時もアプリ内カレンダーと同じ既定色（赤・青）を適用する。
    private fun resolveDayColors(prefs: Preferences): Map<DayOfWeek, Int?> {
        val map = mutableMapOf<DayOfWeek, Int?>(
            DayOfWeek.SUNDAY to SettingsKeys.DEFAULT_SUNDAY_COLOR_ARGB,
            DayOfWeek.SATURDAY to SettingsKeys.DEFAULT_SATURDAY_COLOR_ARGB,
        )
        DayOfWeek.values().forEach { day ->
            prefs[SettingsKeys.dayColor(day)]?.let { colorStr ->
                map[day] = if (colorStr == SettingsKeys.COLOR_UNSPECIFIED) {
                    null
                } else {
                    try { colorStr.toInt() } catch (_: Exception) { null }
                }
            }
        }
        return map
    }

    private suspend fun getEventsForRange(
        context: Context, prefs: Preferences,
        start: LocalDate, end: LocalDate,
    ): List<Event> {
        val mode = prefs[SettingsKeys.MODE] ?: "GOLENDAR"
        val repo = CalendarRepository(context)
        val startMillis = start.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val endMillis = end.atTime(23, 59, 59).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val events = if (mode == "GOOGLE") {
            val selectedJson = prefs[SettingsKeys.SELECTED_CALENDARS]
            val visibleIds = SelectedCalendarJson.fromJson(selectedJson ?: "")
                .filter { it.isVisible }.map { it.calendarId }
            if (visibleIds.isEmpty()) emptyList()
            else repo.getEventsForMonth(startMillis, endMillis, visibleIds)
        } else {
            val calendars = repo.getAllLocalCalendars()
            val visibleIds = calendars.filter { it.isVisible }.map { it.id }
            if (visibleIds.isEmpty()) emptyList()
            else repo.getLocalEventsForMonth(startMillis, endMillis, visibleIds)
        }
        // 「祝日を表示」ON/OFF をウィジェットにも適用する（アプリ内カレンダーと同じ挙動）。
        return if (prefs[SettingsKeys.SHOW_HOLIDAYS] ?: true) events else events.filter { event ->
            if (mode == "GOOGLE") {
                !event.isHolidayCalendar
            } else {
                // Golendar モードの祝日は記載マーカー(description)で判別する
                event.description != com.monsivamon.golender.data.LocalEvent.DESCRIPTION_HOLIDAY
            }
        }
    }

    private fun Event.toWidgetEvent(): WidgetEvent = WidgetEvent(
        id = id, title = title, startTime = startTime, endTime = endTime, isAllDay = isAllDay,
    )
}