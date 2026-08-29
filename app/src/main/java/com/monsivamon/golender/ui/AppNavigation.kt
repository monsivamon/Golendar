package com.monsivamon.golender.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntOffset
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.monsivamon.golender.viewmodel.CalendarViewModel
import com.monsivamon.golender.viewmodel.ThemeMode

object Routes {
    const val DAILY = "daily"
    const val WEEKLY = "weekly"
    const val MONTHLY = "monthly"
    const val SETTINGS = "settings"
}

val tabOrder = listOf(Routes.DAILY, Routes.WEEKLY, Routes.MONTHLY)

fun navigateTab(navController: NavController, currentRoute: String, direction: Int) {
    val currentIndex = tabOrder.indexOf(currentRoute)
    if (currentIndex == -1) return
    val newIndex = (currentIndex + direction).mod(tabOrder.size)
    navController.navigate(tabOrder[newIndex]) { launchSingleTop = true }
}

fun getSlideDirection(initialRoute: String?, targetRoute: String?): Int {
    val initialIndex = tabOrder.indexOf(initialRoute)
    val targetIndex = tabOrder.indexOf(targetRoute)
    if (initialIndex == -1 || targetIndex == -1) return 1
    return if (targetIndex > initialIndex) 1 else -1
}

data class AppColors(
    val bg: Color,
    val surface: Color,
    val primaryAccent: Color,
    val text: Color,
    val textGray: Color,
    val divider: Color,
    val sunRed: Color,
    val satBlue: Color
)

@Composable
fun getAppColors(themeMode: ThemeMode, customBg: Color = Color.Unspecified): AppColors {
    val isDark = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val defaultBg = if (isDark) Color(0xFF141419) else Color(0xFFF0F2F5)
    val finalBg = if (customBg != Color.Unspecified) customBg else defaultBg

    return if (isDark) {
        AppColors(bg = finalBg, surface = Color(0xFF25252D), primaryAccent = Color(0xFF4B59D6), text = Color(0xFFF3F3F3), textGray = Color(0xFFAAAAAA), divider = Color(0xFF333333), sunRed = Color(0xFFE55A5A), satBlue = Color(0xFF5A8CE5))
    } else {
        AppColors(bg = finalBg, surface = Color(0xFFFFFFFF), primaryAccent = Color(0xFF4B59D6), text = Color(0xFF1A1A1A), textGray = Color(0xFF666666), divider = Color(0xFFE0E0E0), sunRed = Color(0xFFD32F2F), satBlue = Color(0xFF1976D2))
    }
}

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