package com.monsivamon.golender.ui

import android.graphics.drawable.ColorDrawable
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
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
import com.monsivamon.golender.ui.dialogs.ExitConfirmDialog
import com.monsivamon.golender.ui.dialogs.SyncConfirmDialog
import com.monsivamon.golender.ui.theme.getAppColors
import com.monsivamon.golender.viewmodel.CalendarViewModel
import java.time.LocalDate

// 画面遷移用のルート定義
object Routes {
    const val DAILY = "daily"
    const val WEEKLY = "weekly"
    const val MONTHLY = "monthly"
    const val SETTINGS = "settings"
}

// タブの順序（日→週→月の並び）
val tabOrder = listOf(Routes.DAILY, Routes.WEEKLY, Routes.MONTHLY)

// タブ移動の標準パターン（状態保存・復元付き）
fun navigateToTab(navController: NavController, route: String) {
    navController.navigate(route) {
        popUpTo(Routes.MONTHLY) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

// 左右矢印操作によるタブ切り替え（循環あり）
fun navigateTab(navController: NavController, currentRoute: String, direction: Int) {
    val currentIndex = tabOrder.indexOf(currentRoute)
    if (currentIndex == -1) return
    val newIndex = (currentIndex + direction).mod(tabOrder.size)
    navigateToTab(navController, tabOrder[newIndex])
}

// スライドアニメーションの方向を決定（タブ順序に基づく）
fun getSlideDirection(initialRoute: String?, targetRoute: String?): Int {
    val initialIndex = tabOrder.indexOf(initialRoute)
    val targetIndex = tabOrder.indexOf(targetRoute)
    if (initialIndex == -1 || targetIndex == -1) return 1
    return if (targetIndex > initialIndex) 1 else -1
}

// 今日ボタン押下時：選択日を今日にリセットし月表示へ遷移
fun navigateToTodayMonth(viewModel: CalendarViewModel, navController: NavController) {
    viewModel.resetToToday()
    navController.navigate(Routes.MONTHLY) {
        popUpTo(Routes.MONTHLY) { inclusive = true }
    }
}

// ステータスバー/ナビゲーションバーのアイコン色を背景輝度に応じて切り替える
@Composable
private fun SystemBarsAppearanceSync(bgColor: Color) {
    val view = LocalView.current
    val context = LocalContext.current

    if (!view.isInEditMode) {
        LaunchedEffect(bgColor) {
            val activity = context.findActivity() ?: return@LaunchedEffect
            val window = activity.window ?: return@LaunchedEffect

            window.setBackgroundDrawable(ColorDrawable(bgColor.toArgb()))

            // 明るい背景ならステータスバーアイコンを黒く
            val isLightBg = bgColor.luminance() > 0.5f

            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = isLightBg
            controller.isAppearanceLightNavigationBars = isLightBg
        }
    }
}

// メインナビゲーション（ヘッダー・タブ行・NavHost・各種ダイアログを統括）
@Composable
fun AppNavigation(viewModel: CalendarViewModel) {
    val navController = rememberNavController()
    val animSpec = tween<IntOffset>(durationMillis = 220, easing = FastOutSlowInEasing)

    // 初回のみ：ウィジェットタップで渡されたルートを startDestination として使用
    val startDestination: String = remember {
        val initial = viewModel.pendingRoute.value
        if (initial != null) viewModel.consumeNavigation()
        when {
            initial == Routes.SETTINGS -> Routes.SETTINGS
            initial != null && initial in tabOrder -> initial
            else -> Routes.MONTHLY
        }
    }

    val themeMode by viewModel.themeMode.collectAsState()
    val customBg by viewModel.calendarBgColor.collectAsState()
    val colors = getAppColors(themeMode, customBg)

    val currentMonth by viewModel.currentMonth.collectAsState()
    val selectedDate by viewModel.selectedDate.collectAsState()
    val weekStartDay by viewModel.weekStartDay.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // 設定画面ではカスタム背景を適用しない
    val settingsColors = getAppColors(themeMode, Color.Unspecified)
    val effectiveBg = if (currentRoute == Routes.SETTINGS) settingsColors.bg else colors.bg

    SystemBarsAppearanceSync(effectiveBg)

    var isSearchMode by remember { mutableStateOf(false) }
    // 「戻る」操作で MONTHLY へ遷移する際に戻る風アニメーションを適用するためのフラグ
    var backNavFlag by remember { mutableStateOf(false) }

    var showSyncDialog by remember { mutableStateOf(false) }
    var showExitDialog by remember { mutableStateOf(false) }
    var showMonthPickerDialog by remember { mutableStateOf(false) }
    var showDatePickerDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }

    // 画面切替時に検索モードとbackNavFlagをリセット
    LaunchedEffect(currentRoute) {
        if (currentRoute == null) return@LaunchedEffect
        isSearchMode = false
        backNavFlag = false
        viewModel.updateSearchQuery("")
    }

    // アプリ起動中のウィジェットタップによる遷移要求を監視
    val pendingRoute by viewModel.pendingRoute.collectAsState()
    LaunchedEffect(pendingRoute) {
        val route = pendingRoute ?: return@LaunchedEffect
        viewModel.consumeNavigation()
        when {
            // 設定画面への遷移要求
            route == Routes.SETTINGS -> {
                if (currentRoute != Routes.SETTINGS) {
                    navController.navigate(Routes.SETTINGS)
                }
            }
            // タブ画面への遷移要求
            route in tabOrder -> {
                if (route != currentRoute) {
                    navigateToTab(navController, route)
                }
            }
        }
    }

    // 戻るボタンの一元処理（検索閉じ→月表示なら終了確認→それ以外は月表示へ）
    BackHandler {
        when {
            isSearchMode -> {
                isSearchMode = false
                viewModel.updateSearchQuery("")
            }
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

    // 画面ごとのヘッダータイトルを決定
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
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {

            // 設定画面以外は共通ヘッダーとタブ行を表示
            if (currentRoute != null && currentRoute != Routes.SETTINGS) {
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
                    onSettingsClick = { navController.navigate(Routes.SETTINGS) },
                )

                CalendarTabRow(currentRoute, colors, navController)
            }

            NavHost(
                navController = navController,
                startDestination = startDestination,
                modifier = Modifier.weight(1f),
                // backNavFlag時は「戻る」風、それ以外はタブ順序に応じたスライド
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
                // 設定画面からの復帰は専用アニメーション
                popEnterTransition = {
                    if (initialState.destination.route == Routes.SETTINGS)
                        slideInHorizontally(animationSpec = animSpec) { fullWidth -> -fullWidth }
                    else
                        slideInHorizontally(animationSpec = animSpec) { fullWidth ->
                            fullWidth * getSlideDirection(targetState.destination.route, initialState.destination.route)
                        }
                },
                popExitTransition = {
                    if (initialState.destination.route == Routes.SETTINGS)
                        slideOutHorizontally(animationSpec = animSpec) { fullWidth -> fullWidth }
                    else
                        slideOutHorizontally(animationSpec = animSpec) { fullWidth ->
                            -fullWidth * getSlideDirection(targetState.destination.route, initialState.destination.route)
                        }
                }
            ) {
                composable(Routes.MONTHLY) { MonthlyCalendarScreen(viewModel, navController, isSearchMode) }
                composable(Routes.DAILY) { DailyCalendarScreen(viewModel, navController, isSearchMode) }
                composable(Routes.WEEKLY) { WeeklyCalendarScreen(viewModel, navController, isSearchMode) }
                composable(Routes.SETTINGS) { SettingsScreen(viewModel, navController) }
            }
        }
    }

    // 同期確認ダイアログ
    if (showSyncDialog) {
        SyncConfirmDialog(
            colors = colors,
            onDismiss = { showSyncDialog = false },
            onConfirm = { viewModel.loadEvents() },
        )
    }

    // アプリ終了確認ダイアログ
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

    // 年月選択ダイアログ（月表示用）
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

    // 日付選択ダイアログ（日・週表示用）
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