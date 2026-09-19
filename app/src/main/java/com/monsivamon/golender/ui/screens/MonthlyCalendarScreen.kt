package com.monsivamon.golender.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.monsivamon.golender.data.Event
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

// 月間カレンダー画面を表示し、月グリッドと選択日の予定一覧・検索・追加編集を扱う。
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

    var showEventDialog by remember { mutableStateOf(false) }
    var editingEvent by remember { mutableStateOf<Event?>(null) }
    var showDatePickerForFAB by remember { mutableStateOf(false) }
    var tempFABDate by remember { mutableStateOf<LocalDate?>(null) }
    var showEventDetailDialog by remember { mutableStateOf(false) }
    var viewingEvent by remember { mutableStateOf<Event?>(null) }
    var viewingDate by remember { mutableStateOf<LocalDate?>(null) }
    var fromCalendar by remember { mutableStateOf(false) }

    var showDayEventsDialog by remember { mutableStateOf(false) }
    var dayEventsDialogDate by remember { mutableStateOf<LocalDate?>(null) }

    val today = remember { LocalDate.now() }

    val requestAddEvent by viewModel.requestAddEvent.collectAsState()
    LaunchedEffect(requestAddEvent) {
        if (requestAddEvent) {
            viewModel.consumeAddEventRequest()
            editingEvent = null
            showDatePickerForFAB = true
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
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
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .swipeToNavigateCalendar(
                        onSwipeUp = {
                            val next = currentMonth.plusMonths(1)
                            val day = minOf(selectedDate.dayOfMonth, next.lengthOfMonth())
                            viewModel.selectDate(next.atDay(day))
                        },
                        onSwipeDown = {
                            val prev = currentMonth.minusMonths(1)
                            val day = minOf(selectedDate.dayOfMonth, prev.lengthOfMonth())
                            viewModel.selectDate(prev.atDay(day))
                        },
                    )
            ) {
                val gridWeight = if (showBottomList) 1.2f else 1f
                Box(modifier = Modifier.weight(gridWeight).fillMaxWidth()) {
                    AnimatedContent(
                        targetState = currentMonth,
                        transitionSpec = { slideVertical(isForward = targetState > initialState) },
                        modifier = Modifier.fillMaxSize(),
                        label = "monthGridTransition",
                    ) { month ->
                        MonthGridView(
                            month = month,
                            events = events,
                            selectedDate = selectedDate,
                            today = today,
                            weekStartDay = weekStartDay,
                            dayColors = dayColors,
                            colors = colors,
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
                        val jpDayOfWeek = getJpDayOfWeek(selectedDate.dayOfWeek)
                        val dailyEvents = events.filter { it.occursOn(selectedDate) }

                        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(bottom = 12.dp, top = 8.dp)) {
                            val c = dayColors[selectedDate.dayOfWeek] ?: Color.Unspecified
                            val finalBottomColor = if (c == Color.Unspecified) colors.text else c
                            Text(
                                "${selectedDate.monthValue}月${selectedDate.dayOfMonth}日 ($jpDayOfWeek)",
                                fontSize = 18.sp, fontWeight = FontWeight.Bold, color = finalBottomColor,
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("${dailyEvents.size}件", fontSize = 14.sp, color = colors.textGray)
                        }

                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            if (dailyEvents.isEmpty()) {
                                item { Text("予定なし", color = colors.textGray, modifier = Modifier.padding(top = 8.dp)) }
                            } else {
                                items(dailyEvents) { event: Event ->
                                    EventCard(event = event, colors = colors, onClick = { ev ->
                                        viewingEvent = ev
                                        viewingDate = selectedDate
                                        showEventDetailDialog = true
                                    })
                                }
                            }
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
                                        contentColor = colors.primaryAccent,
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                ) { Text("+ 予定を追加", fontSize = 16.sp, fontWeight = FontWeight.Medium) }
                            }
                        }
                    }
                }
            }

            FloatingActionButton(
                onClick = {
                    editingEvent = null
                    showDatePickerForFAB = true
                },
                containerColor = colors.primaryAccent,
                contentColor = Color.White,
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 28.dp, bottom = 72.dp),
            ) { Text("+", fontSize = 24.sp) }
        }
    }

    if (showDatePickerForFAB) {
        GolendarDatePickerDialog(
            initialDate = selectedDate,
            colors = colors,
            onDismiss = { showDatePickerForFAB = false },
            onDateSelected = { date ->
                tempFABDate = date
                fromCalendar = true
                showDatePickerForFAB = false
                showEventDialog = true
            },
        )
    }

    if (showDayEventsDialog && dayEventsDialogDate != null) {
        val dialogDate = dayEventsDialogDate!!
        val dialogEvents = events.filter { it.occursOn(dialogDate) }
        DayEventsDialog(
            date = dialogDate,
            events = dialogEvents,
            colors = colors,
            onDismiss = {
                showDayEventsDialog = false
                dayEventsDialogDate = null
            },
            onEventClick = { ev ->
                showDayEventsDialog = false
                viewingEvent = ev
                viewingDate = dialogDate
                showEventDetailDialog = true
            },
            onAddEvent = {
                showDayEventsDialog = false
                editingEvent = null
                tempFABDate = dialogDate
                fromCalendar = true
                showEventDialog = true
            },
        )
    }

    if (showEventDetailDialog && viewingEvent != null && viewingDate != null) {
        EventDetailDialog(
            event = viewingEvent!!, currentDate = viewingDate!!, colors = colors,
            onDismiss = { showEventDetailDialog = false; viewingEvent = null; viewingDate = null },
            onEdit = {
                showEventDetailDialog = false
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

    if (showEventDialog) {
        val zone = editingEvent?.let { if (it.isAllDay) ZoneOffset.UTC else ZoneId.systemDefault() } ?: ZoneId.systemDefault()
        val dialogDate = editingEvent?.let { Instant.ofEpochMilli(it.startTime).atZone(zone).toLocalDate() }
            ?: tempFABDate
            ?: selectedDate

        EventDialog(
            event = editingEvent,
            selectedDate = dialogDate,
            colors = colors,
            fromCalendar = fromCalendar,
            onDismiss = { showEventDialog = false; tempFABDate = null },
            onSave = { title, startMillis, endMillis, isAllDay, location, description, rrule ->
                if (editingEvent == null) viewModel.addEvent(title, startMillis, endMillis, isAllDay, location, description, rrule)
                else viewModel.updateEvent(editingEvent!!.id, title, startMillis, endMillis, isAllDay, location, description, rrule)
                showEventDialog = false
                tempFABDate = null
            },
            onDelete = { ev -> viewModel.deleteEvent(ev.id); showEventDialog = false; tempFABDate = null },
        )
    }
}

// 対象月のカレンダーグリッド（曜日ヘッダーと日付セル）を描画する。
@Composable
private fun MonthGridView(
    month: YearMonth,
    events: List<Event>,
    selectedDate: LocalDate,
    today: LocalDate,
    weekStartDay: DayOfWeek,
    dayColors: Map<DayOfWeek, Color>,
    colors: AppColors,
    onSelectDate: (LocalDate) -> Unit,
) {
    val stringToDayOfWeek = mapOf(
        "日" to DayOfWeek.SUNDAY, "月" to DayOfWeek.MONDAY, "火" to DayOfWeek.TUESDAY,
        "水" to DayOfWeek.WEDNESDAY, "木" to DayOfWeek.THURSDAY, "金" to DayOfWeek.FRIDAY, "土" to DayOfWeek.SATURDAY
    )

    Column(modifier = Modifier.fillMaxSize()) {
        val allDays = listOf("日", "月", "火", "水", "木", "金", "土")
        val startIndex = if (weekStartDay == DayOfWeek.MONDAY) 1 else 0
        val orderedWeekDays = allDays.drop(startIndex) + allDays.take(startIndex)

        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
            orderedWeekDays.forEach { dayString ->
                val dayEnum = stringToDayOfWeek[dayString]!!
                val c = dayColors[dayEnum] ?: Color.Unspecified
                val finalColor = if (c == Color.Unspecified) colors.text else c
                Text(
                    dayString, modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center, color = finalColor,
                    fontSize = 14.sp, fontWeight = FontWeight.Bold,
                )
            }
        }

        val firstDayOfMonth = month.atDay(1)
        val offset = (firstDayOfMonth.dayOfWeek.value - weekStartDay.value + 7) % 7
        val daysInMonth = month.lengthOfMonth()
        val totalCells = ((daysInMonth + offset + 6) / 7) * 7
        val numRows = totalCells / 7

        Column(modifier = Modifier.fillMaxSize()) {
            repeat(numRows) { rowIndex ->
                Row(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    repeat(7) { colIndex ->
                        val index = rowIndex * 7 + colIndex
                        val dayOffset = index - offset
                        val date = when {
                            dayOffset < 0 -> month.minusMonths(1).atEndOfMonth().plusDays((dayOffset + 1).toLong())
                            dayOffset < daysInMonth -> month.atDay(dayOffset + 1)
                            else -> month.plusMonths(1).atDay(dayOffset - daysInMonth + 1)
                        }
                        val isCurrentMonth = date.month == month.month
                        val isToday = date == today
                        val dailyEvents = events.filter { it.occursOn(date) }
                        val c = dayColors[date.dayOfWeek] ?: Color.Unspecified

                        CalendarCell(
                            date = date,
                            events = dailyEvents,
                            isSelected = selectedDate == date,
                            isCurrentMonth = isCurrentMonth,
                            isToday = isToday,
                            dayColor = c,
                            colors = colors,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            onClick = { onSelectDate(date) },
                        )
                    }
                }
            }
        }
    }
}