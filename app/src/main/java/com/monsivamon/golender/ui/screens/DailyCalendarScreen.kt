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

// 日間カレンダー画面（選択日の予定一覧・追加・編集・詳細を扱う）
@Composable
fun DailyCalendarScreen(
    viewModel: CalendarViewModel,
    navController: NavController,
    isSearchMode: Boolean,
    onSearchResultSelected: (LocalDate) -> Unit = {},
) {
    val selectedDate by viewModel.selectedDate.collectAsState()
    val events by viewModel.events.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val isSearchLoading by viewModel.isSearchLoading.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val dayColors by viewModel.dayColors.collectAsState()
    val customBg by viewModel.calendarBgColor.collectAsState()
    val colors = getAppColors(themeMode, customBg)
    val calendarMode by viewModel.calendarMode.collectAsState()

    val selectedCalendarsState by viewModel.selectedCalendars.collectAsState()
    val localCalendars by viewModel.localCalendars.collectAsState()
    val showCalendarName = if (calendarMode == CalendarMode.GOOGLE)
        selectedCalendarsState.count { it.isVisible } >= 2
    else localCalendars.count { it.isVisible } >= 2

    val availableCalendars = selectedCalendarsState.filter { it.isVisible }

    var showEventDialog by remember { mutableStateOf(false) }
    var showAiParseDialog by remember { mutableStateOf(false) }
    // AI 解析結果の登録先カレンダー ID（EventDialog の LocalCalendarSelector から引き継ぐ）
    var aiTargetCalendarId by remember { mutableStateOf<Long?>(null) }
    var editingEvent by remember { mutableStateOf<Event?>(null) }
    var showEventDetailDialog by remember { mutableStateOf(false) }
    var viewingEvent by remember { mutableStateOf<Event?>(null) }
    var viewingDate by remember { mutableStateOf<LocalDate?>(null) }
    var fromCalendar by remember { mutableStateOf(false) }
    var editingPhotos by remember { mutableStateOf<List<EventPhoto>>(emptyList()) }
    var editingPhotosLoaded by remember { mutableStateOf(true) }
    var editingLoadToken by remember { mutableStateOf(0L) }
    var detailPhotos by remember { mutableStateOf<List<EventPhoto>>(emptyList()) }
    var eventPhotoCounts by remember { mutableStateOf<Map<Long, Int>>(emptyMap()) }
    val aiSetupDone by viewModel.aiSetupDone.collectAsState()

    // 予定編集にまつわる写真関連の状態を一括で初期化する。
    // 個別に null/empty を代入すると消し忘れが発生するため、
    // 「開く前」「閉じた後」は必ずこの関数（または LaunchedEffect 経由）を通す。
    fun resetEventEditorPhotos() {
        editingPhotos = emptyList()
        editingPhotosLoaded = true
    }

    LaunchedEffect(viewingEvent) { detailPhotos = viewingEvent?.let { viewModel.getPhotosForEvent(it.id) } ?: emptyList() }
    LaunchedEffect(editingLoadToken) {
        if (editingLoadToken == 0L) return@LaunchedEffect
        val ev = editingEvent ?: run {
            editingPhotosLoaded = true
            return@LaunchedEffect
        }
        editingPhotosLoaded = false
        editingPhotos = try {
            viewModel.getPhotosForEvent(ev.id)
        } catch (_: Exception) {
            emptyList()
        }
        editingPhotosLoaded = true
    }

    // editingEvent が null に戻った瞬間に写真状態をクリアする。
    // 呼び出し側の「editingEvent = null」忘れ／消し忘れを防ぐセーフティネット。
    // （「+ 予定を追加」を開いたときに前の予定の写真サムネイルが残る不具合の恒久対策）
    LaunchedEffect(editingEvent) {
        if (editingEvent == null) resetEventEditorPhotos()
    }

    // dataVersion が更新されるたびに写真枚数マップを再取得する
    val dataVersion by viewModel.dataVersion.collectAsState()
    LaunchedEffect(dataVersion) {
        val ids = viewModel.events.value.map { it.id }.filter { it > 0 }
        eventPhotoCounts = if (ids.isEmpty()) emptyMap()
                         else viewModel.getPhotoCountsForEvents(ids)
    }

    if (isSearchMode) {
        SearchResultsList(
            query = searchQuery, results = searchResults, isLoading = isSearchLoading,
            colors = colors, showCalendarName = showCalendarName,
            onResultSelected = { event -> onSearchResultSelected(event.localStartDate()) },
        )
        return
    }

    Box(modifier = Modifier.fillMaxSize().swipeToNavigateCalendar(
        onSwipeUp = { viewModel.selectDate(selectedDate.plusDays(1)) },
        onSwipeDown = { viewModel.selectDate(selectedDate.minusDays(1)) },
    )) {
        AnimatedContent(
            targetState = selectedDate,
            transitionSpec = { slideVertical(isForward = targetState > initialState) },
            modifier = Modifier.fillMaxSize(), label = "dayTransition",
        ) { date ->
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                val dailyEvents = events.filter { it.occursOn(date) }
                Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(bottom = 12.dp)) {
                    val c = dayColors[date.dayOfWeek] ?: Color.Unspecified
                    val dayColor = if (c == Color.Unspecified) colors.text else c
                    Text("${date.monthValue}月${date.dayOfMonth}日 (${getJpDayOfWeek(date.dayOfWeek)})",
                        fontSize = 18.sp, fontWeight = FontWeight.Bold, color = dayColor)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("${dailyEvents.size}件", fontSize = 14.sp, color = colors.textGray)
                }
                LazyColumn {
                    if (dailyEvents.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier.fillMaxWidth()
                                    .clickable { editingEvent = null; editingPhotos = emptyList(); editingPhotosLoaded = true; fromCalendar = true; showEventDialog = true }
                                    .padding(vertical = 16.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("予定なし", color = colors.textGray, fontSize = 16.sp)
                            }
                        }
                    } else {
                        items(dailyEvents) { event: Event ->
                            EventCard(
                                event = event, colors = colors, showCalendarName = showCalendarName,
                                photoCount = eventPhotoCounts[event.id] ?: 0,
                                onClick = { ev -> viewingEvent = ev; viewingDate = date; showEventDetailDialog = true },
                            )
                        }
                    }
                    item {
                        Button(
                            onClick = { editingEvent = null; editingPhotos = emptyList(); editingPhotosLoaded = true; fromCalendar = true; showEventDialog = true },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.primaryAccent.copy(alpha = 0.15f),
                                contentColor = colors.primaryAccent,
                            ),
                            shape = RoundedCornerShape(12.dp),
                        ) { Text("+ 予定を追加", fontSize = 16.sp, fontWeight = FontWeight.Medium) }
                    }
                }
            }
        }
    }

    if (showEventDetailDialog && viewingEvent != null && viewingDate != null) {
        EventDetailDialog(
            event = viewingEvent!!, currentDate = viewingDate!!, colors = colors, photos = detailPhotos,
            onDismiss = { showEventDetailDialog = false; viewingEvent = null; viewingDate = null },
            onEdit = {
                showEventDetailDialog = false
                editingPhotos = emptyList()
                editingPhotosLoaded = false
                editingEvent = viewingEvent
                editingLoadToken = editingLoadToken + 1
                fromCalendar = false
                showEventDialog = true
            },
            onSplitDelete = {
                viewModel.splitAndDeleteDay(viewingEvent!!, viewingDate!!)
                showEventDetailDialog = false; viewingEvent = null; viewingDate = null
            },
        )
    }

    if (showEventDialog) {
        val zone = editingEvent?.let { if (it.isAllDay) ZoneOffset.UTC else ZoneId.systemDefault() } ?: ZoneId.systemDefault()
        val dialogDate = editingEvent?.let { Instant.ofEpochMilli(it.startTime).atZone(zone).toLocalDate() } ?: selectedDate
        EventDialog(
            event = editingEvent, selectedDate = dialogDate, colors = colors,
            fromCalendar = fromCalendar, initialPhotos = editingPhotos,
            photoAttachEnabled = calendarMode == CalendarMode.GOLENDAR,
            aiParseEnabled = calendarMode == CalendarMode.GOLENDAR,
            availableCalendars = if (calendarMode == CalendarMode.GOOGLE) availableCalendars else emptyList(),
            localCalendars = if (calendarMode == CalendarMode.GOLENDAR) localCalendars else emptyList(),
            onAiParse = { calId -> aiTargetCalendarId = calId; showAiParseDialog = true },
            onDismiss = { showEventDialog = false },
            photosLoaded = editingPhotosLoaded,
            onSave = { title, startMillis, endMillis, isAllDay, location, description, rrule,
                       newPhotoUris, keptPhotoIds, targetCalendarId, localCalendarId ->
                if (editingEvent == null)
                    viewModel.addEvent(title, startMillis, endMillis, isAllDay, location, description, rrule,
                        newPhotoUris = newPhotoUris, targetCalendarId = targetCalendarId,
                        localCalendarId = localCalendarId)
                else
                    viewModel.updateEvent(editingEvent!!.id, title, startMillis, endMillis, isAllDay, location, description, rrule,
                        newPhotoUris = newPhotoUris, keptPhotoIds = keptPhotoIds,
                        localCalendarId = localCalendarId)
                showEventDialog = false
            },
            onDelete = { ev -> viewModel.deleteEvent(ev.id); showEventDialog = false },
        )
    }

    if (showAiParseDialog) {
        AiParseDialog(
            colors = colors, needsSetup = !aiSetupDone,
            onSetupComplete = { viewModel.markAiSetupDone() },
            onDismiss = { showAiParseDialog = false },
            onConfirm = { aiEvents ->
                viewModel.addEventsFromAi(aiEvents, aiTargetCalendarId)
                showAiParseDialog = false
                showEventDialog = false
                aiTargetCalendarId = null
            },
        )
    }
}