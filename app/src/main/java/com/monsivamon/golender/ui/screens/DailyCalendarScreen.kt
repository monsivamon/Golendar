package com.monsivamon.golender.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.monsivamon.golender.data.Event
import com.monsivamon.golender.data.EventPhoto
import com.monsivamon.golender.viewmodel.CalendarMode
import com.monsivamon.golender.data.util.getJpDayOfWeek
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

// 日間カレンダー画面（選択日の予定一覧・追加・編集・詳細を扱う）
@Composable
fun DailyCalendarScreen(
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
    val dayColors by viewModel.dayColors.collectAsState()
    val customBg by viewModel.calendarBgColor.collectAsState()
    val colors = getAppColors(themeMode, customBg)
    // 予定ダイアログ・詳細ダイアログ・AI解析などのUI状態
    var showEventDialog by remember { mutableStateOf(false) }
    var showAiParseDialog by remember { mutableStateOf(false) }
    var editingEvent by remember { mutableStateOf<Event?>(null) }
    var showEventDetailDialog by remember { mutableStateOf(false) }
    var viewingEvent by remember { mutableStateOf<Event?>(null) }
    var viewingDate by remember { mutableStateOf<LocalDate?>(null) }
    var fromCalendar by remember { mutableStateOf(false) }
    var editingPhotos by remember { mutableStateOf<List<EventPhoto>>(emptyList()) }
    var detailPhotos by remember { mutableStateOf<List<EventPhoto>>(emptyList()) }
    // AI解析の初回説明完了フラグを購読する
    val aiSetupDone by viewModel.aiSetupDone.collectAsState()
    // 閲覧対象の予定が変わったら添付写真を読み込む
    LaunchedEffect(viewingEvent) {
        detailPhotos = viewingEvent?.let { viewModel.getPhotosForEvent(it.id) } ?: emptyList()
    }
    // 編集対象の予定が変わったら添付写真を読み込む
    LaunchedEffect(editingEvent) {
        editingPhotos = editingEvent?.let { viewModel.getPhotosForEvent(it.id) } ?: emptyList()
    }
    // 検索モード中は検索結果リストを表示して終了する
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
    // 縦スワイプで前後の日へ移動するコンテナ
    Box(
        modifier = Modifier
            .fillMaxSize()
            .swipeToNavigateCalendar(
                onSwipeUp = { viewModel.selectDate(selectedDate.plusDays(1)) },
                onSwipeDown = { viewModel.selectDate(selectedDate.minusDays(1)) },
            )
    ) {
        // 選択日が変わるとスライドアニメーションで切替える
        AnimatedContent(
            targetState = selectedDate,
            transitionSpec = { slideVertical(isForward = targetState > initialState) },
            modifier = Modifier.fillMaxSize(),
            label = "dayTransition",
        ) { date ->
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                // 対象日の予定を抽出する
                val dailyEvents = events.filter { it.occursOn(date) }
                // 日付ヘッダーと件数を表示する
                Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(bottom = 12.dp)) {
                    val c = dayColors[date.dayOfWeek] ?: Color.Unspecified
                    val dayColor = if (c == Color.Unspecified) colors.text else c
                    Text(
                        "${date.monthValue}月${date.dayOfMonth}日 (${getJpDayOfWeek(date.dayOfWeek)})",
                        fontSize = 18.sp, fontWeight = FontWeight.Bold, color = dayColor
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("${dailyEvents.size}件", fontSize = 14.sp, color = colors.textGray)
                }
                LazyColumn {
                    // 予定なし時はタップで新規追加できる空状態を表示する
                    if (dailyEvents.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier.fillMaxWidth()
                                    .clickable {
                                        editingEvent = null
                                        fromCalendar = true
                                        showEventDialog = true
                                    }
                                    .padding(vertical = 16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("予定なし", color = colors.textGray, fontSize = 16.sp)
                            }
                        }
                    } else {
                        // 予定をカードで並べ、タップで詳細ダイアログを開く
                        items(dailyEvents) { event: Event ->
                            EventCard(event = event, colors = colors, onClick = { ev ->
                                viewingEvent = ev; viewingDate = date; showEventDetailDialog = true
                            })
                        }
                    }
                    // 画面下部に予定追加ボタンを配置する
                    item {
                        Button(
                            onClick = {
                                editingEvent = null
                                fromCalendar = true
                                showEventDialog = true
                            },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.primaryAccent.copy(alpha = 0.15f),
                                contentColor = colors.primaryAccent
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) { Text("+ 予定を追加", fontSize = 16.sp, fontWeight = FontWeight.Medium) }
                    }
                }
            }
        }
    }
    // 予定詳細ダイアログ（編集・この日だけ削除に対応）
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
            }
        )
    }
    // 予定の追加・編集ダイアログを表示する
    if (showEventDialog) {
        val zone = editingEvent?.let { if (it.isAllDay) ZoneOffset.UTC else ZoneId.systemDefault() } ?: ZoneId.systemDefault()
        val dialogDate = editingEvent?.let { Instant.ofEpochMilli(it.startTime).atZone(zone).toLocalDate() } ?: selectedDate
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
            onDelete = { ev -> viewModel.deleteEvent(ev.id); showEventDialog = false }
        )
    }
    // AI解析ダイアログ（選択されたイベントを一括登録する）
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