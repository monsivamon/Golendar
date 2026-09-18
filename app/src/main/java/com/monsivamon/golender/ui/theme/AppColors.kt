package com.monsivamon.golender.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import com.monsivamon.golender.viewmodel.ThemeMode

// アプリ全体のカラーパレットを保持するデータクラス。
data class AppColors(
    val bg: Color = Color.Unspecified,
    val surface: Color = Color.Unspecified,
    val text: Color = Color.Unspecified,
    val textGray: Color = Color.Unspecified,
    val primaryAccent: Color = Color.Unspecified,
    val sunRed: Color = Color.Unspecified,
    val satBlue: Color = Color.Unspecified,
    val divider: Color = Color.Unspecified,
)

// テーマと背景色から最適なカラーパレットを生成する。
@Composable
fun getAppColors(themeMode: ThemeMode, customBg: Color = Color.Unspecified): AppColors {
    val isDark = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    val bg = when {
        customBg == Color.Unspecified || customBg == Color.Transparent ->
            if (isDark) Color(0xFF121212) else Color(0xFFF0F2F5)

        isDark && customBg.luminance() > 0.5f ->
            darkVariant(customBg, 0.65f)

        else -> customBg
    }

    val light = bg.luminance() > 0.5f

    val surface = if (light) lerp(bg, Color.White, 0.55f) else lerp(bg, Color.White, 0.08f)
    val divider = if (light) lerp(bg, Color.Black, 0.08f) else lerp(bg, Color.White, 0.15f)

    return AppColors(
        bg = bg,
        surface = surface,
        text = if (light) Color(0xFF1A1A1A) else Color(0xFFF1F3F4),
        textGray = if (light) Color(0xFF666666) else Color(0xFFAAAAAA),
        primaryAccent = if (light) Color(0xFF6A1B9A) else Color(0xFFB39DDB),
        sunRed = if (light) Color(0xFFE53935) else Color(0xFFEF9A9A),
        satBlue = if (light) Color(0xFF1E88E5) else Color(0xFF81D4FA),
        divider = divider,
    )
}

// 曜日色に使う16色のカラーパレット。
val DayColorPalette = listOf(
    Color(0xFFE53935), Color(0xFFD81B60), Color(0xFF8E24AA), Color(0xFF5E35B1),
    Color(0xFF3949AB), Color(0xFF1E88E5), Color(0xFF039BE5), Color(0xFF00ACC1),
    Color(0xFF00897B), Color(0xFF43A047), Color(0xFF7CB342), Color(0xFFC0CA33),
    Color(0xFFFBC02D), Color(0xFFFFB300), Color(0xFFFB8C00), Color(0xFFF4511E),
)

// 背景色に使う16色のパステルカラーパレット。
val PastelColorPalette = listOf(
    Color(0xFFFFB3BA), Color(0xFFFFDFBA), Color(0xFFFFFFBA), Color(0xFFBAFFC9),
    Color(0xFFBAE1FF), Color(0xFFE6B3FF), Color(0xFFFFC6FF), Color(0xFFC4FAF8),
    Color(0xFFA0E8AF), Color(0xFFFFD1DC), Color(0xFFFDFD96), Color(0xFFE2F0CB),
    Color(0xFFB5EAD7), Color(0xFFC7CEEA), Color(0xFFF4C2C2), Color(0xFFFDECDA),
)

// 明色を黒とブレンドしてダークテーマ向けの暗色を生成する。
fun darkVariant(color: Color, factor: Float = 0.65f): Color =
    lerp(color, Color.Black, factor)

// 現在のテーマでパステル色がどう表示されるかを返す（プレビュー用）。
fun previewColor(base: Color, isDarkTheme: Boolean): Color =
    if (isDarkTheme) darkVariant(base) else base