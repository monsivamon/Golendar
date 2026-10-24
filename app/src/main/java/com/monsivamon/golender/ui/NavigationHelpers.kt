package com.monsivamon.golender.ui

import androidx.navigation.NavController
import com.monsivamon.golender.viewmodel.CalendarViewModel

// 画面遷移で使うルート文字列を定義する
object Routes {
    const val DAILY = "daily"
    const val WEEKLY = "weekly"
    const val MONTHLY = "monthly"
    const val ALBUM = "album"
    const val SETTINGS = "settings"
}

// 基本のタブ順（日→週→月）を保持する
val tabOrder = listOf(Routes.DAILY, Routes.WEEKLY, Routes.MONTHLY)

// アルバムタブ表示時のみ、月の後ろにアルバムを追加したタブ順を返す
fun tabOrderFor(showAlbum: Boolean): List<String> =
    if (showAlbum) tabOrder + Routes.ALBUM else tabOrder

// 状態保存・復元付きで指定タブへ遷移する
fun navigateToTab(navController: NavController, route: String) {
    navController.navigate(route) {
        popUpTo(Routes.MONTHLY) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

// 現在のタブから前後方向のタブへ循環移動する
fun navigateTab(
    navController: NavController,
    currentRoute: String,
    direction: Int,
    tabs: List<String> = tabOrder,
) {
    val currentIndex = tabs.indexOf(currentRoute)
    if (currentIndex == -1) return
    val newIndex = (currentIndex + direction).mod(tabs.size)
    navigateToTab(navController, tabs[newIndex])
}

// タブ順序に基づきスライドアニメーションの方向を返す
fun getSlideDirection(
    initialRoute: String?,
    targetRoute: String?,
    tabs: List<String> = tabOrder,
): Int {
    val initialIndex = tabs.indexOf(initialRoute)
    val targetIndex = tabs.indexOf(targetRoute)
    if (initialIndex == -1 || targetIndex == -1) return 1
    return if (targetIndex > initialIndex) 1 else -1
}

// 選択日を今日にリセットし月表示へ遷移する
fun navigateToTodayMonth(viewModel: CalendarViewModel, navController: NavController) {
    viewModel.resetToToday()
    navigateToTab(navController, Routes.MONTHLY)
}
