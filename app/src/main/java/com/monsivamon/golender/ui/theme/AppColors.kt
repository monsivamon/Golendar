package com.monsivamon.golender.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import com.monsivamon.golender.viewmodel.ThemeMode

// アプリ全体のカラーパレット
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

// テーマと背景色から最適なカラーパレットを生成する
// ・ユーザーは色相のみを選び、明暗はテーマが自動決定
// ・ダークテーマ時に明るいカスタム色が選ばれた場合は自動で暗色化
@Composable
fun getAppColors(themeMode: ThemeMode, customBg: Color = Color.Unspecified): AppColors {
    val isDark = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    // 有効な背景色を決定（ダークテーマ+明るいカスタム色は自動暗色化）
    val bg = when {
        customBg == Color.Unspecified || customBg == Color.Transparent ->
            if (isDark) Color(0xFF121212) else Color(0xFFF0F2F5)

        isDark && customBg.luminance() > 0.5f ->
            darkVariant(customBg, 0.65f)

        else -> customBg
    }

    // 実際の背景色の輝度から明暗を判定（テーマではなく背景基準）
    val light = bg.luminance() > 0.5f

    // 背景色から自然なsurface/dividerを導出
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

// 曜日色パレット（16色 / 彩度高め）
val DayColorPalette = listOf(
    Color(0xFFE53935), Color(0xFFD81B60), Color(0xFF8E24AA), Color(0xFF5E35B1),
    Color(0xFF3949AB), Color(0xFF1E88E5), Color(0xFF039BE5), Color(0xFF00ACC1),
    Color(0xFF00897B), Color(0xFF43A047), Color(0xFF7CB342), Color(0xFFC0CA33),
    Color(0xFFFBC02D), Color(0xFFFFB300), Color(0xFFFB8C00), Color(0xFFF4511E),
)

// 背景色パレット（16色 / パステル）
// 明色の色相サンプルとして扱い、ダークテーマ時はgetAppColors()で暗色化される
val PastelColorPalette = listOf(
    Color(0xFFFFB3BA), Color(0xFFFFDFBA), Color(0xFFFFFFBA), Color(0xFFBAFFC9),
    Color(0xFFBAE1FF), Color(0xFFE6B3FF), Color(0xFFFFC6FF), Color(0xFFC4FAF8),
    Color(0xFFA0E8AF), Color(0xFFFFD1DC), Color(0xFFFDFD96), Color(0xFFE2F0CB),
    Color(0xFFB5EAD7), Color(0xFFC7CEEA), Color(0xFFF4C2C2), Color(0xFFFDECDA),
)

// 明色を黒とブレンドして暗色を生成（色相を残しつつダークテーマに馴染ませる）
// factor: 黒へ寄せる度合い（0.0=元色、1.0=完全な黒）
fun darkVariant(color: Color, factor: Float = 0.65f): Color =
    lerp(color, Color.Black, factor)

// プレビュー用：現在のテーマでパステル色がどう表示されるかを返す
fun previewColor(base: Color, isDarkTheme: Boolean): Color =
    if (isDarkTheme) darkVariant(base) else base