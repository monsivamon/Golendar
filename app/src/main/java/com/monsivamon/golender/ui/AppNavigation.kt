package com.monsivamon.golender.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.monsivamon.golender.viewmodel.CalendarViewModel

// 画面遷移用のルート定義
object Routes {
    const val DAILY = "daily"
    const val WEEKLY = "weekly"
    const val MONTHLY = "monthly"
    const val SETTINGS = "settings"
}

// タブの順序（日→週→月の並び）
val tabOrder = listOf(Routes.DAILY, Routes.WEEKLY, Routes.MONTHLY)

// 左右矢印操作によるタブ切り替え
fun navigateTab(navController: NavController, currentRoute: String, direction: Int) {
    val currentIndex = tabOrder.indexOf(currentRoute)
    if (currentIndex == -1) return
    val newIndex = (currentIndex + direction).mod(tabOrder.size)
    navController.navigate(tabOrder[newIndex]) { launchSingleTop = true }
}

// スライドアニメーションの方向を決定
fun getSlideDirection(initialRoute: String?, targetRoute: String?): Int {
    val initialIndex = tabOrder.indexOf(initialRoute)
    val targetIndex = tabOrder.indexOf(targetRoute)
    if (initialIndex == -1 || targetIndex == -1) return 1
    return if (targetIndex > initialIndex) 1 else -1
}

// メインナビゲーション（日・週・月・設定の4画面）
@Composable
fun AppNavigation(viewModel: CalendarViewModel) {
    val navController = rememberNavController()
    val animSpec = tween<IntOffset>(durationMillis = 220, easing = FastOutSlowInEasing)

    val themeMode by viewModel.themeMode.collectAsState()
    val customBg by viewModel.calendarBgColor.collectAsState()
    val colors = getAppColors(themeMode, customBg)

    NavHost(
        navController = navController,
        startDestination = Routes.MONTHLY,
        modifier = Modifier.background(colors.bg),
        enterTransition = {
            slideInHorizontally(animationSpec = animSpec) { fullWidth -> fullWidth * getSlideDirection(initialState.destination.route, targetState.destination.route) }
        },
        exitTransition = {
            slideOutHorizontally(animationSpec = animSpec) { fullWidth -> -fullWidth * getSlideDirection(initialState.destination.route, targetState.destination.route) }
        },
        popEnterTransition = {
            if (initialState.destination.route == Routes.SETTINGS) slideInHorizontally(animationSpec = animSpec) { fullWidth -> -fullWidth }
            else slideInHorizontally(animationSpec = animSpec) { fullWidth -> fullWidth * getSlideDirection(targetState.destination.route, initialState.destination.route) }
        },
        popExitTransition = {
            if (initialState.destination.route == Routes.SETTINGS) slideOutHorizontally(animationSpec = animSpec) { fullWidth -> fullWidth }
            else slideOutHorizontally(animationSpec = animSpec) { fullWidth -> -fullWidth * getSlideDirection(targetState.destination.route, initialState.destination.route) }
        }
    ) {
        composable(Routes.MONTHLY) { MonthlyCalendarScreen(viewModel, navController) }
        composable(Routes.DAILY) { DailyCalendarScreen(viewModel, navController) }
        composable(Routes.WEEKLY) { WeeklyCalendarScreen(viewModel, navController) }
        composable(Routes.SETTINGS) { SettingsScreen(viewModel, navController) }
    }
}