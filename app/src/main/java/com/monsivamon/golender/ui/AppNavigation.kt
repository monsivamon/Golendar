package com.monsivamon.golender.ui

import android.graphics.drawable.ColorDrawable
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.LocalIndication
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
import com.monsivamon.golender.ui.dialogs.CalendarPermissionDialog
import com.monsivamon.golender.ui.dialogs.CalendarSelectionDialog
import com.monsivamon.golender.ui.dialogs.ExitConfirmDialog
import com.monsivamon.golender.ui.dialogs.GestureSetupDialog
import com.monsivamon.golender.ui.dialogs.NotificationSetupDialog
import com.monsivamon.golender.ui.dialogs.SyncConfirmDialog
import com.monsivamon.golender.ui.theme.PressScaleIndication
import com.monsivamon.golender.ui.theme.getAppColors
import com.monsivamon.golender.viewmodel.CalendarViewModel
import java.time.LocalDate

// 背景輝度に応じてステータスバー・ナビゲーションバーのアイコン色を切り替える
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

// アプリ全体に押下時の縮小フィードバックを適用して中身を起動する
@Composable
fun AppNavigation(viewModel: CalendarViewModel) {
    CompositionLocalProvider(LocalIndication provides PressScaleIndication) {
        AppNavigationContent(viewModel)
    }
}

// ヘッダー・タブ行・NavHost・設定オーバーレイ・各種ダイアログを統括するメイン画面
@Composable
private fun AppNavigationContent(viewModel: CalendarViewModel) {
    val navController = rememberNavController()
    val animSpec = tween<IntOffset>(durationMillis = 220, easing = FastOutSlowInEasing)
    // 起動時に渡された初期ルートとショートカットを消費する
    val initialRoute: String? = remember { val initial = viewModel.pendingRoute.value; if (initial != null) viewModel.consumeNavigation(); initial }
    val initialShortcut: String? = remember { viewModel.pendingShortcut.value }
    val startDestination: String = Routes.MONTHLY
    var isSettingsOpen by remember { mutableStateOf(initialRoute == Routes.SETTINGS) }
    // ViewModel から各種状態を購読する
    val themeMode by viewModel.themeMode.collectAsState()
    val customBg by viewModel.calendarBgColor.collectAsState()
    val colors = getAppColors(themeMode, customBg)
    val currentMonth by viewModel.currentMonth.collectAsState()
    val selectedDate by viewModel.selectedDate.collectAsState()
    val weekStartDay by viewModel.weekStartDay.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val showBottomList by viewModel.showBottomList.collectAsState()
    val showNotificationSetup by viewModel.showNotificationSetup.collectAsState()
    val showCalendarSetup by viewModel.showCalendarSetup.collectAsState()
    val showCalendarSelection by viewModel.showCalendarSelection.collectAsState()
    val showGestureSetup by viewModel.showGestureSetup.collectAsState()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val effectiveBg = colors.bg
    SystemBarsAppearanceSync(effectiveBg)
    // 画面内 UI 状態を管理するフラグ群
    var isSearchMode by remember { mutableStateOf(false) }
    var backNavFlag by remember { mutableStateOf(false) }
    var pendingSearchAfterNav by remember { mutableStateOf(false) }
    var showSyncDialog by remember { mutableStateOf(false) }
    var showExitDialog by remember { mutableStateOf(false) }
    var showMonthPickerDialog by remember { mutableStateOf(false) }
    var showDatePickerDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    // 検索結果を選択したときの共通処理
    val onSearchResultSelected: (LocalDate) -> Unit = { date -> isSearchMode = false; viewModel.updateSearchQuery(""); viewModel.exitSearchMode(); viewModel.selectDate(date) }

    // ステータスメッセージ（モード自動切替・バックアップ結果など）をトーストで通知する
    val statusMessage by viewModel.statusMessage.collectAsState()
    LaunchedEffect(statusMessage) {
        statusMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.clearStatusMessage()
        }
    }

    // 起動時の初期ルート／ショートカットに応じてタブを移動する
    LaunchedEffect(Unit) {
        val target = when {
            initialRoute != null && initialRoute in tabOrder && initialRoute != Routes.MONTHLY -> initialRoute
            initialShortcut == "today" -> Routes.DAILY
            else -> null
        }
        if (target != null) navigateToTab(navController, target)
    }
    // タブ切替時の検索モード解除・検索キャッシュのクリア
    LaunchedEffect(currentRoute) {
        if (currentRoute == null) return@LaunchedEffect
        if (pendingSearchAfterNav && currentRoute == Routes.MONTHLY) { isSearchMode = true; viewModel.enterSearchMode(); pendingSearchAfterNav = false } else { isSearchMode = false }
        backNavFlag = false; viewModel.updateSearchQuery("")
    }
    // 外部からの画面遷移要求を監視する
    val pendingRoute by viewModel.pendingRoute.collectAsState()
    LaunchedEffect(pendingRoute) {
        val route = pendingRoute ?: return@LaunchedEffect
        viewModel.consumeNavigation()
        when {
            route == Routes.SETTINGS -> isSettingsOpen = true
            route in tabOrder -> { if (route != currentRoute) navigateToTab(navController, route) }
        }
    }
    // ショートカットからのアクション要求を監視する
    val pendingShortcut by viewModel.pendingShortcut.collectAsState()
    LaunchedEffect(pendingShortcut) {
        val action = pendingShortcut ?: return@LaunchedEffect
        viewModel.consumeShortcut()
        when (action) {
            "today" -> { viewModel.resetToToday(); if (currentRoute != null && currentRoute != Routes.DAILY) navigateToTab(navController, Routes.DAILY) }
            "search" -> { if (currentRoute == Routes.MONTHLY) { isSearchMode = true; viewModel.enterSearchMode() } else { pendingSearchAfterNav = true; if (currentRoute != null) navigateToTab(navController, Routes.MONTHLY) } }
            "add_event" -> { if (currentRoute != null && currentRoute != Routes.MONTHLY) navigateToTab(navController, Routes.MONTHLY); viewModel.requestAddEvent() }
        }
    }
    // 戻るボタンの遷移優先順位を制御する
    BackHandler {
        when {
            isSearchMode -> { isSearchMode = false; viewModel.updateSearchQuery(""); viewModel.exitSearchMode() }
            isSettingsOpen -> isSettingsOpen = false
            currentRoute == Routes.MONTHLY -> showExitDialog = true
            else -> { backNavFlag = true; navigateToTab(navController, Routes.MONTHLY) }
        }
    }
    // ヘッダーに表示するタイトル文字列を決定する
    val headerTitle = when (currentRoute) {
        Routes.MONTHLY -> "${currentMonth.year}年 ${currentMonth.monthValue}月"
        Routes.DAILY -> "${selectedDate.year}年${selectedDate.monthValue}月${selectedDate.dayOfMonth}日"
        Routes.WEEKLY -> { val offset = (selectedDate.dayOfWeek.value - weekStartDay.value + 7) % 7; val sow = selectedDate.minusDays(offset.toLong()); val eow = sow.plusDays(6); "${sow.year}/${sow.monthValue}/${sow.dayOfMonth}~${eow.monthValue}/${eow.dayOfMonth}" }
        else -> ""
    }
    Surface(modifier = Modifier.fillMaxSize(), color = effectiveBg) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
                // ヘッダー（タイトル・検索・同期・設定）
                CalendarHeader(title = headerTitle, colors = colors, isSearchMode = isSearchMode, searchQuery = searchQuery, showBottomListToggle = currentRoute == Routes.MONTHLY, showBottomList = showBottomList,
                    onTitleClick = { when (currentRoute) { Routes.MONTHLY -> showMonthPickerDialog = true; else -> showDatePickerDialog = true } },
                    onTodayClick = { navigateToTodayMonth(viewModel, navController) }, onToggleBottomList = { viewModel.setShowBottomList(!showBottomList) },
                    onSearchStart = { isSearchMode = true; viewModel.enterSearchMode() }, onSearchClose = { isSearchMode = false; viewModel.updateSearchQuery(""); viewModel.exitSearchMode() },
                    onSearchQueryChange = { viewModel.updateSearchQuery(it) }, onSyncClick = { showSyncDialog = true }, onSettingsClick = { isSettingsOpen = true })
                // 日・週・月のタブ行
                CalendarTabRow(currentRoute ?: Routes.MONTHLY, colors, navController)
                // 本体の画面（左右スワイプでタブ切替）
                Box(modifier = Modifier.weight(1f).swipeToNavigateHorizontal(
                    onSwipeLeft = { currentRoute?.let { route -> if (route in tabOrder) navigateTab(navController, route, 1) } },
                    onSwipeRight = { currentRoute?.let { route -> if (route in tabOrder) navigateTab(navController, route, -1) } })) {
                    NavHost(navController = navController, startDestination = startDestination, modifier = Modifier.fillMaxSize(),
                        enterTransition = { if (backNavFlag) slideInHorizontally(animationSpec = animSpec) { fullWidth -> -fullWidth } else slideInHorizontally(animationSpec = animSpec) { fullWidth -> fullWidth * getSlideDirection(initialState.destination.route, targetState.destination.route) } },
                        exitTransition = { if (backNavFlag) slideOutHorizontally(animationSpec = animSpec) { fullWidth -> fullWidth } else slideOutHorizontally(animationSpec = animSpec) { fullWidth -> -fullWidth * getSlideDirection(initialState.destination.route, targetState.destination.route) } },
                        popEnterTransition = { slideInHorizontally(animationSpec = animSpec) { fullWidth -> fullWidth * getSlideDirection(initialState.destination.route, targetState.destination.route) } },
                        popExitTransition = { slideOutHorizontally(animationSpec = animSpec) { fullWidth -> -fullWidth * getSlideDirection(initialState.destination.route, targetState.destination.route) } }) {
                        composable(Routes.MONTHLY) { MonthlyCalendarScreen(viewModel = viewModel, navController = navController, isSearchMode = isSearchMode, onSearchResultSelected = onSearchResultSelected) }
                        composable(Routes.DAILY) { DailyCalendarScreen(viewModel = viewModel, navController = navController, isSearchMode = isSearchMode, onSearchResultSelected = onSearchResultSelected) }
                        composable(Routes.WEEKLY) { WeeklyCalendarScreen(viewModel = viewModel, navController = navController, isSearchMode = isSearchMode, onSearchResultSelected = onSearchResultSelected) }
                    }
                }
            }
            // 設定画面オーバーレイ（横スライドで開閉）
            AnimatedVisibility(visible = isSettingsOpen, enter = slideInHorizontally(animationSpec = animSpec) { fullWidth -> fullWidth }, exit = slideOutHorizontally(animationSpec = animSpec) { fullWidth -> fullWidth }) {
                SettingsScreen(viewModel = viewModel, onBack = { isSettingsOpen = false })
            }
        }
    }
    // 同期確認ダイアログ
    if (showSyncDialog) SyncConfirmDialog(colors = colors, onDismiss = { showSyncDialog = false }, onConfirm = { viewModel.loadEvents() })
    // 終了確認ダイアログ
    if (showExitDialog) ExitConfirmDialog(colors = colors, onDismiss = { showExitDialog = false }, onConfirm = { showExitDialog = false; activity?.finish() })
    // 年月選択ダイアログ（月表示のタイトルタップ）
    if (showMonthPickerDialog) YearMonthPickerDialog(currentYear = currentMonth.year, currentMonth = currentMonth.monthValue, colors = colors, onDismiss = { showMonthPickerDialog = false }, onDateSelected = { year, month -> viewModel.selectDate(LocalDate.of(year, month, 1)); showMonthPickerDialog = false })
    // 日付選択ダイアログ（日／週表示のタイトルタップ）
    if (showDatePickerDialog) GolendarDatePickerDialog(initialDate = selectedDate, colors = colors, onDismiss = { showDatePickerDialog = false }, onDateSelected = { viewModel.selectDate(it); showDatePickerDialog = false })
    // 初回通知セットアップ
    if (showNotificationSetup) NotificationSetupDialog(colors = colors, onComplete = { viewModel.markNotificationSetupDone() })
    // 初回カレンダー権限セットアップ
    if (showCalendarSetup) CalendarPermissionDialog(colors = colors, title = "Googleカレンダーへのアクセス", message = "Googleカレンダーと同期して予定を読み書きするには、カレンダーへのアクセス許可が必要です。\nGolendarモード（アプリ内のみ）だけを使う場合は、許可せずに後で設定画面から変更することもできます。", onResult = { _ -> viewModel.markCalendarSetupDone() }, onDismiss = { viewModel.markCalendarSetupDone() })

    // 初回カレンダー選択ダイアログ（Google モード選択時）
    if (showCalendarSelection) {
        val calendars = remember { mutableStateOf<List<com.monsivamon.golender.data.source.CalendarMeta>>(emptyList()) }
        LaunchedEffect(Unit) {
            calendars.value = viewModel.loadAllCalendarMetas()
        }
        CalendarSelectionDialog(
            colors = colors,
            calendars = calendars.value,
            onComplete = { list ->
                viewModel.saveSelectedCalendars(list)
                viewModel.markCalendarSelectionDone()
            },
        )
    }

    // 初回ジェスチャー案内ダイアログ
    if (showGestureSetup) GestureSetupDialog(colors = colors, onComplete = { viewModel.markGestureSetupDone() })
}