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
            weekStart = weekStart,
            weekEnd = weekEnd,
            events = grouped,
            weekStartDay = weekStartDay,
            bgColor = bgColor,
            themeMode = themeMode,
        )
    }

    // 月次ウィジェット用データ（グリッド全セルの日別予定マップ）を組み立てる
    suspend fun getMonthWidgetData(context: Context): MonthWidgetData {
        // DataStore からテーマ・背景色・週開始曜日を取得する
        val prefs = context.dataStore.data.first()
        val themeMode = resolveThemeMode(prefs)
        val bgColor = resolveBgColor(prefs)
        val weekStartDay = resolveWeekStartDay(prefs)
        val yearMonth = YearMonth.now()

        // 月グリッドの前後月セル分も含めた日付範囲を算出する
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
            yearMonth = yearMonth,
            events = grouped,
            weekStartDay = weekStartDay,
            bgColor = bgColor,
            themeMode = themeMode,
        )
    }

    // Preferences からテーマモードを解決する（未設定/不正時は SYSTEM）
    private fun resolveThemeMode(prefs: Preferences): ThemeMode {
        return try {
            ThemeMode.valueOf(prefs[SettingsKeys.THEME] ?: "SYSTEM")
        } catch (_: Exception) { ThemeMode.SYSTEM }
    }

    // Preferences から背景色 ARGB を解決する（未設定/不正時は null）
    private fun resolveBgColor(prefs: Preferences): Int? {
        val bgStr = prefs[SettingsKeys.BG_COLOR]
        if (bgStr == null || bgStr == SettingsKeys.COLOR_UNSPECIFIED) return null
        return try { bgStr.toInt() } catch (_: Exception) { null }
    }

    // Preferences から週の開始曜日を解決する（未設定/不正時は SUNDAY）
    private fun resolveWeekStartDay(prefs: Preferences): DayOfWeek {
        return try {
            DayOfWeek.valueOf(prefs[SettingsKeys.WEEK_START] ?: "SUNDAY")
        } catch (_: Exception) { DayOfWeek.SUNDAY }
    }

    // モードに応じて指定期間の予定を取得する（Google / ローカル）
    private suspend fun getEventsForRange(
        context: Context,
        prefs: Preferences,
        start: LocalDate,
        end: LocalDate,
    ): List<Event> {
        // 期間をミリ秒に変換し、モード別に予定を取得する
        val mode = prefs[SettingsKeys.MODE] ?: "GOLENDAR"
        val repo = CalendarRepository(context)
        val startMillis = start.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val endMillis = end.atTime(23, 59, 59).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        return if (mode == "GOOGLE") {
            // 可視カレンダー ID のみを対象に取得する（空なら予定なし）
            val selectedJson = prefs[SettingsKeys.SELECTED_CALENDARS]
            val visibleIds = SelectedCalendarJson.fromJson(selectedJson ?: "")
                .filter { it.isVisible }
                .map { it.calendarId }
            if (visibleIds.isEmpty()) emptyList()
            else repo.getEventsForMonth(startMillis, endMillis, visibleIds)
        } else {
            repo.getLocalEventsForMonth(startMillis, endMillis)
        }
    }

    // Event をウィジェット表示用の軽量データへ変換する
    private fun Event.toWidgetEvent(): WidgetEvent = WidgetEvent(
        id = id,
        title = title,
        startTime = startTime,
        endTime = endTime,
        isAllDay = isAllDay,
    )
}