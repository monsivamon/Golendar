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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.navigation.NavController
import com.monsivamon.golender.viewmodel.CalendarMode
import com.monsivamon.golender.viewmodel.CalendarViewModel
import com.monsivamon.golender.viewmodel.ThemeMode
import java.time.DayOfWeek

// 折りたたみ可能な設定セクション
@Composable
fun SettingsSection(title: String, colors: AppColors, content: @Composable () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(colors.surface)
                .clickable { expanded = !expanded }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = colors.text)
            Text(if (expanded) "▲" else "▼", color = colors.primaryAccent)
        }
        AnimatedVisibility(visible = expanded) {
            Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp, start = 8.dp, end = 8.dp, bottom = 8.dp)) {
                content()
            }
        }
    }
}

// 設定画面
@Composable
fun SettingsScreen(viewModel: CalendarViewModel, navController: NavController) {
    val context = LocalContext.current
    val themeMode by viewModel.themeMode.collectAsState()
    val weekStartDay by viewModel.weekStartDay.collectAsState()
    val calendarMode by viewModel.calendarMode.collectAsState()
    val availableAccounts by viewModel.availableAccounts.collectAsState()
    val selectedAccount by viewModel.selectedAccount.collectAsState()
    val calendarBgColor by viewModel.calendarBgColor.collectAsState()
    val dayColors by viewModel.dayColors.collectAsState()

    val notifyAtStart by viewModel.notifyAtStart.collectAsState()
    val notify10MinBefore by viewModel.notify10MinBefore.collectAsState()

    val statusMessage by viewModel.statusMessage.collectAsState()
    val colors = getAppColors(themeMode)

    var colorPickerDay by remember { mutableStateOf<DayOfWeek?>(null) }
    var showBgColorPicker by remember { mutableStateOf(false) }

    // カレンダー権限リクエスト用ランチャー
    val calendarPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val readGranted = permissions[Manifest.permission.READ_CALENDAR] ?: false
        val writeGranted = permissions[Manifest.permission.WRITE_CALENDAR] ?: false
        if (readGranted && writeGranted) {
            viewModel.setCalendarMode(CalendarMode.GOOGLE)
        } else {
            viewModel.setCalendarMode(CalendarMode.GOLENDAR)
            Toast.makeText(context, "カレンダーへのアクセスが許可されなかったため、Golendarモードに切り替えました", Toast.LENGTH_LONG).show()
        }
    }

    // 通知権限の状態
    var notificationPermissionGranted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
    }

    // 正確なアラーム権限の状態（Android S+）
    var exactAlarmPermissionGranted by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).canScheduleExactAlarms()
            } else true
        )
    }

    val powerManager = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
    var isIgnoringBatteryOptimizations by remember {
        mutableStateOf(powerManager.isIgnoringBatteryOptimizations(context.packageName))
    }

    // 通知権限リクエスト用ランチャー
    val notificationPermissionLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.RequestPermission()) { isGranted ->
        notificationPermissionGranted = isGranted
    }

    // バックアップ・復元用ランチャー
    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { viewModel.exportBackup(it) }
    }

    val appendLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.importBackup(it, isAppend = true) }
    }

    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.importBackup(it, isAppend = false) }
    }

    // 権限状態を最新に更新
    fun refreshPermissions() {
        notificationPermissionGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            exactAlarmPermissionGranted = (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).canScheduleExactAlarms()
        }
        isIgnoringBatteryOptimizations = powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    LaunchedEffect(Unit) {
        refreshPermissions()
    }

    // ステータスメッセージをトーストで表示
    LaunchedEffect(statusMessage) {
        statusMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearStatusMessage()
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = colors.bg,
        contentColor = colors.text
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .verticalScroll(rememberScrollState())
        ) {
            // 戻るヘッダー
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "‹ 戻る",
                    fontSize = 18.sp,
                    color = colors.primaryAccent,
                    modifier = Modifier.clickable { navController.popBackStack() }
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(text = "設定", fontSize = 24.sp, fontWeight = FontWeight.Bold)
            }
            HorizontalDivider(color = colors.divider, modifier = Modifier.padding(bottom = 8.dp))

            // カレンダーモード設定
            SettingsSection("カレンダーモード", colors) {
                Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(colors.surface).padding(4.dp)) {
                    // Golendarモード
                    Box(
                        modifier = Modifier.weight(1f).clip(RoundedCornerShape(6.dp))
                            .background(if (calendarMode == CalendarMode.GOLENDAR) colors.primaryAccent else Color.Transparent)
                            .clickable { viewModel.setCalendarMode(CalendarMode.GOLENDAR) }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Golendar", color = if (calendarMode == CalendarMode.GOLENDAR) Color.White else colors.text, fontWeight = FontWeight.Bold)
                    }

                    // Googleモード（権限チェック付き）
                    Box(
                        modifier = Modifier.weight(1f).clip(RoundedCornerShape(6.dp))
                            .background(if (calendarMode == CalendarMode.GOOGLE) colors.primaryAccent else Color.Transparent)
                            .clickable {
                                if (calendarMode != CalendarMode.GOOGLE) {
                                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED &&
                                        ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED) {
                                        viewModel.setCalendarMode(CalendarMode.GOOGLE)
                                    } else {
                                        calendarPermissionLauncher.launch(arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR))
                                    }
                                } else {
                                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED ||
                                        ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
                                        calendarPermissionLauncher.launch(arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR))
                                    }
                                }
                            }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Google", color = if (calendarMode == CalendarMode.GOOGLE) Color.White else colors.text, fontWeight = FontWeight.Bold)
                    }
                }
                Text(
                    if (calendarMode == CalendarMode.GOLENDAR) "Googleアカウントと一切同期せず、アプリ内のみで完結します。"
                    else "Googleカレンダーのシステムと同期して予定を読み書きします。",
                    fontSize = 13.sp, color = colors.textGray, modifier = Modifier.padding(top = 8.dp, bottom = 12.dp)
                )

                // Googleモード時はアカウント選択を表示
                if (calendarMode == CalendarMode.GOOGLE && availableAccounts.isNotEmpty()) {
                    Text("表示するカレンダー", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = colors.textGray, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
                    availableAccounts.forEach { account ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { viewModel.setSelectedAccount(account) }.padding(vertical = 4.dp)) {
                            RadioButton(selected = selectedAccount == account, onClick = { viewModel.setSelectedAccount(account) }, colors = RadioButtonDefaults.colors(selectedColor = colors.primaryAccent, unselectedColor = colors.textGray))
                            Text(account, fontSize = 15.sp)
                        }
                    }
                }
            }

            // 通知設定
            SettingsSection("通知設定", colors) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
                    Switch(
                        checked = notifyAtStart,
                        onCheckedChange = { viewModel.setNotifyOptions(it, notify10MinBefore) },
                        colors = SwitchDefaults.colors(checkedThumbColor = colors.primaryAccent, checkedTrackColor = colors.primaryAccent.copy(alpha = 0.5f))
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text("定刻（開始時間）に通知", fontSize = 16.sp, color = colors.text)
                }

                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 16.dp)) {
                    Switch(
                        checked = notify10MinBefore,
                        onCheckedChange = { viewModel.setNotifyOptions(notifyAtStart, it) },
                        colors = SwitchDefaults.colors(checkedThumbColor = colors.primaryAccent, checkedTrackColor = colors.primaryAccent.copy(alpha = 0.5f))
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text("10分前に通知", fontSize = 16.sp, color = colors.text)
                }

                HorizontalDivider(color = colors.divider, modifier = Modifier.padding(vertical = 8.dp))

                Text("バックグラウンド通知の確実化", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = colors.textGray, modifier = Modifier.padding(bottom = 8.dp))

                // バッテリー最適化無効化
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
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

                // 通知許可
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                    Text("通知の許可", fontSize = 14.sp, color = colors.text, modifier = Modifier.weight(1f))
                    if (notificationPermissionGranted) {
                        Text("許可済み", fontSize = 14.sp, color = colors.primaryAccent)
                    } else {
                        TextButton(onClick = { notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }) { Text("許可する", color = colors.primaryAccent) }
                    }
                }

                // 正確なアラーム（Android S+）
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("正確なアラーム機能の許可", fontSize = 14.sp, color = colors.text, modifier = Modifier.weight(1f))
                        if (exactAlarmPermissionGranted) {
                            Text("許可済み", fontSize = 14.sp, color = colors.primaryAccent)
                        } else {
                            TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply { data = Uri.parse("package:${context.packageName}") }) }) { Text("許可する", color = colors.primaryAccent) }
                        }
                    }
                }

                Button(
                    onClick = { refreshPermissions() },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.surface, contentColor = colors.textGray),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp).border(1.dp, colors.divider, RoundedCornerShape(12.dp))
                ) {
                    Text("設定状況を再チェックする", fontSize = 12.sp)
                }
            }

            // カスタム設定
            SettingsSection("カスタム設定", colors) {
                // 背景色
                Text("カレンダー背景色", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = colors.textGray)
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { showBgColorPicker = true }.padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("背景色を選択", fontSize = 16.sp, color = colors.text)
                    Box(
                        modifier = Modifier.size(24.dp).clip(CircleShape)
                            .background(if (calendarBgColor == Color.Unspecified) Color.Transparent else calendarBgColor)
                            .border(1.dp, if (calendarBgColor == Color.Unspecified) colors.textGray else Color.Transparent, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        if (calendarBgColor == Color.Unspecified) Text("/", color = colors.textGray, fontSize = 14.sp)
                    }
                }

                HorizontalDivider(color = colors.divider, modifier = Modifier.padding(vertical = 8.dp))

                // 曜日色
                Text("曜日の色", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = colors.textGray)
                val days = listOf(DayOfWeek.SUNDAY, DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY)
                days.forEach { day ->
                    val color = dayColors[day] ?: Color.Unspecified
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { colorPickerDay = day }.padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(getJpDayOfWeek(day) + "曜日", fontSize = 16.sp, color = colors.text)
                        Box(
                            modifier = Modifier.size(24.dp).clip(CircleShape)
                                .background(if (color == Color.Unspecified) Color.Transparent else color)
                                .border(1.dp, if (color == Color.Unspecified) colors.textGray else Color.Transparent, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            if (color == Color.Unspecified) Text("/", color = colors.textGray, fontSize = 14.sp)
                        }
                    }
                }

                HorizontalDivider(color = colors.divider, modifier = Modifier.padding(vertical = 8.dp))

                // テーマ
                Text("表示テーマ", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = colors.textGray, modifier = Modifier.padding(bottom = 4.dp))
                ThemeMode.entries.forEach { mode ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { viewModel.setThemeMode(mode) }.padding(vertical = 4.dp)) {
                        RadioButton(selected = themeMode == mode, onClick = { viewModel.setThemeMode(mode) }, colors = RadioButtonDefaults.colors(selectedColor = colors.primaryAccent, unselectedColor = colors.textGray))
                        Text(when (mode) { ThemeMode.SYSTEM -> "端末の設定に合わせる"; ThemeMode.LIGHT -> "ライトモード"; ThemeMode.DARK -> "ダークモード" }, fontSize = 15.sp)
                    }
                }

                HorizontalDivider(color = colors.divider, modifier = Modifier.padding(vertical = 8.dp))

                // 週の始まり
                Text("週の始まり", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = colors.textGray, modifier = Modifier.padding(bottom = 4.dp))
                listOf(DayOfWeek.SUNDAY to "日曜日から始める", DayOfWeek.MONDAY to "月曜日から始める").forEach { (day, label) ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { viewModel.setWeekStartDay(day) }.padding(vertical = 4.dp)) {
                        RadioButton(selected = weekStartDay == day, onClick = { viewModel.setWeekStartDay(day) }, colors = RadioButtonDefaults.colors(selectedColor = colors.primaryAccent, unselectedColor = colors.textGray))
                        Text(label, fontSize = 15.sp)
                    }
                }
            }

            // バックアップと復元
            SettingsSection("バックアップと復元", colors) {
                Text("現在選択されているカレンダーの予定と設定をJSONで保存します。保存したファイルから別アカウントやGolendarモードへの「追記」が可能です。\n※Googleモードでの「復元（上書き）」はデータ保護のため実行できません。", fontSize = 13.sp, color = colors.textGray, modifier = Modifier.padding(bottom = 12.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { backupLauncher.launch("golendar_backup.json") },
                        colors = ButtonDefaults.buttonColors(containerColor = colors.surface, contentColor = colors.text),
                        modifier = Modifier.weight(1f).border(1.dp, colors.divider, RoundedCornerShape(24.dp)),
                        contentPadding = PaddingValues(0.dp)
                    ) { Text("保存", fontSize = 14.sp) }

                    Button(
                        onClick = { appendLauncher.launch(arrayOf("application/json", "*/*")) },
                        colors = ButtonDefaults.buttonColors(containerColor = colors.surface, contentColor = colors.text),
                        modifier = Modifier.weight(1f).border(1.dp, colors.divider, RoundedCornerShape(24.dp)),
                        contentPadding = PaddingValues(0.dp)
                    ) { Text("追記", fontSize = 14.sp) }

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

            // アプリ情報
            SettingsSection("このアプリについて", colors) {
                AboutAppContent(colors)
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // 背景色ピッカーダイアログ
    if (showBgColorPicker) {
        AlertDialog(
            onDismissRequest = { showBgColorPicker = false },
            containerColor = colors.surface,
            title = { Text("背景色を選択", color = colors.text, fontWeight = FontWeight.Bold) },
            text = {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    item {
                        Box(
                            modifier = Modifier.aspectRatio(1f).clip(CircleShape).border(1.dp, colors.textGray, CircleShape).clickable { viewModel.setCalendarBgColor(Color.Unspecified); showBgColorPicker = false },
                            contentAlignment = Alignment.Center
                        ) { Text("標準", color = colors.textGray, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                    }
                    items(PastelColorPalette) { color ->
                        Box(modifier = Modifier.aspectRatio(1f).clip(CircleShape).background(color).clickable { viewModel.setCalendarBgColor(color); showBgColorPicker = false })
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showBgColorPicker = false }) { Text("閉じる", color = colors.textGray) } }
        )
    }

    // 曜日色ピッカーダイアログ
    if (colorPickerDay != null) {
        AlertDialog(
            onDismissRequest = { colorPickerDay = null },
            containerColor = colors.surface,
            title = { Text("${getJpDayOfWeek(colorPickerDay!!)}曜日の色", color = colors.text, fontWeight = FontWeight.Bold) },
            text = {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    item {
                        Box(
                            modifier = Modifier.aspectRatio(1f).clip(CircleShape).border(1.dp, colors.textGray, CircleShape).clickable { viewModel.setDayColor(colorPickerDay!!, Color.Unspecified); colorPickerDay = null },
                            contentAlignment = Alignment.Center
                        ) { Text("標準", color = colors.textGray, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                    }
                    items(DayColorPalette) { color ->
                        Box(modifier = Modifier.aspectRatio(1f).clip(CircleShape).background(color).clickable { viewModel.setDayColor(colorPickerDay!!, color); colorPickerDay = null })
                    }
                }
            },
            confirmButton = { TextButton(onClick = { colorPickerDay = null }) { Text("閉じる", color = colors.textGray) } }
        )
    }
}