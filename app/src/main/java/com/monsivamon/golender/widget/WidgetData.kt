package com.monsivamon.golender.widget

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.glance.unit.ColorProvider
import androidx.glance.color.ColorProvider as DayNightColorProvider
import com.monsivamon.golender.viewmodel.ThemeMode
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

// ウィジェット用の色セット（アプリUI側AppColorsと同じロジックで生成）
data class WidgetColorSet(
    val bg: ColorProvider,
    val surface: ColorProvider,
    val text: ColorProvider,
    val textGray: ColorProvider,
    val primaryAccent: ColorProvider,
    val sunRed: ColorProvider,
    val satBlue: ColorProvider,
)

// テーマモードと背景色からウィジェット用の全色を導出
// ・カスタム背景色未設定 → テーマに応じた既定色
// ・ダークテーマ + 明るい色 → 黒を65%混ぜて自動暗色化
// ・SYSTEM時はday/nightに2色を渡し、Android側が自動切替
fun computeWidgetColors(themeMode: ThemeMode, customBgArgb: Int?): WidgetColorSet {
    val customBg = customBgArgb?.let { Color(it) }

    // ライト側の背景色
    val lightBg = customBg ?: Color(0xFFF0F2F5)

    // ダーク側の背景色（カスタム色が明るければ黒を混ぜて暗色化）
    val darkBg = when {
        customBg == null -> Color(0xFF121212)
        customBg.luminance() > 0.5f -> lerp(customBg, Color.Black, 0.65f)
        else -> customBg
    }

    return WidgetColorSet(
        bg = provider(themeMode, lightBg, darkBg),
        surface = provider(
            themeMode,
            lerp(lightBg, Color.White, 0.55f),
            lerp(darkBg, Color.White, 0.08f),
        ),
        text = provider(themeMode, Color(0xFF1A1A1A), Color(0xFFF1F3F4)),
        textGray = provider(themeMode, Color(0xFF666666), Color(0xFFAAAAAA)),
        // ダーク時のアクセントは彩度を落とした紫（アプリUIと統一）
        primaryAccent = provider(themeMode, Color(0xFF6A1B9A), Color(0xFFB39DDB)),
        sunRed = provider(themeMode, Color(0xFFE53935), Color(0xFFEF9A9A)),
        satBlue = provider(themeMode, Color(0xFF1E88E5), Color(0xFF81D4FA)),
    )
}

// テーマモードに応じてday/nightを同一色にするか分岐させるヘルパー
private fun provider(themeMode: ThemeMode, lightColor: Color, darkColor: Color): ColorProvider {
    return when (themeMode) {
        ThemeMode.LIGHT -> DayNightColorProvider(day = lightColor, night = lightColor)
        ThemeMode.DARK -> DayNightColorProvider(day = darkColor, night = darkColor)
        ThemeMode.SYSTEM -> DayNightColorProvider(day = lightColor, night = darkColor)
    }
}

// ウィジェット表示用の予定データ（軽量版）
data class WidgetEvent(
    val id: Long,
    val title: String,
    val startTime: Long,
    val endTime: Long,
    val isAllDay: Boolean,
)

// 日次ウィジェットの表示データ
data class DayWidgetData(
    val date: LocalDate,
    val events: List<WidgetEvent>,
    val bgColor: Int? = null,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
)

// 週次ウィジェットの表示データ
data class WeekWidgetData(
    val weekStart: LocalDate,
    val weekEnd: LocalDate,
    val events: Map<LocalDate, List<WidgetEvent>>,
    val weekStartDay: DayOfWeek,
    val bgColor: Int? = null,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
)

// 月次ウィジェットの表示データ
data class MonthWidgetData(
    val yearMonth: YearMonth,
    val events: Map<LocalDate, List<WidgetEvent>>,
    val weekStartDay: DayOfWeek,
    val bgColor: Int? = null,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
)