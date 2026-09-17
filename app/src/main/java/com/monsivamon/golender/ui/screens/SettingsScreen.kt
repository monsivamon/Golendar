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
import com.monsivamon.golender.data.util.getJpDayOfWeek
import com.monsivamon.golender.ui.dialogs.BackgroundColorPickerDialog
import com.monsivamon.golender.ui.dialogs.DayColorPickerDialog
import com.monsivamon.golender.ui.theme.getAppColors
import com.monsivamon.golender.viewmodel.CalendarMode
import com.monsivamon.golender.viewmodel.CalendarViewModel
import com.monsivamon.golender.viewmodel.ThemeMode
import java.time.DayOfWeek

private val LIST_ROW_VERTICAL = 10.dp
private val SECTION_LABEL_BOTTOM = 8.dp
private val SECTION_DIVIDER_VERTICAL = 12.dp
private val RADIO_LABEL_SPACING = 8.dp

// タイトルをタップで開閉できる折りたたみ式の設定セクションを表示する。
@Composable
fun SettingsSection(
    title: String,
    colors: com.monsivamon.golender.ui.theme.AppColors,
    content: @Composable () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(colors.surface)
                .clickable { expanded = !expanded }.padding(16.dp),
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

// ラジオボタン付きの選択行を統一レイアウトで表示する。
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

// 設定画面を表示し、カレンダーモード・通知・カスタム・バックアップなどの設定操作を扱う。
@Composable
fun SettingsScreen(viewModel: CalendarViewModel, onBack: () -> Unit) {
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
    val colors = getAppColors(themeMode, calendarBgColor)

    val isSystemDark = isSystemInDarkTheme()

    var colorPickerDay by remember { mutableStateOf<DayOfWeek?>(null) }
    var showBgColorPicker by remember { mutableStateOf(false) }
    var showRestartModal by remember { mutableStateOf(false) }

    val calendarPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val readGranted = permissions[Manifest.permission.READ_CALENDAR] ?: false
        val writeGranted = permissions[Manifest.permission.WRITE_CALENDAR] ?: false
        if (readGranted && writeGranted) {
            showRestartModal = true
        } else {
            viewModel.setCalendarMode(CalendarMode.GOLENDAR)
            Toast.makeText(context, "カレンダーへのアクセスが許可されなかったため、Golendarモードに切り替えました", Toast.LENGTH_LONG).show()
        }
    }

    var notificationPermissionGranted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
    }

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

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        notificationPermissionGranted = isGranted
    }

    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { viewModel.exportBackup(it) }
    }

    val appendLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.importBackup(it, isAppend = true) }
    }

    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.importBackup(it, isAppend = false) }
    }

    fun refreshPermissions() {
        notificationPermissionGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            exactAlarmPermissionGranted = (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).canScheduleExactAlarms()
        }
        isIgnoringBatteryOptimizations = powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    LaunchedEffect(Unit) { refreshPermissions() }

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
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
        ) {
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

            SettingsSection("カレンダーモード", colors) {
                Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(colors.surface).padding(4.dp)) {
                    Box(
                        modifier = Modifier.weight(1f).clip(RoundedCornerShape(6.dp))
                            .background(if (calendarMode == CalendarMode.GOLENDAR) colors.primaryAccent else Color.Transparent)
                            .clickable { viewModel.setCalendarMode(CalendarMode.GOLENDAR) }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Golendar", color = if (calendarMode == CalendarMode.GOLENDAR) Color.White else colors.text, fontWeight = FontWeight.Bold)
                    }

                    Box(
                        modifier = Modifier.weight(1f).clip(RoundedCornerShape(6.dp))
                            .background(if (calendarMode == CalendarMode.GOOGLE) colors.primaryAccent else Color.Transparent)
                            .clickable {
                                if (calendarMode != CalendarMode.GOOGLE) {
                                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED &&
                                        ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED) {
                                        showRestartModal = true
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

                if (calendarMode == CalendarMode.GOOGLE && availableAccounts.isNotEmpty()) {
                    Text(
                        "表示するカレンダー",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textGray,
                        modifier = Modifier.padding(bottom = SECTION_LABEL_BOTTOM),
                    )
                    availableAccounts.forEach { account ->
                        RadioOptionRow(
                            selected = selectedAccount == account,
                            label = account,
                            colors = colors,
                            onClick = { viewModel.setSelectedAccount(account) },
                        )
                    }
                }
            }

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

                HorizontalDivider(color = colors.divider, modifier = Modifier.padding(vertical = SECTION_DIVIDER_VERTICAL))

                Text(
                    "バックグラウンド通知の確実化",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textGray,
                    modifier = Modifier.padding(bottom = SECTION_LABEL_BOTTOM),
                )

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

                Button(
                    onClick = { refreshPermissions() },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.surface, contentColor = colors.textGray),
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp).border(1.dp, colors.divider, RoundedCornerShape(12.dp))
                ) {
                    Text("設定状況を再チェックする", fontSize = 12.sp)
                }
            }

            SettingsSection("カスタム設定", colors) {
                Text(
                    "アプリ背景色",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textGray,
                    modifier = Modifier.padding(bottom = SECTION_LABEL_BOTTOM),
                )
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

            SettingsSection("バックアップと復元", colors) {
                Text(
                    "現在選択されているカレンダーの予定と設定をJSONで保存します。保存したファイルから別アカウントやGolendarモードへの「追記」が可能です。\n※Googleモードでの「復元（上書き）」はデータ保護のため実行できません。",
                    fontSize = 13.sp, color = colors.textGray, modifier = Modifier.padding(bottom = 12.dp)
                )

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

            SettingsSection("このアプリについて", colors) {
                AboutAppContent(colors)
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

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

    colorPickerDay?.let { day ->
        DayColorPickerDialog(
            day = day,
            colors = colors,
            currentColor = dayColors[day] ?: Color.Unspecified,
            onDismiss = { colorPickerDay = null },
            onColorSelected = { viewModel.setDayColor(day, it) },
        )
    }

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
}