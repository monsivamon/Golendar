package com.monsivamon.golender.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.monsivamon.golender.data.Event
import com.monsivamon.golender.data.EventPhoto
import com.monsivamon.golender.viewmodel.CalendarMode
import com.monsivamon.golender.data.util.getJpDayOfWeek
import com.monsivamon.golender.data.util.localStartDate
import com.monsivamon.golender.data.util.occursOn
import com.monsivamon.golender.ui.common.GolendarDatePickerDialog
import com.monsivamon.golender.ui.common.slideVertical
import com.monsivamon.golender.ui.common.swipeToNavigateCalendar
import com.monsivamon.golender.ui.components.CalendarCell
import com.monsivamon.golender.ui.components.SearchResultsList
import com.monsivamon.golender.ui.dialogs.DayEventsDialog
import com.monsivamon.golender.ui.dialogs.EventDetailDialog
import com.monsivamon.golender.ui.dialogs.EventDialog
import com.monsivamon.golender.ui.theme.AppColors
import com.monsivamon.golender.ui.theme.getAppColors
import com.monsivamon.golender.viewmodel.CalendarViewModel
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset
import com.monsivamon.golender.ui.dialogs.AiParseDialog

// 月間カレンダー画面（月グリッドと選択日の予定一覧・検索・追加編集を扱う）
@Composable
fun MonthlyCalendarScreen(
    viewModel: CalendarViewModel,
    navController: NavController,
    isSearchMode: Boolean,
    onSearchResultSelected: (LocalDate) -> Unit = {},
) {
    // ViewModel から各種状態を購読する
    val currentMonth by viewModel.currentMonth.collectAsState()
    val selectedDate by viewModel.selectedDate.collectAsState()
    val events by viewModel.events.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val isSearchLoading by viewModel.isSearchLoading.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val weekStartDay by viewModel.weekStartDay.collectAsState()
    val dayColors by viewModel.dayColors.collectAsState()
    val customBg by viewModel.calendarBgColor.collectAsState()
    val showBottomList by viewModel.showBottomList.collectAsState()
    val colors = getAppColors(themeMode, customBg)

    // 可視カレンダー数が 2 以上なら予定カードにカレンダー名を表示する
    val visibleCalendarCount by remember {
        derivedStateOf { viewModel.selectedCalendars.value.count { it.isVisible } }
    }
    val showCalendarName = visibleCalendarCount >= 2

    // 予定ダイアログ・詳細ダイアログ・AI 解析・ FAB の状態
    var showEventDialog by remember { mutableStateOf(false) }
    var showAiParseDialog by remember { mutableStateOf(false) }
    var editingEvent by remember { mutableStateOf<Event?>(null) }
    var showDatePickerForFAB by remember { mutableStateOf(false) }
    var tempFABDate by remember { mutableStateOf<LocalDate?>(null) }
    var showEventDetailDialog by remember { mutableStateOf(false) }
    var viewingEvent by remember { mutableStateOf<Event?>(null) }
    var viewingDate by remember { mutableStateOf<LocalDate?>(null) }
    var fromCalendar by remember { mutableStateOf(false) }
    var editingPhotos by remember { mutableStateOf<List<EventPhoto>>(emptyList()) }
    var detailPhotos by remember { mutableStateOf<List<EventPhoto>>(emptyList()) }
    val aiSetupDone by viewModel.aiSetupDone.collectAsState()

    // 閲覧・編集対象の予定が変わったら添付写真を読み込む
    LaunchedEffect(viewingEvent) { detailPhotos = viewingEvent?.let { viewModel.getPhotosForEvent(it.id) } ?: emptyList() }
    LaunchedEffect(editingEvent) { editingPhotos = editingEvent?.let { viewModel.getPhotosForEvent(it.id) } ?: emptyList() }

    // 下部リスト OFF 時の日別予定ポップアップ用の状態
    var showDayEventsDialog by remember { mutableStateOf(false) }
    var dayEventsDialogDate by remember { mutableStateOf<LocalDate?>(null) }
    // 今日の日付を 1 度だけ取得しておく
    val today = remember { LocalDate.now() }
    // ショートカットからの「予定追加」要求を監視する
    val requestAddEvent by viewModel.requestAddEvent.collectAsState()
    LaunchedEffect(requestAddEvent) {
        if (requestAddEvent) { viewModel.consumeAddEventRequest(); editingEvent = null; showDatePickerForFAB = true }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (isSearchMode) {
            // 検索モード中は検索結果リストを表示する
            SearchResultsList(query = searchQuery, results = searchResults, isLoading = isSearchLoading, colors = colors, showCalendarName = showCalendarName, onResultSelected = { event -> onSearchResultSelected(event.localStartDate()) })
        } else {
            // 縦スワイプで前後の月へ移動する（選択日の日付は月末にクランプする）
            Column(modifier = Modifier.fillMaxSize().swipeToNavigateCalendar(
                onSwipeUp = { val next = currentMonth.plusMonths(1); viewModel.selectDate(next.atDay(minOf(selectedDate.dayOfMonth, next.lengthOfMonth()))) },
                onSwipeDown = { val prev = currentMonth.minusMonths(1); viewModel.selectDate(prev.atDay(minOf(selectedDate.dayOfMonth, prev.lengthOfMonth()))) },
            )) {
                // 下部リスト表示時はグリッドをやや大きめにする
                val gridWeight = if (showBottomList) 1.2f else 1f
                Box(modifier = Modifier.weight(gridWeight).fillMaxWidth()) {
                    // 月が切り替わったらスライドアニメーションで切替える
                    AnimatedContent(targetState = currentMonth, transitionSpec = { slideVertical(isForward = targetState > initialState) }, modifier = Modifier.fillMaxSize(), label = "monthGridTransition") { month ->
                        MonthGridView(month = month, events = events, selectedDate = selectedDate, today = today, weekStartDay = weekStartDay, dayColors = dayColors, colors = colors,
                            onSelectDate = { date -> viewModel.selectDate(date); if (!showBottomList) { dayEventsDialogDate = date; showDayEventsDialog = true } })
                    }
                }
                // 下部リスト ON 時は選択日の予定一覧を表示する
                if (showBottomList) {
                    HorizontalDivider(thickness = 1.dp, color = colors.divider)
                    Column(modifier = Modifier.weight(0.8f).fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                        val jpDayOfWeek = getJpDayOfWeek(selectedDate.dayOfWeek)
                        val dailyEvents = events.filter { it.occursOn(selectedDate) }
                        // 選択日の日付ヘッダーと件数
                        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(bottom = 12.dp, top = 8.dp)) {
                            val c = dayColors[selectedDate.dayOfWeek] ?: Color.Unspecified
                            val finalBottomColor = if (c == Color.Unspecified) colors.text else c
                            Text("${selectedDate.monthValue}月${selectedDate.dayOfMonth}日 ($jpDayOfWeek)", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = finalBottomColor)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("${dailyEvents.size}件", fontSize = 14.sp, color = colors.textGray)
                        }
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            // 予定なし or 予定カード一覧
                            if (dailyEvents.isEmpty()) { item { Text("予定なし", color = colors.textGray, modifier = Modifier.padding(top = 8.dp)) } }
                            else { items(dailyEvents) { event: Event -> EventCard(event = event, colors = colors, showCalendarName = showCalendarName, onClick = { ev -> viewingEvent = ev; viewingDate = selectedDate; showEventDetailDialog = true }) } }
                            // 下部リストから予定を追加するボタン
                            item { Button(onClick = { editingEvent = null; fromCalendar = true; showEventDialog = true }, modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), colors = ButtonDefaults.buttonColors(containerColor = colors.primaryAccent.copy(alpha = 0.15f), contentColor = colors.primaryAccent), shape = RoundedCornerShape(12.dp)) { Text("+ 予定を追加", fontSize = 16.sp, fontWeight = FontWeight.Medium) } }
                        }
                    }
                }
            }
            // 予定追加用 FAB（日付選択ダイアログを開く）
            FloatingActionButton(onClick = { editingEvent = null; showDatePickerForFAB = true }, containerColor = colors.primaryAccent, contentColor = Color.White, modifier = Modifier.align(Alignment.BottomEnd).padding(end = 28.dp, bottom = 72.dp)) { Text("+", fontSize = 24.sp) }
        }
    }

    // FAB 用の日付選択ダイアログ
    if (showDatePickerForFAB) { GolendarDatePickerDialog(initialDate = selectedDate, colors = colors, onDismiss = { showDatePickerForFAB = false }, onDateSelected = { date -> tempFABDate = date; fromCalendar = true; showDatePickerForFAB = false; showEventDialog = true }) }
    // 下部リスト OFF 時の日別予定ポップアップ
    if (showDayEventsDialog && dayEventsDialogDate != null) {
        val dialogDate = dayEventsDialogDate!!
        DayEventsDialog(date = dialogDate, events = events.filter { it.occursOn(dialogDate) }, colors = colors, showCalendarName = showCalendarName,
            onDismiss = { showDayEventsDialog = false; dayEventsDialogDate = null },
            onEventClick = { ev -> showDayEventsDialog = false; viewingEvent = ev; viewingDate = dialogDate; showEventDetailDialog = true },
            onAddEvent = { showDayEventsDialog = false; editingEvent = null; tempFABDate = dialogDate; fromCalendar = true; showEventDialog = true })
    }
    // 予定詳細ダイアログ
    if (showEventDetailDialog && viewingEvent != null && viewingDate != null) {
        EventDetailDialog(event = viewingEvent!!, currentDate = viewingDate!!, colors = colors, photos = detailPhotos,
            onDismiss = { showEventDetailDialog = false; viewingEvent = null; viewingDate = null },
            onEdit = { showEventDetailDialog = false; editingPhotos = emptyList(); editingEvent = viewingEvent; fromCalendar = false; showEventDialog = true },
            onSplitDelete = { viewModel.splitAndDeleteDay(viewingEvent!!, viewingDate!!); showEventDetailDialog = false; viewingEvent = null; viewingDate = null })
    }
    // 予定の追加・編集ダイアログ
    if (showEventDialog) {
        // 既存予定の終日有無で初期タイムゾーンを切り替える
        val zone = editingEvent?.let { if (it.isAllDay) ZoneOffset.UTC else ZoneId.systemDefault() } ?: ZoneId.systemDefault()
        // 編集対象 > FAB で選んだ日付 > 選択日 の優先順で対象日を決定する
        val dialogDate = editingEvent?.let { Instant.ofEpochMilli(it.startTime).atZone(zone).toLocalDate() } ?: tempFABDate ?: selectedDate
        EventDialog(event = editingEvent, selectedDate = dialogDate, colors = colors, fromCalendar = fromCalendar, initialPhotos = editingPhotos,
            photoAttachEnabled = viewModel.calendarMode.collectAsState().value == CalendarMode.GOLENDAR,
            aiParseEnabled = viewModel.calendarMode.collectAsState().value == CalendarMode.GOLENDAR,
            onAiParse = { showAiParseDialog = true }, onDismiss = { showEventDialog = false; tempFABDate = null },
            onSave = { title, startMillis, endMillis, isAllDay, location, description, rrule, newPhotoUris, keptPhotoIds ->
                // 新規は addEvent、既存は updateEvent で保存する
                if (editingEvent == null) viewModel.addEvent(title, startMillis, endMillis, isAllDay, location, description, rrule, newPhotoUris = newPhotoUris)
                else viewModel.updateEvent(editingEvent!!.id, title, startMillis, endMillis, isAllDay, location, description, rrule, newPhotoUris = newPhotoUris, keptPhotoIds = keptPhotoIds)
                showEventDialog = false; tempFABDate = null
            },
            onDelete = { ev -> viewModel.deleteEvent(ev.id); showEventDialog = false; tempFABDate = null })
    }
    // AI 解析ダイアログ
    if (showAiParseDialog) {
        AiParseDialog(colors = colors, needsSetup = !aiSetupDone, onSetupComplete = { viewModel.markAiSetupDone() }, onDismiss = { showAiParseDialog = false },
            onConfirm = { events -> viewModel.addEventsFromAi(events); showAiParseDialog = false; showEventDialog = false })
    }
}

// 対象月のカレンダーグリッド（曜日ヘッダーと日付セル）を描画する
@Composable
private fun MonthGridView(month: YearMonth, events: List<Event>, selectedDate: LocalDate, today: LocalDate, weekStartDay: DayOfWeek, dayColors: Map<DayOfWeek, Color>, colors: AppColors, onSelectDate: (LocalDate) -> Unit) {
    // 曜日文字列から DayOfWeek への変換テーブル
    val stringToDayOfWeek = mapOf("日" to DayOfWeek.SUNDAY, "月" to DayOfWeek.MONDAY, "火" to DayOfWeek.TUESDAY, "水" to DayOfWeek.WEDNESDAY, "木" to DayOfWeek.THURSDAY, "金" to DayOfWeek.FRIDAY, "土" to DayOfWeek.SATURDAY)
    Column(modifier = Modifier.fillMaxSize()) {
        // 週の開始曜日に合わせて曜日ヘッダーを並び替える
        val allDays = listOf("日", "月", "火", "水", "木", "金", "土")
        val startIndex = if (weekStartDay == DayOfWeek.MONDAY) 1 else 0
        val orderedWeekDays = allDays.drop(startIndex) + allDays.take(startIndex)
        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
            orderedWeekDays.forEach { dayString ->
                val dayEnum = stringToDayOfWeek[dayString]!!
                val c = dayColors[dayEnum] ?: Color.Unspecified
                val finalColor = if (c == Color.Unspecified) colors.text else c
                Text(dayString, modifier = Modifier.weight(1f), textAlign = TextAlign.Center, color = finalColor, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }
        // 月初の曜日オフセットと週数を計算する
        val firstDayOfMonth = month.atDay(1)
        val offset = (firstDayOfMonth.dayOfWeek.value - weekStartDay.value + 7) % 7
        val daysInMonth = month.lengthOfMonth()
        val totalCells = ((daysInMonth + offset + 6) / 7) * 7
        val numRows = totalCells / 7
        Column(modifier = Modifier.fillMaxSize()) {
            // 行ごとに 7 日分のセルを並べる
            repeat(numRows) { rowIndex ->
                Row(modifier = Modifier.fillMaxWidth().weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    repeat(7) { colIndex ->
                        val index = rowIndex * 7 + colIndex
                        val dayOffset = index - offset
                        // 前月・当月・翌月に応じて実際の日付を算出する
                        val date = when {
                            dayOffset < 0 -> month.minusMonths(1).atEndOfMonth().plusDays((dayOffset + 1).toLong())
                            dayOffset < daysInMonth -> month.atDay(dayOffset + 1)
                            else -> month.plusMonths(1).atDay(dayOffset - daysInMonth + 1)
                        }
                        val isCurrentMonth = date.month == month.month
                        val isToday = date == today
                        val dailyEvents = events.filter { it.occursOn(date) }
                        val c = dayColors[date.dayOfWeek] ?: Color.Unspecified
                        // 1 日分のセル（日付＋予定タイトル）を描画する
                        CalendarCell(date = date, events = dailyEvents, isSelected = selectedDate == date, isCurrentMonth = isCurrentMonth, isToday = isToday, dayColor = c, colors = colors, modifier = Modifier.weight(1f).fillMaxHeight(), onClick = { onSelectDate(date) })
                    }
                }
            }
        }
    }
}