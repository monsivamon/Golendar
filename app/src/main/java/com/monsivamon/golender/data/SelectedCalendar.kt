package com.monsivamon.golender.data

// 表示対象として選択された Google カレンダー情報を保持するデータクラス
data class SelectedCalendar(
    val calendarId: Long,
    val accountName: String,
    val displayName: String,
    val colorArgb: Int,        // 0 = カレンダー標準色に追従
    val isVisible: Boolean = true,
) {
    // 名前が長い場合に末尾を省略した表示用文字列を返す
    fun trimmedName(maxLength: Int = 8): String {
        return if (displayName.length > maxLength) {
            displayName.take(maxLength) + "…"
        } else {
            displayName
        }
    }
}