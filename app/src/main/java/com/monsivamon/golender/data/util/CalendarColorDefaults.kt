package com.monsivamon.golender.data.util

// カレンダー識別色の既定パレット。
// - ライトモード: 濃色 16 色（Material 700 番台）
// - ダークモード: 明色 16 色（Material 300 番台）
// - colorArgb が未設定(0) のとき、index に基づいて自動割り当てする
object CalendarColorDefaults {

    // ライトモード向けの濃色 16 色
    val LIGHT_ARGB: IntArray = intArrayOf(
        0xFFD32F2F.toInt(), 0xFFC2185B.toInt(), 0xFF7B1FA2.toInt(), 0xFF512DA8.toInt(),
        0xFF303F9F.toInt(), 0xFF1976D2.toInt(), 0xFF0288D1.toInt(), 0xFF0097A7.toInt(),
        0xFF00796B.toInt(), 0xFF388E3C.toInt(), 0xFF689F38.toInt(), 0xFFAFB42B.toInt(),
        0xFFF9A825.toInt(), 0xFFFF8F00.toInt(), 0xFFEF6C00.toInt(), 0xFFE64A19.toInt(),
    )

    // ダークモード向けの明色 16 色
    val DARK_ARGB: IntArray = intArrayOf(
        0xFFE57373.toInt(), 0xFFF06292.toInt(), 0xFFBA68C8.toInt(), 0xFF9575CD.toInt(),
        0xFF7986CB.toInt(), 0xFF64B5F6.toInt(), 0xFF4FC3F7.toInt(), 0xFF4DD0E1.toInt(),
        0xFF4DB6AC.toInt(), 0xFF81C784.toInt(), 0xFFAED581.toInt(), 0xFFDCE775.toInt(),
        0xFFFFF176.toInt(), 0xFFFFD54F.toInt(), 0xFFFFB74D.toInt(), 0xFFFF8A65.toInt(),
    )

    // index に基づいてパレットから色を返す（負の index も安全に処理する）
    fun forIndex(index: Int, isDark: Boolean): Int {
        val palette = if (isDark) DARK_ARGB else LIGHT_ARGB
        val n = palette.size
        val i = ((index % n) + n) % n
        return palette[i]
    }

    // colorArgb が 0 のときだけ forIndex で補正し、非ゼロの ARGB を返す
    fun resolve(colorArgb: Int, index: Int, isDark: Boolean): Int =
        if (colorArgb != 0) colorArgb else forIndex(index, isDark)
}