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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.monsivamon.golender.data.Event
import com.monsivamon.golender.data.prefs.AppTimeZone
import com.monsivamon.golender.data.EventPhoto
import com.monsivamon.golender.viewmodel.CalendarMode
import com.monsivamon.golender.R
import com.monsivamon.golender.data.util.dayShort
import com.monsivamon.golender.data.util.localEndDate
import com.monsivamon.golender.data.util.localStartDate
import com.monsivamon.golender.data.util.occursOn
import com.monsivamon.golender.ui.common.slideVertical
import com.monsivamon.golender.ui.common.swipeToNavigateCalendar
import com.monsivamon.golender.ui.components.SearchResultsList
import com.monsivamon.golender.ui.dialogs.EventDetailDialog
import com.monsivamon.golender.ui.dialogs.EventDialog
import com.monsivamon.golender.ui.events.EventEditorPhotoEffects
import com.monsivamon.golender.ui.theme.getAppColors
import com.monsivamon.golender.viewmodel.CalendarViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import com.monsivamon.golender.ui.dialogs.AiParseDialog

// 週間カレンダー画面（週の日付ごとの予定一覧と追加・編集・詳細を扱う）
@Composable
fun WeeklyCalendarScreen(
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
    val weekStartDay by viewModel.weekStartDay.collectAsState()
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
    var dialogDateForNewEvent by remember { mutableStateOf<LocalDate?>(null) }
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

    // 写真関連の共通エフェクト（詳細/編集の写真ロード、📎枚数マップ）を呼び出す。
    // 3画面の重複ロジックは EventEditorPhotoEffects に集約済み。
    val dataVersion by viewModel.dataVersion.collectAsState()
    EventEditorPhotoEffects(
        viewModel = viewModel,
        viewingEvent = viewingEvent,
        editingEvent = editingEvent,
        editingLoadToken = editingLoadToken,
        dataVersion = dataVersion,
        onDetailPhotosChange = { detailPhotos = it },
        onEditingPhotosChange = { photos, loaded -> editingPhotos = photos; editingPhotosLoaded = loaded },
        onPhotoCountsChange = { eventPhotoCounts = it },
        onEditingEventCleared = { editingPhotos = emptyList(); editingPhotosLoaded = true },
    )

    if (isSearchMode) {
        SearchResultsList(
            query = searchQuery, results = searchResults, isLoading = isSearchLoading,
            colors = colors, showCalendarName = showCalendarName,
            onResultSelected = { event -> onSearchResultSelected(event.localStartDate()) },
        )
        return
    }

    val offset = (selectedDate.dayOfWeek.value - weekStartDay.value + 7) % 7
    val startOfWeek = selectedDate.minusDays(offset.toLong())

    Box(modifier = Modifier.fillMaxSize().swipeToNavigateCalendar(
        onSwipeUp = { viewModel.selectDate(selectedDate.plusWeeks(1)) },
        onSwipeDown = { viewModel.selectDate(selectedDate.minusWeeks(1)) },
    )) {
        AnimatedContent(
            targetState = startOfWeek,
            transitionSpec = { slideVertical(isForward = targetState > initialState) },
            modifier = Modifier.fillMaxSize(), label = "weekTransition",
        ) { weekStart ->
            val weekEnd = weekStart.plusDays(6)
            val weekEvents = events.filter { it.localStartDate() <= weekEnd && it.localEndDate() >= weekStart }
            LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                val weekDates = (0..6).map { weekStart.plusDays(it.toLong()) }
                weekDates.forEach { date ->
                    item {
                        val dayEvents = weekEvents.filter { it.occursOn(date) }
                        val c = dayColors[date.dayOfWeek] ?: Color.Unspecified
                        val dayColor = if (c == Color.Unspecified) colors.text else c
                        Text(
                            stringResource(R.string.sc_weekDateWithCount, date.monthValue, date.dayOfMonth, date.dayOfWeek.dayShort(LocalContext.current), dayEvents.size),
                            fontSize = 18.sp, fontWeight = FontWeight.Bold, color = dayColor,
                            modifier = Modifier.padding(bottom = 8.dp, top = 16.dp),
                        )
                        if (dayEvents.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxWidth()
                                    .clickable {
                                        editingEvent = null; editingPhotos = emptyList(); editingPhotosLoaded = true; dialogDateForNewEvent = date
                                        fromCalendar = true; showEventDialog = true
                                    }
                                    .padding(vertical = 8.dp),
                            ) {
                                Text(stringResource(R.string.sc_noEvents), color = colors.textGray, modifier = Modifier.padding(vertical = 4.dp))
                            }
                        } else {
                            dayEvents.forEach { event: Event ->
                                EventCard(
                                    event = event, colors = colors, showCalendarName = showCalendarName,
                                    photoCount = eventPhotoCounts[event.id] ?: 0,
                                    onClick = { ev -> viewingEvent = ev; viewingDate = date; showEventDetailDialog = true },
                                )
                            }
                        }
                        Button(
                            onClick = {
                                editingEvent = null; editingPhotos = emptyList(); editingPhotosLoaded = true; dialogDateForNewEvent = date
                                fromCalendar = true; showEventDialog = true
                            },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.primaryAccent.copy(alpha = 0.15f),
                                contentColor = colors.primaryAccent,
                            ),
                            shape = RoundedCornerShape(12.dp),
                        ) { Text(stringResource(R.string.sc_addEvent), fontSize = 14.sp, fontWeight = FontWeight.Medium) }
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
        val zone = editingEvent?.let { if (it.isAllDay) ZoneOffset.UTC else AppTimeZone.effective() } ?: AppTimeZone.effective()
        val dialogDate = editingEvent?.let { Instant.ofEpochMilli(it.startTime).atZone(zone).toLocalDate() }
            ?: dialogDateForNewEvent ?: selectedDate
        EventDialog(
            event = editingEvent, selectedDate = dialogDate, colors = colors,
            fromCalendar = fromCalendar, initialPhotos = editingPhotos,
            photoAttachEnabled = calendarMode == CalendarMode.GOLENDAR,
            aiParseEnabled = calendarMode == CalendarMode.GOLENDAR,
            eventColorEnabled = calendarMode == CalendarMode.GOLENDAR,
            availableCalendars = if (calendarMode == CalendarMode.GOOGLE) availableCalendars else emptyList(),
            localCalendars = if (calendarMode == CalendarMode.GOLENDAR) localCalendars else emptyList(),
            onAiParse = { calId -> aiTargetCalendarId = calId; showAiParseDialog = true },
            onDismiss = { showEventDialog = false },
            photosLoaded = editingPhotosLoaded,
            onSave = { title, startMillis, endMillis, isAllDay, location, description, rrule,
                       colorArgb, newPhotoUris, keptPhotoIds, targetCalendarId, localCalendarId ->
                if (editingEvent == null)
                    viewModel.addEvent(title, startMillis, endMillis, isAllDay, location, description, rrule,
                        newPhotoUris = newPhotoUris, targetCalendarId = targetCalendarId,
                        localCalendarId = localCalendarId, colorArgb = colorArgb)
                else
                    viewModel.updateEvent(editingEvent!!.id, title, startMillis, endMillis, isAllDay, location, description, rrule,
                        newPhotoUris = newPhotoUris, keptPhotoIds = keptPhotoIds,
                        localCalendarId = localCalendarId, colorArgb = colorArgb)
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