package com.monsivamon.golender.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.AlertDialog
import com.monsivamon.golender.data.SelectedCalendar
import com.monsivamon.golender.data.source.CalendarMeta
import com.monsivamon.golender.ui.dialogs.CalendarColorPickerDialog
import com.monsivamon.golender.ui.dialogs.CalendarPermissionDialog
import com.monsivamon.golender.ui.dialogs.CalendarModeTutorialDialog
import com.monsivamon.golender.ui.dialogs.CalendarSelectionDialog
import com.monsivamon.golender.ui.dialogs.DayColorPickerDialog
import com.monsivamon.golender.ui.dialogs.BackgroundColorPickerDialog
import com.monsivamon.golender.ui.dialogs.AiSetupDialog
import com.monsivamon.golender.ui.dialogs.GestureSetupDialog
import com.monsivamon.golender.ui.dialogs.MapSetupDialog
import com.monsivamon.golender.ui.dialogs.ScheduleAddTutorialDialog
import com.monsivamon.golender.ui.dialogs.MonthViewToggleTutorialDialog
import com.monsivamon.golender.ui.dialogs.ScheduleEditTutorialDialog
import com.monsivamon.golender.ui.dialogs.RecurringTutorialDialog
import com.monsivamon.golender.ui.dialogs.PhotoTutorialDialog
import com.monsivamon.golender.ui.dialogs.SearchTutorialDialog
import com.monsivamon.golender.ui.dialogs.NotificationTutorialDialog
import com.monsivamon.golender.ui.dialogs.BackupTutorialDialog
import com.monsivamon.golender.ui.dialogs.WidgetTutorialDialog
import com.monsivamon.golender.ui.sections.BackupSection
import com.monsivamon.golender.ui.sections.CalendarModeSection
import com.monsivamon.golender.ui.sections.CustomSettingsSection
import com.monsivamon.golender.ui.sections.NotificationSection
import com.monsivamon.golender.ui.sections.TutorialSection
import com.monsivamon.golender.ui.theme.AppColors
import com.monsivamon.golender.ui.theme.getAppColors
import com.monsivamon.golender.viewmodel.CalendarMode
import com.monsivamon.golender.viewmodel.CalendarViewModel
import com.monsivamon.golender.viewmodel.ThemeMode
import java.time.DayOfWeek

// 設定画面で共通利用するレイアウト定数
internal val LIST_ROW_VERTICAL = 10.dp
internal val SECTION_LABEL_BOTTOM = 8.dp
internal val SECTION_DIVIDER_VERTICAL = 12.dp
internal val RADIO_LABEL_SPACING = 8.dp

// タイトルをタップで開閉できる折りたたみ式の設定セクションを表示する
@Composable
fun SettingsSection(
    title: String,
    colors: AppColors,
    onExpand: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(colors.surface)
                .clickable {
                    val willExpand = !expanded
                    expanded = willExpand
                    if (willExpand) onExpand?.invoke()
                }.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
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

// ラジオボタン付きの選択行を統一レイアウトで表示する
@Composable
fun RadioOptionRow(
    selected: Boolean,
    label: String,
    colors: AppColors,
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

@Composable
fun SettingsScreen(viewModel: CalendarViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val themeMode by viewModel.themeMode.collectAsState()
    val calendarBgColor by viewModel.calendarBgColor.collectAsState()
    val dayColors by viewModel.dayColors.collectAsState()
    val calendarMode by viewModel.calendarMode.collectAsState()
    val showCalendarSelection by viewModel.showCalendarSelection.collectAsState()
    val colors = getAppColors(themeMode, calendarBgColor)
    val isSystemDark = isSystemInDarkTheme()

    var colorPickerDay by remember { mutableStateOf<DayOfWeek?>(null) }
    var colorPickerCalendar by remember { mutableStateOf<SelectedCalendar?>(null) }
    var showBgColorPicker by remember { mutableStateOf(false) }

    var showCalendarPermissionDialog by remember { mutableStateOf(false) }
    var showNoAccountDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }

    val showCalendarTutorial by viewModel.showCalendarTutorial.collectAsState()
    val showGestureTutorial by viewModel.showGestureTutorial.collectAsState()
    val showAiSetup by viewModel.showAiSetup.collectAsState()
    val showCalendarModeTutorial by viewModel.showCalendarModeTutorial.collectAsState()
    val showMapTutorial by viewModel.showMapTutorial.collectAsState()
    val showScheduleAddTutorial by viewModel.showScheduleAddTutorial.collectAsState()
    val showMonthViewToggleTutorial by viewModel.showMonthViewToggleTutorial.collectAsState()
    val showScheduleEditTutorial by viewModel.showScheduleEditTutorial.collectAsState()
    val showRecurringTutorial by viewModel.showRecurringTutorial.collectAsState()
    val showPhotoTutorial by viewModel.showPhotoTutorial.collectAsState()
    val showSearchTutorial by viewModel.showSearchTutorial.collectAsState()
    val showNotificationTutorial by viewModel.showNotificationTutorial.collectAsState()
    val showBackupTutorial by viewModel.showBackupTutorial.collectAsState()
    val showWidgetTutorial by viewModel.showWidgetTutorial.collectAsState()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = colors.bg,
        contentColor = colors.text,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState()),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "‹ 戻る",
                    fontSize = 18.sp,
                    color = colors.primaryAccent,
                    modifier = Modifier.clickable { onBack() },
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(text = "設定", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = colors.text)
            }
            HorizontalDivider(color = colors.divider, modifier = Modifier.padding(bottom = 8.dp))

            // ==================== 各セクション ====================
            CalendarModeSection(
                viewModel = viewModel,
                colors = colors,
                onPermissionNeeded = { showCalendarPermissionDialog = true },
                onNoAccounts = { showNoAccountDialog = true },
                onColorPickerRequested = { cal -> colorPickerCalendar = cal },
            )

            NotificationSection(
                viewModel = viewModel,
                colors = colors,
            )

            CustomSettingsSection(
                viewModel = viewModel,
                colors = colors,
                isSystemDark = isSystemDark,
                onOpenBgColorPicker = { showBgColorPicker = true },
                onOpenDayColorPicker = { day -> colorPickerDay = day },
            )

            TutorialSection(
                viewModel = viewModel,
                colors = colors,
            )

            BackupSection(
                viewModel = viewModel,
                colors = colors,
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors.surface)
                    .clickable { showAboutDialog = true }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "このアプリについて",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.text,
                )
                Text("›", color = colors.primaryAccent, fontSize = 20.sp)
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (showBgColorPicker) {
        BackgroundColorPickerDialog(
            colors = colors, themeMode = themeMode, isSystemDark = isSystemDark,
            currentColor = calendarBgColor,
            onDismiss = { showBgColorPicker = false },
            onColorSelected = { viewModel.setCalendarBgColor(it) },
        )
    }
    colorPickerDay?.let { day ->
        DayColorPickerDialog(
            day = day, colors = colors,
            currentColor = dayColors[day] ?: Color.Unspecified,
            onDismiss = { colorPickerDay = null },
            onColorSelected = { viewModel.setDayColor(day, it) },
        )
    }
    colorPickerCalendar?.let { cal ->
        CalendarColorPickerDialog(
            calendarName = cal.displayName, colors = colors,
            currentColorArgb = cal.colorArgb,
            onDismiss = { colorPickerCalendar = null },
            onColorSelected = { argb ->
                viewModel.setCalendarColor(cal.calendarId, argb)
                colorPickerCalendar = null
            },
        )
    }
    if (showCalendarSelection) {
        val calendars = remember { mutableStateOf<List<CalendarMeta>>(emptyList()) }
        LaunchedEffect(Unit) { calendars.value = viewModel.loadGoogleCalendarMetas() }
        CalendarSelectionDialog(
            colors = colors, calendars = calendars.value,
            onComplete = { list ->
                viewModel.saveSelectedCalendars(list)
                viewModel.markCalendarSelectionDone()
            },
        )
    }
    if (showCalendarPermissionDialog) {
        CalendarPermissionDialog(
            colors = colors,
            title = "Googleカレンダーへのアクセス",
            message = "Googleカレンダーと同期するには、カレンダーへのアクセス許可が必要です。\n" +
                    "次の画面で「許可」を選んでください。",
            onResult = { granted ->
                showCalendarPermissionDialog = false
                if (granted) {
                    // 権限が揃ったので再起動なしで Google モードへ切り替える
                    viewModel.setCalendarMode(CalendarMode.GOOGLE)
                } else {
                    viewModel.setCalendarMode(CalendarMode.GOLENDAR)
                    android.widget.Toast.makeText(
                        context,
                        "カレンダーへのアクセスが許可されなかったため、Golendarモードに切り替えました",
                        android.widget.Toast.LENGTH_LONG,
                    ).show()
                }
            },
            onDismiss = { showCalendarPermissionDialog = false },
        )
    }

    if (showCalendarTutorial) {
        CalendarPermissionDialog(
            colors = colors,
            title = "Googleカレンダーへのアクセス",
            message = "Googleカレンダーと同期して予定を読み書きするには、カレンダーへのアクセス許可が必要です。\n\n" +
                    "Golendarモード（アプリ内のみ）だけを使う場合は、許可せずに後で設定画面から変更することもできます。",
            onResult = { viewModel.dismissCalendarTutorial() },
            onDismiss = { viewModel.dismissCalendarTutorial() },
        )
    }
    if (showGestureTutorial) {
        GestureSetupDialog(colors = colors, onComplete = { viewModel.dismissGestureTutorial() })
    }
    if (showAiSetup) {
        AiSetupDialog(colors = colors, onComplete = { viewModel.markAiSetupDone() })
    }
    if (showCalendarModeTutorial) {
        CalendarModeTutorialDialog(colors = colors, onComplete = { viewModel.dismissCalendarModeTutorial() })
    }
    if (showMapTutorial) {
        MapSetupDialog(colors = colors, onComplete = { viewModel.dismissMapTutorial() })
    }
    if (showMonthViewToggleTutorial) {
        MonthViewToggleTutorialDialog(colors = colors, onComplete = { viewModel.dismissMonthViewToggleTutorial() })
    }
    if (showScheduleAddTutorial) {
        ScheduleAddTutorialDialog(colors = colors, onComplete = { viewModel.dismissScheduleAddTutorial() })
    }
    if (showScheduleEditTutorial) {
        ScheduleEditTutorialDialog(colors = colors, onComplete = { viewModel.dismissScheduleEditTutorial() })
    }
    if (showRecurringTutorial) {
        RecurringTutorialDialog(colors = colors, onComplete = { viewModel.dismissRecurringTutorial() })
    }
    if (showPhotoTutorial) {
        PhotoTutorialDialog(colors = colors, onComplete = { viewModel.dismissPhotoTutorial() })
    }
    if (showSearchTutorial) {
        SearchTutorialDialog(colors = colors, onComplete = { viewModel.dismissSearchTutorial() })
    }
    if (showNotificationTutorial) {
        NotificationTutorialDialog(colors = colors, onComplete = { viewModel.dismissNotificationTutorial() })
    }
    if (showBackupTutorial) {
        BackupTutorialDialog(colors = colors, onComplete = { viewModel.dismissBackupTutorial() })
    }
    if (showWidgetTutorial) {
        WidgetTutorialDialog(colors = colors, onComplete = { viewModel.dismissWidgetTutorial() })
    }
    if (showNoAccountDialog) {
        AlertDialog(
            onDismissRequest = { showNoAccountDialog = false },
            containerColor = colors.surface,
            title = { Text("Googleアカウントが見つかりません", color = colors.text, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "端末にGoogleアカウントが設定されていないため、Googleモードを使用できません。\n\n" +
                            "端末の「設定」→「アカウント」からGoogleアカウントを追加してから、もう一度お試しください。\n\n" +
                            "Golendarモード（アプリ内のみ）は引き続きご利用いただけます。",
                    color = colors.text, fontSize = 14.sp,
                )
            },
            confirmButton = {
                TextButton(onClick = { showNoAccountDialog = false }) {
                    Text("閉じる", color = colors.primaryAccent, fontWeight = FontWeight.Bold)
                }
            },
        )
    }

    if (showAboutDialog) {
        AboutAppDialog(colors = colors, onDismiss = { showAboutDialog = false })
    }
}