package com.monsivamon.golender.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
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
import com.monsivamon.golender.ui.common.swipeToNavigate
import com.monsivamon.golender.ui.components.CalendarCell
import com.monsivamon.golender.ui.dialogs.EventDetailDialog
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
) {
    val currentMonth by viewModel.currentMonth.collectAsState()
    val selectedDate by viewModel.selectedDate.collectAsState()
    val events by viewModel.events.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val weekStartDay by viewModel.weekStartDay.collectAsState()
    val dayColors by viewModel.dayColors.collectAsState()
    val customBg by viewModel.calendarBgColor.collectAsState()
    val colors = getAppColors(themeMode, customBg)

    var showEventDialog by remember { mutableStateOf(false) }
    var editingEvent by remember { mutableStateOf<Event?>(null) }
    var showDatePickerForFAB by remember { mutableStateOf(false) }
    var tempFABDate by remember { mutableStateOf<LocalDate?>(null) }
    var showEventDetailDialog by remember { mutableStateOf(false) }
    var viewingEvent by remember { mutableStateOf<Event?>(null) }
    var viewingDate by remember { mutableStateOf<LocalDate?>(null) }

    val today = remember { LocalDate.now() }

    Box(modifier = Modifier.fillMaxSize()) {
        if (isSearchMode && searchQuery.isNotBlank()) {
            val filteredEvents = events.filter { it.title.contains(searchQuery, ignoreCase = true) }
            LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                item {
                    Text("検索結果: ${filteredEvents.size}件", color = colors.textGray,
                        modifier = Modifier.padding(bottom = 8.dp))
                }
                items(filteredEvents) { event: Event ->
                    SearchResultCard(event = event, colors = colors, onClick = { ev ->
                        viewingEvent = ev
                        viewingDate = ev.localStartDate()
                        showEventDetailDialog = true
                    })
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .swipeToNavigate(
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
                AnimatedContent(
                    targetState = currentMonth,
                    transitionSpec = { slideVertical(isForward = targetState > initialState) },
                    modifier = Modifier.weight(1.2f),
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
                        onSelectDate = { viewModel.selectDate(it) },
                    )
                }

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
                                onClick = { editingEvent = null; showEventDialog = true },
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
            onClick = { editingEvent = null; showDatePickerForFAB = true },
            containerColor = colors.primaryAccent,
            contentColor = Color.White,
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 28.dp, bottom = 72.dp),
        ) { Text("+", fontSize = 24.sp) }
    }

    if (showDatePickerForFAB) {
        GolendarDatePickerDialog(
            colors = colors,
            onDismiss = { showDatePickerForFAB = false },
            onDateSelected = { date ->
                tempFABDate = date
                showDatePickerForFAB = false
                showEventDialog = true
            },
        )
    }

    if (showEventDetailDialog && viewingEvent != null && viewingDate != null) {
        EventDetailDialog(
            event = viewingEvent!!, currentDate = viewingDate!!, colors = colors,
            onDismiss = { showEventDetailDialog = false; viewingEvent = null; viewingDate = null },
            onEdit = { showEventDetailDialog = false; editingEvent = viewingEvent; showEventDialog = true },
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
            event = editingEvent, selectedDate = dialogDate, colors = colors,
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

        LazyVerticalGrid(
            columns = GridCells.Fixed(7),
            modifier = Modifier.fillMaxSize(),
            userScrollEnabled = false,
        ) {
            items(count = totalCells) { index: Int ->
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
                    onClick = { onSelectDate(date) },
                )
            }
        }
    }
}