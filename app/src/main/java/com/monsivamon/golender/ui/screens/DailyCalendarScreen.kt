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
import com.monsivamon.golender.data.util.getJpDayOfWeek
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

// 日間カレンダー画面を表示し、選択日の予定一覧と追加・編集・詳細を扱う。
// 上下スワイプ（2回連続）で前日/翌日へ移動、切替時は上下スライドアニメーション。
@Composable
fun DailyCalendarScreen(
    viewModel: CalendarViewModel,
    navController: NavController,
    isSearchMode: Boolean,
) {
    val selectedDate by viewModel.selectedDate.collectAsState()
    val events by viewModel.events.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val dayColors by viewModel.dayColors.collectAsState()
    val customBg by viewModel.calendarBgColor.collectAsState()
    val colors = getAppColors(themeMode, customBg)

    var showEventDialog by remember { mutableStateOf(false) }
    var editingEvent by remember { mutableStateOf<Event?>(null) }
    var showEventDetailDialog by remember { mutableStateOf(false) }
    var viewingEvent by remember { mutableStateOf<Event?>(null) }
    var viewingDate by remember { mutableStateOf<LocalDate?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            // 上下スワイプ（2回連続）で前後の日へ移動
            .swipeToNavigateCalendar(
                onSwipeUp = { viewModel.selectDate(selectedDate.plusDays(1)) },
                onSwipeDown = { viewModel.selectDate(selectedDate.minusDays(1)) },
            )
    ) {
        AnimatedContent(
            targetState = selectedDate,
            transitionSpec = { slideVertical(isForward = targetState > initialState) },
            modifier = Modifier.fillMaxSize(),
            label = "dayTransition",
        ) { date ->
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                val dailyEvents = events.filter { it.occursOn(date) }
                val filteredEvents = if (searchQuery.isNotBlank()) {
                    dailyEvents.filter { it.title.contains(searchQuery, ignoreCase = true) }
                } else dailyEvents

                Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(bottom = 12.dp)) {
                    val c = dayColors[date.dayOfWeek] ?: Color.Unspecified
                    val dayColor = if (c == Color.Unspecified) colors.text else c
                    Text(
                        "${date.monthValue}月${date.dayOfMonth}日 (${getJpDayOfWeek(date.dayOfWeek)})",
                        fontSize = 18.sp, fontWeight = FontWeight.Bold, color = dayColor
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    if (isSearchMode && searchQuery.isNotBlank())
                        Text("検索結果: ${filteredEvents.size}件", fontSize = 14.sp, color = colors.primaryAccent)
                    else
                        Text("${filteredEvents.size}件", fontSize = 14.sp, color = colors.textGray)
                }

                LazyColumn {
                    if (filteredEvents.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier.fillMaxWidth()
                                    .clickable { editingEvent = null; showEventDialog = true }
                                    .padding(vertical = 16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    if (isSearchMode && searchQuery.isNotBlank()) "該当する予定はありません" else "予定なし",
                                    color = colors.textGray, fontSize = 16.sp
                                )
                            }
                        }
                    } else {
                        items(filteredEvents) { event: Event ->
                            if (isSearchMode && searchQuery.isNotBlank())
                                SearchResultCard(event = event, colors = colors, onClick = { ev ->
                                    viewingEvent = ev; viewingDate = date; showEventDetailDialog = true
                                })
                            else
                                EventCard(event = event, colors = colors, onClick = { ev ->
                                    viewingEvent = ev; viewingDate = date; showEventDetailDialog = true
                                })
                        }
                    }

                    item {
                        Button(
                            onClick = { editingEvent = null; showEventDialog = true },
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

    if (showEventDetailDialog && viewingEvent != null && viewingDate != null) {
        EventDetailDialog(
            event = viewingEvent!!, currentDate = viewingDate!!, colors = colors,
            onDismiss = { showEventDetailDialog = false; viewingEvent = null; viewingDate = null },
            onEdit = { showEventDetailDialog = false; editingEvent = viewingEvent; showEventDialog = true },
            onSplitDelete = {
                viewModel.splitAndDeleteDay(viewingEvent!!, viewingDate!!)
                showEventDetailDialog = false; viewingEvent = null; viewingDate = null
            }
        )
    }

    if (showEventDialog) {
        val zone = editingEvent?.let { if (it.isAllDay) ZoneOffset.UTC else ZoneId.systemDefault() } ?: ZoneId.systemDefault()
        val dialogDate = editingEvent?.let { Instant.ofEpochMilli(it.startTime).atZone(zone).toLocalDate() } ?: selectedDate

        EventDialog(
            event = editingEvent, selectedDate = dialogDate, colors = colors,
            onDismiss = { showEventDialog = false },
            onSave = { title, startMillis, endMillis, isAllDay, location, description, rrule ->
                if (editingEvent == null) viewModel.addEvent(title, startMillis, endMillis, isAllDay, location, description, rrule)
                else viewModel.updateEvent(editingEvent!!.id, title, startMillis, endMillis, isAllDay, location, description, rrule)
                showEventDialog = false
            },
            onDelete = { ev -> viewModel.deleteEvent(ev.id); showEventDialog = false }
        )
    }
}