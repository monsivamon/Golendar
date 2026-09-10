package com.monsivamon.golender.widget

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import com.monsivamon.golender.viewmodel.ThemeMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.glance.color.ColorProvider
import androidx.glance.unit.ColorProvider

// テーマモードに応じたColorProviderを生成（ライト/ダークで色を切り替え）
fun getWidgetColorProvider(themeMode: ThemeMode, lightColor: Color, darkColor: Color): ColorProvider {
    return when (themeMode) {
        ThemeMode.LIGHT -> ColorProvider(day = lightColor, night = lightColor)
        ThemeMode.DARK -> ColorProvider(day = darkColor, night = darkColor)
        ThemeMode.SYSTEM -> ColorProvider(day = lightColor, night = darkColor)
    }
}

// カスタム背景色の輝度に応じて文字色を自動反転させる（明るい背景なら暗い色、暗い背景なら明るい色）
fun getAdaptiveColorProvider(themeMode: ThemeMode, customBg: Color?, lightThemeColor: Color, darkThemeColor: Color): ColorProvider {
    if (customBg != null) {
        // 背景の輝度を計算（0.5より大きければ明るい背景とみなす）
        val isLight = customBg.luminance() > 0.5f
        // 背景の明るさに応じて、ライト用/ダーク用の色を強制的に適用
        val fixedColor = if (isLight) lightThemeColor else darkThemeColor
        return ColorProvider(day = fixedColor, night = fixedColor)
    }
    // カスタム背景がなければ通常のテーマ連動
    return getWidgetColorProvider(themeMode, lightThemeColor, darkThemeColor)
}

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
    val bgColor: Int? = null,
    val themeMode: ThemeMode = ThemeMode.SYSTEM
)

// 週次ウィジェットの表示データ
data class WeekWidgetData(
    val weekStart: LocalDate,
    val weekEnd: LocalDate,
    val events: Map<LocalDate, List<WidgetEvent>>,
    val weekStartDay: DayOfWeek,
    val bgColor: Int? = null,
    val themeMode: ThemeMode = ThemeMode.SYSTEM
)

// 月次ウィジェットの表示データ
data class MonthWidgetData(
    val yearMonth: YearMonth,
    val events: Map<LocalDate, List<WidgetEvent>>,
    val weekStartDay: DayOfWeek,
    val bgColor: Int? = null,
    val themeMode: ThemeMode = ThemeMode.SYSTEM
)