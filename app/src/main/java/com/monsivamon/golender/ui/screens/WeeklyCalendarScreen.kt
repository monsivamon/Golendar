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
import com.monsivamon.golender.data.util.getJpDayOfWeek
import com.monsivamon.golender.data.util.localEndDate
import com.monsivamon.golender.data.util.localStartDate
import com.monsivamon.golender.data.util.occursOn
import com.monsivamon.golender.ui.common.slideVertical
import com.monsivamon.golender.ui.common.swipeToNavigateCalendar
import com.monsivamon.golender.ui.dialogs.EventDetailDialog
import com.monsivamon.golender.ui.theme.getAppColors
import com.monsivamon.golender.viewmodel.CalendarViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

// 週間カレンダー画面を表示し、週の日付ごとの予定一覧と追加・編集・詳細を扱う。
// 上下スワイプ（2回連続）で前週/翌週へ移動、切替時は上下スライドアニメーション。
@Composable
fun WeeklyCalendarScreen(
    viewModel: CalendarViewModel,
    navController: NavController,
    isSearchMode: Boolean,
) {
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
    var dialogDateForNewEvent by remember { mutableStateOf<LocalDate?>(null) }
    var showEventDetailDialog by remember { mutableStateOf(false) }
    var viewingEvent by remember { mutableStateOf<Event?>(null) }
    var viewingDate by remember { mutableStateOf<LocalDate?>(null) }

    val offset = (selectedDate.dayOfWeek.value - weekStartDay.value + 7) % 7
    val startOfWeek = selectedDate.minusDays(offset.toLong())

    Box(
        modifier = Modifier
            .fillMaxSize()
            // 上下スワイプ（2回連続）で前後の週へ移動
            .swipeToNavigateCalendar(
                onSwipeUp = { viewModel.selectDate(selectedDate.plusWeeks(1)) },
                onSwipeDown = { viewModel.selectDate(selectedDate.minusWeeks(1)) },
            )
    ) {
        AnimatedContent(
            targetState = startOfWeek,
            transitionSpec = { slideVertical(isForward = targetState > initialState) },
            modifier = Modifier.fillMaxSize(),
            label = "weekTransition",
        ) { weekStart ->
            val weekEnd = weekStart.plusDays(6)

            val weekEvents = events.filter {
                it.localStartDate() <= weekEnd && it.localEndDate() >= weekStart
            }
            val filteredEvents = if (searchQuery.isNotBlank()) {
                weekEvents.filter { it.title.contains(searchQuery, ignoreCase = true) }
            } else weekEvents

            LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                val weekDates = (0..6).map { weekStart.plusDays(it.toLong()) }
                weekDates.forEach { date ->
                    item {
                        val dayEvents = filteredEvents.filter { it.occursOn(date) }

                        val c = dayColors[date.dayOfWeek] ?: Color.Unspecified
                        val dayColor = if (c == Color.Unspecified) colors.text else c

                        Text(
                            "${date.monthValue}月${date.dayOfMonth}日 (${getJpDayOfWeek(date.dayOfWeek)})  ${dayEvents.size}件",
                            fontSize = 18.sp, fontWeight = FontWeight.Bold, color = dayColor,
                            modifier = Modifier.padding(bottom = 8.dp, top = 16.dp),
                        )

                        if (dayEvents.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxWidth()
                                    .clickable { editingEvent = null; dialogDateForNewEvent = date; showEventDialog = true }
                                    .padding(vertical = 8.dp)
                            ) {
                                Text(
                                    if (isSearchMode && searchQuery.isNotBlank()) "該当する予定はありません" else "予定なし",
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

                        Button(
                            onClick = { editingEvent = null; dialogDateForNewEvent = date; showEventDialog = true },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.primaryAccent.copy(alpha = 0.15f),
                                contentColor = colors.primaryAccent,
                            ),
                            shape = RoundedCornerShape(12.dp),
                        ) { Text("+ 予定を追加", fontSize = 14.sp, fontWeight = FontWeight.Medium) }
                    }
                }

                if (isSearchMode && searchQuery.isNotBlank() && filteredEvents.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth()
                                .clickable { editingEvent = null; dialogDateForNewEvent = selectedDate; showEventDialog = true }
                                .padding(vertical = 16.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "該当する予定はありません\nタップして予定を追加",
                                color = colors.textGray,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            )
                        }
                    }
                }
            }
        }
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
            ?: dialogDateForNewEvent
            ?: selectedDate

        EventDialog(
            event = editingEvent, selectedDate = dialogDate, colors = colors,
            onDismiss = { showEventDialog = false },
            onSave = { title, startMillis, endMillis, isAllDay, location, description, rrule ->
                if (editingEvent == null) viewModel.addEvent(title, startMillis, endMillis, isAllDay, location, description, rrule)
                else viewModel.updateEvent(editingEvent!!.id, title, startMillis, endMillis, isAllDay, location, description, rrule)
                showEventDialog = false
            },
            onDelete = { ev -> viewModel.deleteEvent(ev.id); showEventDialog = false },
        )
    }
}