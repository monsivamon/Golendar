package com.monsivamon.golender.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import com.monsivamon.golender.data.EventPhoto
import com.monsivamon.golender.viewmodel.CalendarMode
import com.monsivamon.golender.R
import com.monsivamon.golender.data.util.dayShort
import com.monsivamon.golender.data.util.localStartDate
import com.monsivamon.golender.data.util.occursOn
import com.monsivamon.golender.ui.common.GolendarDatePickerDialog
import com.monsivamon.golender.ui.common.slideVertical
import com.monsivamon.golender.ui.common.swipeToNavigateCalendar
import com.monsivamon.golender.ui.components.SearchResultsList
import com.monsivamon.golender.ui.dialogs.DayEventsDialog
import com.monsivamon.golender.ui.dialogs.EventDetailDialog
import com.monsivamon.golender.ui.dialogs.EventDialog
import com.monsivamon.golender.ui.theme.getAppColors
import com.monsivamon.golender.viewmodel.CalendarViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import com.monsivamon.golender.ui.dialogs.AiParseDialog
import com.monsivamon.golender.ui.components.MonthGridView

// 月間カレンダー画面（月グリッドと選択日の予定一覧・検索・追加編集を扱う）
@Composable
fun MonthlyCalendarScreen(
    viewModel: CalendarViewModel,
    navController: NavController,
    isSearchMode: Boolean,
    onSearchResultSelected: (LocalDate) -> Unit = {},
) {
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
    var showDatePickerForFAB by remember { mutableStateOf(false) }
    var tempFABDate by remember { mutableStateOf<LocalDate?>(null) }
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
        // DB 読み込みが一時的に失敗しただけで「写真0枚」と確定させない。
        // 空リストのまま確定すると保存時に既存写真がすべて消去されるため、
        // 数回リトライし、それでも失敗したらローディングのまま（保存不可）にして
        // 誤データでの保存を防ぐ。
        var loaded = false
        for (attempt in 1..3) {
            try {
                editingPhotos = viewModel.getPhotosForEvent(ev.id)
                loaded = true
                break
            } catch (_: Exception) {
                if (attempt < 3) kotlinx.coroutines.delay(300L * attempt)
            }
        }
        editingPhotosLoaded = loaded
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

    var showDayEventsDialog by remember { mutableStateOf(false) }
    var dayEventsDialogDate by remember { mutableStateOf<LocalDate?>(null) }
    val today = remember { LocalDate.now() }

    val requestAddEvent by viewModel.requestAddEvent.collectAsState()
    LaunchedEffect(requestAddEvent) {
        if (requestAddEvent) {
            viewModel.consumeAddEventRequest()
            editingEvent = null; editingPhotos = emptyList(); editingPhotosLoaded = true
            showDatePickerForFAB = true
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (isSearchMode) {
            SearchResultsList(
                query = searchQuery, results = searchResults, isLoading = isSearchLoading,
                colors = colors, showCalendarName = showCalendarName,
                onResultSelected = { event -> onSearchResultSelected(event.localStartDate()) },
            )
        } else {
            Column(modifier = Modifier.fillMaxSize().swipeToNavigateCalendar(
                onSwipeUp = {
                    val next = currentMonth.plusMonths(1)
                    viewModel.selectDate(next.atDay(minOf(selectedDate.dayOfMonth, next.lengthOfMonth())))
                },
                onSwipeDown = {
                    val prev = currentMonth.minusMonths(1)
                    viewModel.selectDate(prev.atDay(minOf(selectedDate.dayOfMonth, prev.lengthOfMonth())))
                },
            )) {
                val gridWeight = if (showBottomList) 1.2f else 1f
                Box(modifier = Modifier.weight(gridWeight).fillMaxWidth()) {
                    AnimatedContent(
                        targetState = currentMonth,
                        transitionSpec = { slideVertical(isForward = targetState > initialState) },
                        modifier = Modifier.fillMaxSize(), label = "monthGridTransition",
                    ) { month ->
                        MonthGridView(
                            month = month, events = events, selectedDate = selectedDate, today = today,
                            weekStartDay = weekStartDay, dayColors = dayColors, colors = colors,
                            // 下部リスト表示中はグリッドが狭いので 3 件、非表示時は 6 件まで表示
                            maxEventsPerCell = if (showBottomList) 3 else 6,
                            onSelectDate = { date ->
                                viewModel.selectDate(date)
                                if (!showBottomList) {
                                    dayEventsDialogDate = date
                                    showDayEventsDialog = true
                                }
                            },
                        )
                    }
                }
                if (showBottomList) {
                    HorizontalDivider(thickness = 1.dp, color = colors.divider)
                    Column(modifier = Modifier.weight(0.8f).fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                        val jpDayOfWeek = selectedDate.dayOfWeek.dayShort(LocalContext.current)
                        val dailyEvents = events.filter { it.occursOn(selectedDate) }
                        // 予定が 3 件以上あるときは「一覧に収まりきらない」ことがあるため、
                        // ヘッダーで強調し、リスト下端にもスクロールヒントを出す。
                        val hasOverflow = dailyEvents.size >= 3
                        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(bottom = 12.dp, top = 8.dp)) {
                            val c = dayColors[selectedDate.dayOfWeek] ?: Color.Unspecified
                            val finalBottomColor = if (c == Color.Unspecified) colors.text else c
                            Text(stringResource(R.string.sc_dateWithCount, selectedDate.monthValue, selectedDate.dayOfMonth, jpDayOfWeek),
                                fontSize = 18.sp, fontWeight = FontWeight.Bold, color = finalBottomColor)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                stringResource(R.string.sc_eventCount, dailyEvents.size),
                                fontSize = 14.sp,
                                color = if (hasOverflow) colors.primaryAccent else colors.textGray,
                                fontWeight = if (hasOverflow) FontWeight.Bold else FontWeight.Normal,
                            )
                            if (hasOverflow) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("▼", fontSize = 12.sp, color = colors.primaryAccent, fontWeight = FontWeight.Bold)
                            }
                        }
                        // リストのスクロール状態を保持し、下に未表示アイテムがあるかを判定する
                        val bottomListState = rememberLazyListState()
                        val hasMoreBelow by remember {
                            derivedStateOf {
                                val info = bottomListState.layoutInfo
                                val visible = info.visibleItemsInfo
                                if (visible.isEmpty()) false
                                else visible.last().index < info.totalItemsCount - 1
                            }
                        }
                        Box(modifier = Modifier.fillMaxSize()) {
                            LazyColumn(state = bottomListState, modifier = Modifier.fillMaxSize()) {
                                if (dailyEvents.isEmpty()) {
                                    item { Text(stringResource(R.string.sc_noEvents), color = colors.textGray, modifier = Modifier.padding(top = 8.dp)) }
                                } else {
                                    items(dailyEvents) { event: Event ->
                                        EventCard(
                                            event = event, colors = colors, showCalendarName = showCalendarName,
                                            photoCount = eventPhotoCounts[event.id] ?: 0,
                                            onClick = { ev -> viewingEvent = ev; viewingDate = selectedDate; showEventDetailDialog = true },
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
                                    ) { Text(stringResource(R.string.sc_addEvent), fontSize = 16.sp, fontWeight = FontWeight.Medium) }
                                }
                            }
                            // スクロール可能な未表示アイテムがあるときのみ下端 ▼ を表示する。
                            // 下端まで到達したら非表示になり、リストが伸びきっている場合は
                            // そもそも表示されない。
                            if (hasMoreBelow) {
                                Text(
                                    "▼",
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .padding(bottom = 2.dp),
                                    color = colors.primaryAccent,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                }
            }
            FloatingActionButton(
                onClick = { editingEvent = null; editingPhotos = emptyList(); editingPhotosLoaded = true; showDatePickerForFAB = true },
                containerColor = colors.primaryAccent, contentColor = Color.White,
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 28.dp, bottom = 72.dp),
            ) { Text("+", fontSize = 24.sp) }
        }
    }

    if (showDatePickerForFAB) {
        GolendarDatePickerDialog(
            initialDate = selectedDate, colors = colors,
            onDismiss = { showDatePickerForFAB = false },
            onDateSelected = { date ->
                tempFABDate = date; fromCalendar = true
                showDatePickerForFAB = false; showEventDialog = true
            },
        )
    }
    if (showDayEventsDialog && dayEventsDialogDate != null) {
        val dialogDate = dayEventsDialogDate!!
        DayEventsDialog(
            date = dialogDate, events = events.filter { it.occursOn(dialogDate) },
            colors = colors, showCalendarName = showCalendarName,
            onDismiss = { showDayEventsDialog = false; dayEventsDialogDate = null },
            onEventClick = { ev ->
                showDayEventsDialog = false; viewingEvent = ev
                viewingDate = dialogDate; showEventDetailDialog = true
            },
            onAddEvent = {
                showDayEventsDialog = false; editingEvent = null; editingPhotos = emptyList(); editingPhotosLoaded = true
                tempFABDate = dialogDate; fromCalendar = true; showEventDialog = true
            },
        )
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
        val dialogDate = editingEvent?.let { Instant.ofEpochMilli(it.startTime).atZone(zone).toLocalDate() }
            ?: tempFABDate ?: selectedDate
        EventDialog(
            event = editingEvent, selectedDate = dialogDate, colors = colors,
            fromCalendar = fromCalendar, initialPhotos = editingPhotos,
            photoAttachEnabled = calendarMode == CalendarMode.GOLENDAR,
            aiParseEnabled = calendarMode == CalendarMode.GOLENDAR,
            availableCalendars = if (calendarMode == CalendarMode.GOOGLE) availableCalendars else emptyList(),
            localCalendars = if (calendarMode == CalendarMode.GOLENDAR) localCalendars else emptyList(),
            onAiParse = { calId -> aiTargetCalendarId = calId; showAiParseDialog = true },
            onDismiss = { showEventDialog = false; tempFABDate = null },
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
                showEventDialog = false; tempFABDate = null
            },
            onDelete = { ev -> viewModel.deleteEvent(ev.id); showEventDialog = false; tempFABDate = null },
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