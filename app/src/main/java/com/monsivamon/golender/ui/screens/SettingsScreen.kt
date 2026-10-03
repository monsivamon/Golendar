package com.monsivamon.golender.ui

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.monsivamon.golender.data.SelectedCalendar
import com.monsivamon.golender.data.source.SystemCalendarSource
import com.monsivamon.golender.data.util.getJpDayOfWeek
import com.monsivamon.golender.ui.dialogs.AiSetupDialog
import com.monsivamon.golender.ui.dialogs.BackgroundColorPickerDialog
import com.monsivamon.golender.ui.dialogs.CalendarColorPickerDialog
import com.monsivamon.golender.ui.dialogs.CalendarPermissionDialog
import com.monsivamon.golender.ui.dialogs.CalendarSelectionDialog
import com.monsivamon.golender.ui.dialogs.DayColorPickerDialog
import com.monsivamon.golender.ui.dialogs.GestureSetupDialog
import com.monsivamon.golender.ui.dialogs.RestoreTargetPickerDialog
import com.monsivamon.golender.data.source.CalendarMeta
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import com.monsivamon.golender.ui.theme.getAppColors
import com.monsivamon.golender.viewmodel.CalendarMode
import com.monsivamon.golender.viewmodel.CalendarViewModel
import com.monsivamon.golender.viewmodel.ThemeMode
import kotlinx.coroutines.launch
import java.time.DayOfWeek

// 設定画面で共通利用するレイアウト定数
private val LIST_ROW_VERTICAL = 10.dp
private val SECTION_LABEL_BOTTOM = 8.dp
private val SECTION_DIVIDER_VERTICAL = 12.dp
private val RADIO_LABEL_SPACING = 8.dp

// タイトルをタップで開閉できる折りたたみ式の設定セクションを表示する
@Composable
fun SettingsSection(
    title: String,
    colors: com.monsivamon.golender.ui.theme.AppColors,
    content: @Composable () -> Unit,
) {
    // セクションの開閉状態を保持する
    var expanded by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        // タイトル行（タップで開閉）
        Row(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(colors.surface)
                .clickable { expanded = !expanded }.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = colors.text)
            Text(if (expanded) "▲" else "▼", color = colors.primaryAccent)
        }
        // 展開時のみ中身を描画する
        AnimatedVisibility(visible = expanded) {
            Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp, start = 8.dp, end = 8.dp, bottom = 8.dp)) {
                content()
            }
        }
    }
}

// ラジオボタン付きの選択行を統一レイアウトで表示する
@Composable
private fun RadioOptionRow(
    selected: Boolean,
    label: String,
    colors: com.monsivamon.golender.ui.theme.AppColors,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = LIST_ROW_VERTICAL),
    ) {
        // ラジオボタン本体（タップは行全体で処理するので onClick=null）
        RadioButton(
            selected = selected,
            onClick = null,
            colors = RadioButtonDefaults.colors(
                selectedColor = colors.primaryAccent,
                unselectedColor = colors.textGray,
            ),
        )
        Spacer(modifier = Modifier.width(RADIO_LABEL_SPACING))
        Text(label, fontSize = 15.sp, color = colors.text)
    }
}

// 設定画面を表示し、カレンダーモード・通知・カスタム・バックアップなどの設定操作を扱う
@Composable
fun SettingsScreen(viewModel: CalendarViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    // ViewModel から各種設定状態を購読する
    val themeMode by viewModel.themeMode.collectAsState()
    val weekStartDay by viewModel.weekStartDay.collectAsState()
    val calendarMode by viewModel.calendarMode.collectAsState()
    val selectedCalendars by viewModel.selectedCalendars.collectAsState()
    val showHolidays by viewModel.showHolidays.collectAsState()
    val calendarBgColor by viewModel.calendarBgColor.collectAsState()
    val dayColors by viewModel.dayColors.collectAsState()
    val backupPhotos by viewModel.backupPhotos.collectAsState()
    val notifyAtStart by viewModel.notifyAtStart.collectAsState()
    val notify10MinBefore by viewModel.notify10MinBefore.collectAsState()
    val showCalendarSelection by viewModel.showCalendarSelection.collectAsState()
    val colors = getAppColors(themeMode, calendarBgColor)
    val isSystemDark = isSystemInDarkTheme()
    val scope = rememberCoroutineScope()

    // 個別カラー設定ダイアログ用の状態
    var colorPickerDay by remember { mutableStateOf<DayOfWeek?>(null) }
    var colorPickerCalendar by remember { mutableStateOf<SelectedCalendar?>(null) }
    var showBgColorPicker by remember { mutableStateOf(false) }
    // 再起動・権限・復元先選択などモーダル系の状態
    var showRestartModal by remember { mutableStateOf(false) }
    var showCalendarPermissionDialog by remember { mutableStateOf(false) }
    var showRestoreTargetDialog by remember { mutableStateOf(false) }
    var pendingRestoreTargetCalendarId by remember { mutableStateOf<Long?>(null) }
    var showNoAccountDialog by remember { mutableStateOf(false) }

    // 通知権限の現在の許可状態
    var notificationPermissionGranted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
    }
    // 正確なアラーム権限の現在の許可状態
    var exactAlarmPermissionGranted by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).canScheduleExactAlarms()
            } else true
        )
    }
    // バッテリー最適化の除外状態
    val powerManager = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
    var isIgnoringBatteryOptimizations by remember {
        mutableStateOf(powerManager.isIgnoringBatteryOptimizations(context.packageName))
    }
    // 通知権限要求ランチャー
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        notificationPermissionGranted = isGranted
    }

    // バックアップ／追記／復元のファイル選択ランチャー
    val backupMime = if (backupPhotos) "application/zip" else "application/json"
    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(backupMime)) { uri ->
        uri?.let { viewModel.exportBackup(it) }
    }
    val appendLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val target = pendingRestoreTargetCalendarId
        pendingRestoreTargetCalendarId = null
        uri?.let { viewModel.importBackup(it, isAppend = true, targetCalendarId = target) }
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.importBackup(it, isAppend = false) }
    }

    // チュートリアル系ダイアログの表示状態
    val showCalendarTutorial by viewModel.showCalendarTutorial.collectAsState()
    val showGestureTutorial by viewModel.showGestureTutorial.collectAsState()
    val showAiSetup by viewModel.showAiSetup.collectAsState()

    // 権限状態を再取得して UI に反映する
    fun refreshPermissions() {
        notificationPermissionGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            exactAlarmPermissionGranted = (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).canScheduleExactAlarms()
        }
        isIgnoringBatteryOptimizations = powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }
    // 画面表示時に一度リフレッシュする
    LaunchedEffect(Unit) { refreshPermissions() }

    // バックアップ／復元の進捗を進行中ダイアログで表示する
    val backupProgress by viewModel.backupProgress.collectAsState()
    backupProgress?.let { p ->
        AlertDialog(
            onDismissRequest = { },
            title = { Text(p.phase, color = colors.text, fontWeight = FontWeight.Bold, fontSize = 18.sp) },
            text = {
                Column {
                    if (p.total > 0) {
                        // 総数が分かる場合は進捗率を表示する
                        LinearProgressIndicator(
                            progress = { p.current.toFloat() / p.total.coerceAtLeast(1) },
                            modifier = Modifier.fillMaxWidth(),
                            color = colors.primaryAccent,
                        )
                        Text(
                            "${p.current} / ${p.total}",
                            color = colors.textGray,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    } else {
                        // 総数不明時はインジターミネート表示
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth(),
                            color = colors.primaryAccent,
                        )
                    }
                }
            },
            confirmButton = { },
            containerColor = colors.surface,
            titleContentColor = colors.text,
        )
    }

    // 画面全体を Surface で包み、縦スクロールで設定項目を並べる
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = colors.bg,
        contentColor = colors.text
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
        ) {
            // 戻るボタンと画面タイトル
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "‹ 戻る",
                    fontSize = 18.sp,
                    color = colors.primaryAccent,
                    modifier = Modifier.clickable { onBack() }
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(text = "設定", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = colors.text)
            }
            HorizontalDivider(color = colors.divider, modifier = Modifier.padding(bottom = 8.dp))

            // ==================== カレンダーモード ====================
            SettingsSection("カレンダーモード", colors) {
                // Golendar / Google の切替ボタン（セグメント風）
                Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(colors.surface).padding(4.dp)) {
                    // Golendar 側
                    Box(
                        modifier = Modifier.weight(1f).clip(RoundedCornerShape(6.dp))
                            .background(if (calendarMode == CalendarMode.GOLENDAR) colors.primaryAccent else Color.Transparent)
                            .clickable { viewModel.setCalendarMode(CalendarMode.GOLENDAR) }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Golendar", color = if (calendarMode == CalendarMode.GOLENDAR) Color.White else colors.text, fontWeight = FontWeight.Bold)
                    }
                    // Google 側（アカウント有無と権限を確認してから切替）
                    Box(
                        modifier = Modifier.weight(1f).clip(RoundedCornerShape(6.dp))
                            .background(if (calendarMode == CalendarMode.GOOGLE) colors.primaryAccent else Color.Transparent)
                            .clickable {
                                scope.launch {
                                    // 最新のアカウント一覧を取得（キャッシュが古い可能性があるため）
                                    val accounts = viewModel.fetchAccountNames()
                                    if (accounts.isEmpty()) {
                                        showNoAccountDialog = true
                                        return@launch
                                    }
                                    // カレンダー権限の現在の許可状態を確認する
                                    val hasRead = ContextCompat.checkSelfPermission(
                                        context, Manifest.permission.READ_CALENDAR
                                    ) == PackageManager.PERMISSION_GRANTED
                                    val hasWrite = ContextCompat.checkSelfPermission(
                                        context, Manifest.permission.WRITE_CALENDAR
                                    ) == PackageManager.PERMISSION_GRANTED
                                    when {
                                        hasRead && hasWrite -> {
                                            if (calendarMode != CalendarMode.GOOGLE) {
                                                showRestartModal = true
                                            }
                                        }
                                        else -> showCalendarPermissionDialog = true
                                    }
                                }
                            }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Google", color = if (calendarMode == CalendarMode.GOOGLE) Color.White else colors.text, fontWeight = FontWeight.Bold)
                    }
                }
                // モードの説明文
                Text(
                    if (calendarMode == CalendarMode.GOLENDAR) "Googleアカウントと一切同期せず、アプリ内のみで完結します。"
                    else "Googleカレンダーのシステムと同期して予定を読み書きします。",
                    fontSize = 13.sp, color = colors.textGray, modifier = Modifier.padding(top = 8.dp, bottom = 12.dp)
                )
                // Google モード時はカレンダー一覧を表示する
                if (calendarMode == CalendarMode.GOOGLE) {
                    val grouped = selectedCalendars.groupBy { it.accountName }
                    // 一括表示／非表示ボタン
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { viewModel.setAllCalendarsVisible(true) }) { Text("全て表示", color = colors.primaryAccent) }
                        TextButton(onClick = { viewModel.setAllCalendarsVisible(false) }) { Text("全て非表示", color = colors.primaryAccent) }
                    }
                    // アカウント単位でカレンダーを一覧表示する
                    grouped.forEach { (account, cals) ->
                        Text(account, fontWeight = FontWeight.Bold, color = colors.textGray, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
                        cals.forEach { cal ->
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                // 表示 ON/OFF チェックボックス
                                Checkbox(
                                    checked = cal.isVisible,
                                    onCheckedChange = { viewModel.setCalendarVisibility(cal.calendarId, it) },
                                    colors = CheckboxDefaults.colors(checkedColor = colors.primaryAccent)
                                )
                                // カレンダー色（タップで個別色ピッカーを開く）
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(if (cal.colorArgb == 0) Color.Gray else Color(cal.colorArgb))
                                        .clickable { colorPickerCalendar = cal }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(cal.displayName.ifBlank { "名称未設定" }, color = colors.text, fontSize = 15.sp)
                            }
                        }
                    }
                    // 端末のカレンダー追加・削除に追従するための再取得ボタン
                    Button(
                        onClick = { viewModel.refreshAvailableCalendars() },
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.surface, contentColor = colors.primaryAccent)
                    ) {
                        Text("カレンダー一覧を再取得")
                    }
                }
            }

            // ==================== 通知設定 ====================
            SettingsSection("通知設定", colors) {
                // 定刻通知 ON/OFF
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
                    Switch(
                        checked = notifyAtStart,
                        onCheckedChange = { viewModel.setNotifyOptions(it, notify10MinBefore) },
                        colors = SwitchDefaults.colors(checkedThumbColor = colors.primaryAccent, checkedTrackColor = colors.primaryAccent.copy(alpha = 0.5f))
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text("定刻（開始時間）に通知", fontSize = 16.sp, color = colors.text)
                }
                // 10 分前通知 ON/OFF
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 16.dp)) {
                    Switch(
                        checked = notify10MinBefore,
                        onCheckedChange = { viewModel.setNotifyOptions(notifyAtStart, it) },
                        colors = SwitchDefaults.colors(checkedThumbColor = colors.primaryAccent, checkedTrackColor = colors.primaryAccent.copy(alpha = 0.5f))
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text("10分前に通知", fontSize = 16.sp, color = colors.text)
                }
                HorizontalDivider(color = colors.divider, modifier = Modifier.padding(vertical = SECTION_DIVIDER_VERTICAL))
                // バックグラウンド通知の確実化セクション見出し
                Text(
                    "バックグラウンド通知の確実化",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textGray,
                    modifier = Modifier.padding(bottom = SECTION_LABEL_BOTTOM),
                )
                // バッテリー最適化除外の設定状況行
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth().padding(vertical = LIST_ROW_VERTICAL),
                ) {
                    Text("バッテリー最適化の無効化\n（スリープ中の通知遅延を防ぎます）", fontSize = 14.sp, color = colors.text, modifier = Modifier.weight(1f))
                    if (isIgnoringBatteryOptimizations) {
                        Text("無効化済み", fontSize = 14.sp, color = colors.primaryAccent)
                    } else {
                        TextButton(onClick = {
                            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                data = Uri.parse("package:${context.packageName}")
                            }
                            context.startActivity(intent)
                        }) { Text("設定を開く", color = colors.primaryAccent) }
                    }
                }
                // 通知権限の設定状況行
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth().padding(vertical = LIST_ROW_VERTICAL),
                ) {
                    Text("通知の許可", fontSize = 14.sp, color = colors.text, modifier = Modifier.weight(1f))
                    if (notificationPermissionGranted) {
                        Text("許可済み", fontSize = 14.sp, color = colors.primaryAccent)
                    } else {
                        TextButton(onClick = { notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }) {
                            Text("許可する", color = colors.primaryAccent)
                        }
                    }
                }
                // 正確なアラームの設定状況行（Android 12+ のみ表示）
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth().padding(vertical = LIST_ROW_VERTICAL),
                    ) {
                        Text("正確なアラーム機能の許可", fontSize = 14.sp, color = colors.text, modifier = Modifier.weight(1f))
                        if (exactAlarmPermissionGranted) {
                            Text("許可済み", fontSize = 14.sp, color = colors.primaryAccent)
                        } else {
                            TextButton(onClick = {
                                context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                })
                            }) { Text("許可する", color = colors.primaryAccent) }
                        }
                    }
                }
                // 設定状況を再取得するボタン
                Button(
                    onClick = { refreshPermissions() },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.surface, contentColor = colors.textGray),
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp).border(1.dp, colors.divider, RoundedCornerShape(12.dp))
                ) {
                    Text("設定状況を再チェックする", fontSize = 12.sp)
                }
            }

            // ==================== カスタム設定 ====================
            SettingsSection("カスタム設定", colors) {
                // 祝日表示 ON/OFF
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
                    Switch(
                        checked = showHolidays,
                        onCheckedChange = { viewModel.setShowHolidays(it) },
                        colors = SwitchDefaults.colors(checkedThumbColor = colors.primaryAccent, checkedTrackColor = colors.primaryAccent.copy(alpha = 0.5f))
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text("祝日を表示", fontSize = 16.sp, color = colors.text)
                }
                HorizontalDivider(color = colors.divider, modifier = Modifier.padding(vertical = SECTION_DIVIDER_VERTICAL))
                Text(
                    "アプリ背景色",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textGray,
                    modifier = Modifier.padding(bottom = SECTION_LABEL_BOTTOM),
                )
                // アプリ背景色の選択行（右端に現在色の丸を表示）
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { showBgColorPicker = true },
                        )
                        .padding(vertical = LIST_ROW_VERTICAL),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("アプリ背景色を選択", fontSize = 16.sp, color = colors.text)
                    Box(
                        modifier = Modifier.size(24.dp).clip(CircleShape)
                            .background(if (calendarBgColor == Color.Unspecified) Color.Transparent else calendarBgColor)
                            .border(1.dp, colors.textGray, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        // 未設定時は「/」を表示する
                        if (calendarBgColor == Color.Unspecified) Text("/", color = colors.textGray, fontSize = 14.sp)
                    }
                }
                HorizontalDivider(color = colors.divider, modifier = Modifier.padding(vertical = SECTION_DIVIDER_VERTICAL))
                Text(
                    "曜日の色",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textGray,
                    modifier = Modifier.padding(bottom = SECTION_LABEL_BOTTOM),
                )
                // 曜日ごとの色設定行を列挙する
                val days = listOf(DayOfWeek.SUNDAY, DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY)
                days.forEach { day ->
                    val color = dayColors[day] ?: Color.Unspecified
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { colorPickerDay = day },
                            )
                            .padding(vertical = LIST_ROW_VERTICAL),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(getJpDayOfWeek(day) + "曜日", fontSize = 16.sp, color = colors.text)
                        Box(
                            modifier = Modifier.size(24.dp).clip(CircleShape)
                                .background(if (color == Color.Unspecified) Color.Transparent else color)
                                .border(1.dp, colors.textGray, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            // 未設定時は「/」を表示する
                            if (color == Color.Unspecified) Text("/", color = colors.textGray, fontSize = 14.sp)
                        }
                    }
                }
                HorizontalDivider(color = colors.divider, modifier = Modifier.padding(vertical = SECTION_DIVIDER_VERTICAL))
                Text(
                    "表示テーマ",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textGray,
                    modifier = Modifier.padding(bottom = SECTION_LABEL_BOTTOM),
                )
                // ライト／ダーク／システム追従の選択
                ThemeMode.entries.forEach { mode ->
                    RadioOptionRow(
                        selected = themeMode == mode,
                        label = when (mode) {
                            ThemeMode.SYSTEM -> "端末の設定に合わせる"
                            ThemeMode.LIGHT -> "ライトモード"
                            ThemeMode.DARK -> "ダークモード"
                        },
                        colors = colors,
                        onClick = { viewModel.setThemeMode(mode) },
                    )
                }
                HorizontalDivider(color = colors.divider, modifier = Modifier.padding(vertical = SECTION_DIVIDER_VERTICAL))
                Text(
                    "週の始まり",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textGray,
                    modifier = Modifier.padding(bottom = SECTION_LABEL_BOTTOM),
                )
                // 週の開始曜日の選択
                listOf(
                    DayOfWeek.SUNDAY to "日曜日から始める",
                    DayOfWeek.MONDAY to "月曜日から始める",
                ).forEach { (day, label) ->
                    RadioOptionRow(
                        selected = weekStartDay == day,
                        label = label,
                        colors = colors,
                        onClick = { viewModel.setWeekStartDay(day) },
                    )
                }
            }

            // ==================== チュートリアル ====================
            SettingsSection("チュートリアル", colors) {
                // カレンダー権限の案内を再表示する
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { viewModel.requestShowCalendarTutorial() },
                        )
                        .padding(vertical = LIST_ROW_VERTICAL),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("カレンダーのアクセス権限を確認する", fontSize = 16.sp, color = colors.text)
                }
                // ジェスチャー操作の案内を再表示する
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { viewModel.requestShowGestureTutorial() },
                        )
                        .padding(vertical = LIST_ROW_VERTICAL),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("ジェスチャー操作の案内を見る", fontSize = 16.sp, color = colors.text)
                }
                // AI 解析の使い方を再表示する
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { viewModel.requestShowAiSetup() },
                        )
                        .padding(vertical = LIST_ROW_VERTICAL),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("AI解析の使い方を見る", fontSize = 16.sp, color = colors.text)
                }
            }

            // ==================== バックアップと復元 ====================
            SettingsSection("バックアップと復元", colors) {
                Text(
                    "現在選択されているカレンダーの予定と設定をJSONで保存します。保存したファイルから別アカウントやGolendarモードへの「追記」が可能です。\n※Googleモードでの「復元（上書き）」はデータ保護のため実行できません。",
                    fontSize = 13.sp, color = colors.textGray, modifier = Modifier.padding(bottom = 12.dp)
                )
                // Golendar モード時のみ写真もバックアップの選択を表示する
                if (calendarMode == CalendarMode.GOLENDAR) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.setBackupPhotos(!backupPhotos) }
                            .padding(vertical = 8.dp),
                    ) {
                        Checkbox(
                            checked = backupPhotos,
                            onCheckedChange = { viewModel.setBackupPhotos(it) },
                            colors = CheckboxDefaults.colors(
                                checkedColor = colors.primaryAccent,
                                uncheckedColor = colors.textGray,
                            ),
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(
                                "写真もバックアップ",
                                fontSize = 15.sp, color = colors.text, fontWeight = FontWeight.Medium,
                            )
                            Text(
                                if (backupPhotos)
                                    "予定に添付した写真も ZIP ファイルに含めます（Golendarモードのみ）"
                                else
                                    "OFF の場合は従来通り JSON で保存されます",
                                fontSize = 12.sp, color = colors.textGray,
                            )
                        }
                    }
                    HorizontalDivider(color = colors.divider, modifier = Modifier.padding(vertical = 8.dp))
                }
                // 保存／追記／復元のボタン列
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // 保存ボタン（拡張子を選んでファイル作成）
                    Button(
                        onClick = {
                            val name = if (backupPhotos) "golendar_backup.zip" else "golendar_backup.json"
                            backupLauncher.launch(name)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = colors.surface, contentColor = colors.text),
                        modifier = Modifier.weight(1f).border(1.dp, colors.divider, RoundedCornerShape(24.dp)),
                        contentPadding = PaddingValues(0.dp)
                    ) { Text("保存", fontSize = 14.sp) }
                    // 追記ボタン（Google モード時は復元先カレンダーを先に選ぶ）
                    Button(
                        onClick = {
                            if (calendarMode == CalendarMode.GOOGLE) {
                                showRestoreTargetDialog = true
                            } else {
                                pendingRestoreTargetCalendarId = null
                                appendLauncher.launch(arrayOf("application/json", "*/*"))
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = colors.surface, contentColor = colors.text),
                        modifier = Modifier.weight(1f).border(1.dp, colors.divider, RoundedCornerShape(24.dp)),
                        contentPadding = PaddingValues(0.dp)
                    ) { Text("追記", fontSize = 14.sp) }
                    // 復元ボタン（Google モードでは無効化して案内を出す）
                    Button(
                        onClick = {
                            if (calendarMode == CalendarMode.GOOGLE) {
                                Toast.makeText(context, "Googleモードでは追記のみ可能です", Toast.LENGTH_SHORT).show()
                            } else {
                                restoreLauncher.launch(arrayOf("application/json", "*/*"))
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (calendarMode == CalendarMode.GOOGLE) colors.bg else colors.surface,
                            contentColor = if (calendarMode == CalendarMode.GOOGLE) colors.textGray else colors.sunRed
                        ),
                        modifier = Modifier.weight(1f).border(1.dp, colors.divider, RoundedCornerShape(24.dp)),
                        contentPadding = PaddingValues(0.dp)
                    ) { Text("復元", fontSize = 14.sp) }
                }
            }

            // ==================== このアプリについて ====================
            SettingsSection("このアプリについて", colors) {
                com.monsivamon.golender.ui.AboutAppContent(colors)
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // 個別ダイアログ: 背景色ピッカー
    if (showBgColorPicker) {
        BackgroundColorPickerDialog(
            colors = colors,
            themeMode = themeMode,
            isSystemDark = isSystemDark,
            currentColor = calendarBgColor,
            onDismiss = { showBgColorPicker = false },
            onColorSelected = { viewModel.setCalendarBgColor(it) },
        )
    }
    // 個別ダイアログ: 曜日色ピッカー
    colorPickerDay?.let { day ->
        DayColorPickerDialog(
            day = day,
            colors = colors,
            currentColor = dayColors[day] ?: Color.Unspecified,
            onDismiss = { colorPickerDay = null },
            onColorSelected = { viewModel.setDayColor(day, it) },
        )
    }
    // 個別ダイアログ: カレンダー色ピッカー
    colorPickerCalendar?.let { cal ->
        CalendarColorPickerDialog(
            calendarName = cal.displayName,
            colors = colors,
            currentColorArgb = cal.colorArgb,
            onDismiss = { colorPickerCalendar = null },
            onColorSelected = { argb ->
                viewModel.setCalendarColor(cal.calendarId, argb)
                colorPickerCalendar = null
            }
        )
    }
    // カレンダー選択ダイアログ（初回 or 再選択）
    if (showCalendarSelection) {
        val calendars = remember { mutableStateOf<List<CalendarMeta>>(emptyList()) }
        LaunchedEffect(Unit) {
            // 実アカウントのカレンダーのみを取得する（account_local などを除外）
            calendars.value = viewModel.loadGoogleCalendarMetas()
        }
        CalendarSelectionDialog(
            colors = colors,
            calendars = calendars.value,
            onComplete = { list ->
                viewModel.saveSelectedCalendars(list)
                viewModel.markCalendarSelectionDone()
            }
        )
    }
    // カレンダー権限の許可ダイアログ
    if (showCalendarPermissionDialog) {
        CalendarPermissionDialog(
            colors = colors,
            title = "Googleカレンダーへのアクセス",
            message = "Googleカレンダーと同期するには、カレンダーへのアクセス許可が必要です。\n" +
                    "次の画面で「許可」を選んでください。",
            onResult = { granted ->
                showCalendarPermissionDialog = false
                if (granted) {
                    showRestartModal = true
                } else {
                    // 拒否されたら Golendar モードに戻して案内を出す
                    viewModel.setCalendarMode(CalendarMode.GOLENDAR)
                    Toast.makeText(
                        context,
                        "カレンダーへのアクセスが許可されなかったため、Golendarモードに切り替えました",
                        Toast.LENGTH_LONG
                    ).show()
                }
            },
            onDismiss = { showCalendarPermissionDialog = false },
        )
    }
    // 再起動確認ダイアログ（Google モード切替時）
    if (showRestartModal) {
        AlertDialog(
            onDismissRequest = { showRestartModal = false },
            containerColor = colors.surface,
            title = { Text("再起動が必要です", color = colors.text, fontWeight = FontWeight.Bold) },
            text = { Text("Googleアカウントを認識して同期するため、アプリを再起動します。", color = colors.text) },
            confirmButton = {
                TextButton(onClick = {
                    showRestartModal = false
                    viewModel.setCalendarMode(CalendarMode.GOOGLE)
                    // タスクを再起動してアプリを再起動する
                    val packageManager = context.packageManager
                    val intent = packageManager.getLaunchIntentForPackage(context.packageName)
                    if (intent != null) {
                        val componentName = intent.component
                        val mainIntent = Intent.makeRestartActivityTask(componentName)
                        context.startActivity(mainIntent)
                        Runtime.getRuntime().exit(0)
                    }
                }) { Text("再起動", color = colors.primaryAccent, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showRestartModal = false }) { Text("キャンセル", color = colors.textGray) }
            }
        )
    }
    // チュートリアル: カレンダー権限
    if (showCalendarTutorial) {
        CalendarPermissionDialog(
            colors = colors,
            title = "Googleカレンダーへのアクセス",
            message = "Googleカレンダーと同期して予定を読み書きするには、カレンダーへのアクセス許可が必要です。\n" +
                    "\n" +
                    "Golendarモード（アプリ内のみ）だけを使う場合は、許可せずに後で設定画面から変更することもできます。",
            onResult = { viewModel.dismissCalendarTutorial() },
            onDismiss = { viewModel.dismissCalendarTutorial() },
        )
    }
    // チュートリアル: ジェスチャー案内
    if (showGestureTutorial) {
        GestureSetupDialog(
            colors = colors,
            onComplete = { viewModel.dismissGestureTutorial() },
        )
    }
    // チュートリアル: AI 解析の使い方
    if (showAiSetup) {
        AiSetupDialog(
            colors = colors,
            onComplete = { viewModel.markAiSetupDone() },
        )
    }

    // 復元先カレンダー選択ダイアログ（Google モードの「追記」時のみ表示）
    if (showRestoreTargetDialog) {
        val calendarsState = remember { mutableStateOf<List<CalendarMeta>>(emptyList()) }
        LaunchedEffect(Unit) {
            // 復元先には端末ローカルのカレンダーも候補に含めるため、全カレンダーを取得する
            calendarsState.value = viewModel.loadAllCalendarMetas()
        }
        RestoreTargetPickerDialog(
            colors = colors,
            calendars = calendarsState.value,
            onDismiss = { showRestoreTargetDialog = false },
            onConfirm = { meta ->
                showRestoreTargetDialog = false
                pendingRestoreTargetCalendarId = meta.id
                appendLauncher.launch(arrayOf("application/json", "*/*"))
            },
        )
    }

    // Google アカウントが 1 件も無い状態で Google モードへ切り替えようとした場合の案内
    if (showNoAccountDialog) {
        AlertDialog(
            onDismissRequest = { showNoAccountDialog = false },
            containerColor = colors.surface,
            title = {
                Text(
                    "Googleアカウントが見つかりません",
                    color = colors.text,
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                Text(
                    "端末にGoogleアカウントが設定されていないため、Googleモードを使用できません。\n\n" +
                            "端末の「設定」→「アカウント」からGoogleアカウントを追加してから、もう一度お試しください。\n\n" +
                            "Golendarモード（アプリ内のみ）は引き続きご利用いただけます。",
                    color = colors.text,
                    fontSize = 14.sp,
                )
            },
            confirmButton = {
                TextButton(onClick = { showNoAccountDialog = false }) {
                    Text(
                        "閉じる",
                        color = colors.primaryAccent,
                        fontWeight = FontWeight.Bold,
                    )
                }
            },
        )
    }
}