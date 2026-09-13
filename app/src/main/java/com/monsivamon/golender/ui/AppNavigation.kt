package com.monsivamon.golender.ui

import android.graphics.drawable.ColorDrawable
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntOffset
import androidx.core.view.WindowCompat
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.monsivamon.golender.ui.common.CalendarHeader
import com.monsivamon.golender.ui.common.CalendarTabRow
import com.monsivamon.golender.ui.common.GolendarDatePickerDialog
import com.monsivamon.golender.ui.common.YearMonthPickerDialog
import com.monsivamon.golender.ui.common.findActivity
import com.monsivamon.golender.ui.common.swipeToNavigateHorizontal
import com.monsivamon.golender.ui.dialogs.ExitConfirmDialog
import com.monsivamon.golender.ui.dialogs.SyncConfirmDialog
import com.monsivamon.golender.ui.theme.getAppColors
import com.monsivamon.golender.viewmodel.CalendarViewModel
import java.time.LocalDate

// 画面遷移で使うルート文字列を定義する。
object Routes {
    const val DAILY = "daily"
    const val WEEKLY = "weekly"
    const val MONTHLY = "monthly"
    const val SETTINGS = "settings"
}

// タブの並び順（日→週→月）を保持する。
val tabOrder = listOf(Routes.DAILY, Routes.WEEKLY, Routes.MONTHLY)

// 状態保存・復元付きで指定タブへ遷移する。
fun navigateToTab(navController: NavController, route: String) {
    navController.navigate(route) {
        popUpTo(Routes.MONTHLY) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

// 現在のタブから前後方向のタブへ循環移動する。
fun navigateTab(navController: NavController, currentRoute: String, direction: Int) {
    val currentIndex = tabOrder.indexOf(currentRoute)
    if (currentIndex == -1) return
    val newIndex = (currentIndex + direction).mod(tabOrder.size)
    navigateToTab(navController, tabOrder[newIndex])
}

// タブ順序に基づきスライドアニメーションの方向を返す。
fun getSlideDirection(initialRoute: String?, targetRoute: String?): Int {
    val initialIndex = tabOrder.indexOf(initialRoute)
    val targetIndex = tabOrder.indexOf(targetRoute)
    if (initialIndex == -1 || targetIndex == -1) return 1
    return if (targetIndex > initialIndex) 1 else -1
}

// 選択日を今日にリセットし月表示へ遷移する。
fun navigateToTodayMonth(viewModel: CalendarViewModel, navController: NavController) {
    viewModel.resetToToday()
    navController.navigate(Routes.MONTHLY) {
        popUpTo(Routes.MONTHLY) { inclusive = true }
    }
}

// 背景輝度に応じてステータスバー・ナビゲーションバーのアイコン色を切り替える。
@Composable
private fun SystemBarsAppearanceSync(bgColor: Color) {
    val view = LocalView.current
    val context = LocalContext.current

    if (!view.isInEditMode) {
        LaunchedEffect(bgColor) {
            val activity = context.findActivity() ?: return@LaunchedEffect
            val window = activity.window ?: return@LaunchedEffect

            window.setBackgroundDrawable(ColorDrawable(bgColor.toArgb()))

            val isLightBg = bgColor.luminance() > 0.5f

            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = isLightBg
            controller.isAppearanceLightNavigationBars = isLightBg
        }
    }
}

// ヘッダー・タブ行・NavHost・設定オーバーレイ・各種ダイアログを統括するメイン画面。
@Composable
fun AppNavigation(viewModel: CalendarViewModel) {
    val navController = rememberNavController()
    val animSpec = tween<IntOffset>(durationMillis = 220, easing = FastOutSlowInEasing)

    val initialRoute: String? = remember {
        val initial = viewModel.pendingRoute.value
        if (initial != null) viewModel.consumeNavigation()
        initial
    }
    val startDestination: String = remember {
        when {
            initialRoute != null && initialRoute in tabOrder -> initialRoute
            else -> Routes.MONTHLY
        }
    }

    var isSettingsOpen by remember { mutableStateOf(initialRoute == Routes.SETTINGS) }

    val themeMode by viewModel.themeMode.collectAsState()
    val customBg by viewModel.calendarBgColor.collectAsState()
    val colors = getAppColors(themeMode, customBg)

    val currentMonth by viewModel.currentMonth.collectAsState()
    val selectedDate by viewModel.selectedDate.collectAsState()
    val weekStartDay by viewModel.weekStartDay.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val effectiveBg = colors.bg

    SystemBarsAppearanceSync(effectiveBg)

    var isSearchMode by remember { mutableStateOf(false) }
    var backNavFlag by remember { mutableStateOf(false) }

    var showSyncDialog by remember { mutableStateOf(false) }
    var showExitDialog by remember { mutableStateOf(false) }
    var showMonthPickerDialog by remember { mutableStateOf(false) }
    var showDatePickerDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }

    LaunchedEffect(currentRoute) {
        if (currentRoute == null) return@LaunchedEffect
        isSearchMode = false
        backNavFlag = false
        viewModel.updateSearchQuery("")
    }

    val pendingRoute by viewModel.pendingRoute.collectAsState()
    LaunchedEffect(pendingRoute) {
        val route = pendingRoute ?: return@LaunchedEffect
        viewModel.consumeNavigation()
        when {
            route == Routes.SETTINGS -> isSettingsOpen = true
            route in tabOrder -> {
                if (route != currentRoute) {
                    navigateToTab(navController, route)
                }
            }
        }
    }

    BackHandler {
        when {
            isSearchMode -> {
                isSearchMode = false
                viewModel.updateSearchQuery("")
            }
            isSettingsOpen -> isSettingsOpen = false
            currentRoute == Routes.MONTHLY -> showExitDialog = true
            else -> {
                val popped = navController.popBackStack(Routes.MONTHLY, inclusive = false)
                if (!popped) {
                    backNavFlag = true
                    navController.navigate(Routes.MONTHLY) {
                        popUpTo(navController.graph.startDestinationId) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            }
        }
    }

    val headerTitle = when (currentRoute) {
        Routes.MONTHLY -> "${currentMonth.year}年 ${currentMonth.monthValue}月"
        Routes.DAILY -> "${selectedDate.year}年${selectedDate.monthValue}月${selectedDate.dayOfMonth}日"
        Routes.WEEKLY -> {
            val offset = (selectedDate.dayOfWeek.value - weekStartDay.value + 7) % 7
            val sow = selectedDate.minusDays(offset.toLong())
            val eow = sow.plusDays(6)
            "${sow.year}/${sow.monthValue}/${sow.dayOfMonth}~${eow.monthValue}/${eow.dayOfMonth}"
        }
        else -> ""
    }

    Surface(modifier = Modifier.fillMaxSize(), color = effectiveBg) {
        Box(modifier = Modifier.fillMaxSize()) {

            Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {

                CalendarHeader(
                    title = headerTitle,
                    colors = colors,
                    isSearchMode = isSearchMode,
                    searchQuery = searchQuery,
                    onTitleClick = {
                        when (currentRoute) {
                            Routes.MONTHLY -> showMonthPickerDialog = true
                            else -> showDatePickerDialog = true
                        }
                    },
                    onTodayClick = { navigateToTodayMonth(viewModel, navController) },
                    onSearchStart = { isSearchMode = true },
                    onSearchClose = { isSearchMode = false; viewModel.updateSearchQuery("") },
                    onSearchQueryChange = { viewModel.updateSearchQuery(it) },
                    onSyncClick = { showSyncDialog = true },
                    onSettingsClick = { isSettingsOpen = true },
                )

                CalendarTabRow(currentRoute ?: Routes.MONTHLY, colors, navController)

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .swipeToNavigateHorizontal(
                            onSwipeLeft = {
                                currentRoute?.let { route ->
                                    if (route in tabOrder) navigateTab(navController, route, 1)
                                }
                            },
                            onSwipeRight = {
                                currentRoute?.let { route ->
                                    if (route in tabOrder) navigateTab(navController, route, -1)
                                }
                            },
                        )
                ) {
                    NavHost(
                        navController = navController,
                        startDestination = startDestination,
                        modifier = Modifier.fillMaxSize(),
                        enterTransition = {
                            if (backNavFlag) {
                                slideInHorizontally(animationSpec = animSpec) { fullWidth -> -fullWidth }
                            } else {
                                slideInHorizontally(animationSpec = animSpec) { fullWidth ->
                                    fullWidth * getSlideDirection(initialState.destination.route, targetState.destination.route)
                                }
                            }
                        },
                        exitTransition = {
                            if (backNavFlag) {
                                slideOutHorizontally(animationSpec = animSpec) { fullWidth -> fullWidth }
                            } else {
                                slideOutHorizontally(animationSpec = animSpec) { fullWidth ->
                                    -fullWidth * getSlideDirection(initialState.destination.route, targetState.destination.route)
                                }
                            }
                        },
                        popEnterTransition = {
                            slideInHorizontally(animationSpec = animSpec) { fullWidth ->
                                fullWidth * getSlideDirection(initialState.destination.route, targetState.destination.route)
                            }
                        },
                        popExitTransition = {
                            slideOutHorizontally(animationSpec = animSpec) { fullWidth ->
                                -fullWidth * getSlideDirection(initialState.destination.route, targetState.destination.route)
                            }
                        }
                    ) {
                        composable(Routes.MONTHLY) { MonthlyCalendarScreen(viewModel, navController, isSearchMode) }
                        composable(Routes.DAILY) { DailyCalendarScreen(viewModel, navController, isSearchMode) }
                        composable(Routes.WEEKLY) { WeeklyCalendarScreen(viewModel, navController, isSearchMode) }
                    }
                }
            }

            AnimatedVisibility(
                visible = isSettingsOpen,
                enter = slideInHorizontally(animationSpec = animSpec) { fullWidth -> fullWidth },
                exit = slideOutHorizontally(animationSpec = animSpec) { fullWidth -> fullWidth },
            ) {
                SettingsScreen(
                    viewModel = viewModel,
                    onBack = { isSettingsOpen = false },
                )
            }
        }
    }

    if (showSyncDialog) {
        SyncConfirmDialog(
            colors = colors,
            onDismiss = { showSyncDialog = false },
            onConfirm = { viewModel.loadEvents() },
        )
    }

    if (showExitDialog) {
        ExitConfirmDialog(
            colors = colors,
            onDismiss = { showExitDialog = false },
            onConfirm = {
                showExitDialog = false
                activity?.finish()
            },
        )
    }

    if (showMonthPickerDialog) {
        YearMonthPickerDialog(
            currentYear = currentMonth.year,
            currentMonth = currentMonth.monthValue,
            colors = colors,
            onDismiss = { showMonthPickerDialog = false },
            onDateSelected = { year, month ->
                viewModel.selectDate(LocalDate.of(year, month, 1))
                showMonthPickerDialog = false
            },
        )
    }

    if (showDatePickerDialog) {
        GolendarDatePickerDialog(
            initialDate = selectedDate,
            colors = colors,
            onDismiss = { showDatePickerDialog = false },
            onDateSelected = {
                viewModel.selectDate(it)
                showDatePickerDialog = false
            },
        )
    }
}