package com.monsivamon.golender.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.monsivamon.golender.data.Event
import com.monsivamon.golender.data.EventPhoto
import com.monsivamon.golender.viewmodel.CalendarMode
import com.monsivamon.golender.data.util.getJpDayOfWeek
import com.monsivamon.golender.data.util.localEndDate
import com.monsivamon.golender.data.util.localStartDate
import com.monsivamon.golender.data.util.occursOn
import com.monsivamon.golender.ui.common.slideVertical
import com.monsivamon.golender.ui.common.swipeToNavigateCalendar
import com.monsivamon.golender.ui.components.SearchResultsList
import com.monsivamon.golender.ui.dialogs.EventDetailDialog
import com.monsivamon.golender.ui.dialogs.EventDialog
import com.monsivamon.golender.ui.theme.getAppColors
import com.monsivamon.golender.viewmodel.CalendarViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import com.monsivamon.golender.ui.dialogs.AiParseDialog
import com.monsivamon.golender.data.LocalEvent

// 週間カレンダー画面（週の日付ごとの予定一覧と追加・編集・詳細を扱う）
@Composable
fun WeeklyCalendarScreen(
    viewModel: CalendarViewModel,
    navController: NavController,
    isSearchMode: Boolean,
    onSearchResultSelected: (LocalDate) -> Unit = {},
) {
    // ViewModelから各種状態を購読する
    val selectedDate by viewModel.selectedDate.collectAsState()
    val events by viewModel.events.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val isSearchLoading by viewModel.isSearchLoading.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val weekStartDay by viewModel.weekStartDay.collectAsState()
    val dayColors by viewModel.dayColors.collectAsState()
    val customBg by viewModel.calendarBgColor.collectAsState()
    val colors = getAppColors(themeMode, customBg)
    // ダイアログ表示や選択中イベントのUI状態
    var showEventDialog by remember { mutableStateOf(false) }
    var showAiParseDialog by remember { mutableStateOf(false) }
    var editingEvent by remember { mutableStateOf<Event?>(null) }
    var dialogDateForNewEvent by remember { mutableStateOf<LocalDate?>(null) }
    var showEventDetailDialog by remember { mutableStateOf(false) }
    var viewingEvent by remember { mutableStateOf<Event?>(null) }
    var viewingDate by remember { mutableStateOf<LocalDate?>(null) }
    var fromCalendar by remember { mutableStateOf(false) }
    var editingPhotos by remember { mutableStateOf<List<EventPhoto>>(emptyList()) }
    var detailPhotos by remember { mutableStateOf<List<EventPhoto>>(emptyList()) }
    // AI解析の初回説明完了フラグを購読する
    val aiSetupDone by viewModel.aiSetupDone.collectAsState()
    // 閲覧対象が変わったら添付写真を読み込む
    LaunchedEffect(viewingEvent) {
        detailPhotos = viewingEvent?.let { viewModel.getPhotosForEvent(it.id) } ?: emptyList()
    }
    // 編集対象が変わったら添付写真を読み込む
    LaunchedEffect(editingEvent) {
        editingPhotos = editingEvent?.let { viewModel.getPhotosForEvent(it.id) } ?: emptyList()
    }
    // 検索モード中は結果リストを表示して終了する
    if (isSearchMode) {
        SearchResultsList(
            query = searchQuery,
            results = searchResults,
            isLoading = isSearchLoading,
            colors = colors,
            onResultSelected = { event ->
                onSearchResultSelected(event.localStartDate())
            },
        )
        return
    }
    // 選択日が属する週の開始日を算出する
    val offset = (selectedDate.dayOfWeek.value - weekStartDay.value + 7) % 7
    val startOfWeek = selectedDate.minusDays(offset.toLong())
    // 縦スワイプで前後の週へ移動する
    Box(
        modifier = Modifier
            .fillMaxSize()
            .swipeToNavigateCalendar(
                onSwipeUp = { viewModel.selectDate(selectedDate.plusWeeks(1)) },
                onSwipeDown = { viewModel.selectDate(selectedDate.minusWeeks(1)) },
            )
    ) {
        // 週が切り替わったらスライドアニメーションで切替える
        AnimatedContent(
            targetState = startOfWeek,
            transitionSpec = { slideVertical(isForward = targetState > initialState) },
            modifier = Modifier.fillMaxSize(),
            label = "weekTransition",
        ) { weekStart ->
            val weekEnd = weekStart.plusDays(6)
            // 週の範囲に重なる予定を抽出する
            val weekEvents = events.filter {
                it.localStartDate() <= weekEnd && it.localEndDate() >= weekStart
            }
            LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                val weekDates = (0..6).map { weekStart.plusDays(it.toLong()) }
                weekDates.forEach { date ->
                    item {
                        val dayEvents = weekEvents.filter { it.occursOn(date) }
                        val c = dayColors[date.dayOfWeek] ?: Color.Unspecified
                        val dayColor = if (c == Color.Unspecified) colors.text else c
                        // その日の日付ヘッダーと件数
                        Text(
                            "${date.monthValue}月${date.dayOfMonth}日 (${getJpDayOfWeek(date.dayOfWeek)})  ${dayEvents.size}件",
                            fontSize = 18.sp, fontWeight = FontWeight.Bold, color = dayColor,
                            modifier = Modifier.padding(bottom = 8.dp, top = 16.dp),
                        )
                        // 予定なし or 予定カード一覧
                        if (dayEvents.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxWidth()
                                    .clickable {
                                        editingEvent = null
                                        dialogDateForNewEvent = date
                                        fromCalendar = true
                                        showEventDialog = true
                                    }
                                    .padding(vertical = 8.dp)
                            ) {
                                Text(
                                    "予定なし",
                                    color = colors.textGray, modifier = Modifier.padding(vertical = 4.dp),
                                )
                            }
                        } else {
                            dayEvents.forEach { event: Event ->
                                EventCard(event = event, colors = colors, onClick = { ev ->
                                    viewingEvent = ev; viewingDate = date; showEventDetailDialog = true
                                })
                            }
                        }
                        // 各日付の下に予定追加ボタンを配置する
                        Button(
                            onClick = {
                                editingEvent = null
                                dialogDateForNewEvent = date
                                fromCalendar = true
                                showEventDialog = true
                            },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.primaryAccent.copy(alpha = 0.15f),
                                contentColor = colors.primaryAccent,
                            ),
                            shape = RoundedCornerShape(12.dp),
                        ) { Text("+ 予定を追加", fontSize = 14.sp, fontWeight = FontWeight.Medium) }
                    }
                }
            }
        }
    }
    // 予定詳細ダイアログ
    if (showEventDetailDialog && viewingEvent != null && viewingDate != null) {
        EventDetailDialog(
            event = viewingEvent!!, currentDate = viewingDate!!, colors = colors,
            photos = detailPhotos,
            onDismiss = { showEventDetailDialog = false; viewingEvent = null; viewingDate = null },
            onEdit = {
                showEventDetailDialog = false
                editingPhotos = emptyList()
                editingEvent = viewingEvent
                fromCalendar = false
                showEventDialog = true
            },
            onSplitDelete = {
                viewModel.splitAndDeleteDay(viewingEvent!!, viewingDate!!)
                showEventDetailDialog = false; viewingEvent = null; viewingDate = null
            },
        )
    }
    // 予定の追加・編集ダイアログ
    if (showEventDialog) {
        val zone = editingEvent?.let { if (it.isAllDay) ZoneOffset.UTC else ZoneId.systemDefault() } ?: ZoneId.systemDefault()
        val dialogDate = editingEvent?.let { Instant.ofEpochMilli(it.startTime).atZone(zone).toLocalDate() }
            ?: dialogDateForNewEvent
            ?: selectedDate
        EventDialog(
            event = editingEvent,
            selectedDate = dialogDate,
            colors = colors,
            fromCalendar = fromCalendar,
            initialPhotos = editingPhotos,
            photoAttachEnabled = viewModel.calendarMode.collectAsState().value == CalendarMode.GOLENDAR,
            // Golendarモード時のみAI解析ボタンを表示する
            aiParseEnabled = viewModel.calendarMode.collectAsState().value == CalendarMode.GOLENDAR,
            onAiParse = { showAiParseDialog = true },
            onDismiss = { showEventDialog = false },
            // 新規はaddEvent、既存はupdateEventで保存する
            onSave = { title, startMillis, endMillis, isAllDay, location, description, rrule,
                       newPhotoUris, keptPhotoIds ->
                if (editingEvent == null) viewModel.addEvent(title, startMillis, endMillis, isAllDay, location, description, rrule, newPhotoUris = newPhotoUris)
                else viewModel.updateEvent(editingEvent!!.id, title, startMillis, endMillis, isAllDay, location, description, rrule, newPhotoUris = newPhotoUris, keptPhotoIds = keptPhotoIds)
                showEventDialog = false
            },
            onDelete = { ev -> viewModel.deleteEvent(ev.id); showEventDialog = false },
        )
    }
    // AI解析ダイアログ（初回説明完了フラグを渡す）
    if (showAiParseDialog) {
        AiParseDialog(
            colors = colors,
            needsSetup = !aiSetupDone,
            onSetupComplete = { viewModel.markAiSetupDone() },
            onDismiss = { showAiParseDialog = false },
            onConfirm = { events ->
                viewModel.addEventsFromAi(events)
                showAiParseDialog = false
                showEventDialog = false
            },
        )
    }
}