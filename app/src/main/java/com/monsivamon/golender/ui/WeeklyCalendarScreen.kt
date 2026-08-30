package com.monsivamon.golender.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.monsivamon.golender.viewmodel.CalendarViewModel
import com.monsivamon.golender.data.Event
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.DayOfWeek

// 週間カレンダー画面
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeeklyCalendarScreen(viewModel: CalendarViewModel, navController: NavController) {
    val selectedDate by viewModel.selectedDate.collectAsState()
    val events by viewModel.events.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val weekStartDay by viewModel.weekStartDay.collectAsState()
    val dayColors by viewModel.dayColors.collectAsState()
    val customBg by viewModel.calendarBgColor.collectAsState()
    val colors = getAppColors(themeMode, customBg)

    var isSearchMode by remember { mutableStateOf(false) }
    var showSyncDialog by remember { mutableStateOf(false) }
    var showDatePickerDialog by remember { mutableStateOf(false) }

    var showEventDialog by remember { mutableStateOf(false) }
    var editingEvent by remember { mutableStateOf<Event?>(null) }
    var dialogDateForNewEvent by remember { mutableStateOf<LocalDate?>(null) }

    var showEventDetailDialog by remember { mutableStateOf(false) }
    var viewingEvent by remember { mutableStateOf<Event?>(null) }
    var viewingDate by remember { mutableStateOf<LocalDate?>(null) }

    // 週の開始日と終了日を計算
    val offset = (selectedDate.dayOfWeek.value - weekStartDay.value + 7) % 7
    val startOfWeek = selectedDate.minusDays(offset.toLong())
    val endOfWeek = startOfWeek.plusDays(6)

    // 週内のイベントをフィルタ（終日予定はUTC、時間指定はシステムタイムゾーンで判定）
    val weekEvents = events.filter { event ->
        val zone = if (event.isAllDay) ZoneOffset.UTC else ZoneId.systemDefault()
        val eventStart = LocalDateTime.ofInstant(Instant.ofEpochMilli(event.startTime), zone).toLocalDate()
        val adjustedEndTime = if (event.endTime > event.startTime) event.endTime - 1 else event.endTime
        val eventEnd = LocalDateTime.ofInstant(Instant.ofEpochMilli(adjustedEndTime), zone).toLocalDate()
        eventStart <= endOfWeek && eventEnd >= startOfWeek
    }

    val filteredEvents = if (searchQuery.isNotBlank()) {
        weekEvents.filter { it.title.contains(searchQuery, ignoreCase = true) }
    } else {
        weekEvents
    }

    Scaffold(
        containerColor = colors.bg
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {

            // ヘッダー（タイトル＋アイコン群）
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                if (isSearchMode) {
                    // 検索モード
                    Box(modifier = Modifier.weight(1f).height(40.dp).border(1.dp, colors.textGray, RoundedCornerShape(8.dp)).padding(horizontal = 12.dp), contentAlignment = Alignment.CenterStart) {
                        if (searchQuery.isEmpty()) Text("予定を検索...", color = colors.textGray, fontSize = 14.sp)
                        BasicTextField(value = searchQuery, onValueChange = { viewModel.updateSearchQuery(it) }, singleLine = true, textStyle = TextStyle(color = colors.text, fontSize = 14.sp), modifier = Modifier.fillMaxWidth())
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("✕", fontSize = 18.sp, color = colors.textGray, modifier = Modifier.clickable { isSearchMode = false; viewModel.updateSearchQuery("") }.padding(8.dp))
                } else {
                    // 通常表示
                    val title = "${startOfWeek.year}/${startOfWeek.monthValue}/${startOfWeek.dayOfMonth}~${endOfWeek.monthValue}/${endOfWeek.dayOfMonth}"
                    DateTitleWithPicker(title = title, colors = colors, onClick = { showDatePickerDialog = true })
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        // 今日ボタン
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .border(1.5.dp, colors.text, RoundedCornerShape(4.dp))
                                .clickable { navigateToTodayMonth(viewModel, navController) }
                        ) {
                            Column(modifier = Modifier.fillMaxSize()) {
                                Box(modifier = Modifier.fillMaxWidth().height(6.dp).background(colors.sunRed))
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text(text = LocalDate.now().dayOfMonth.toString(), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = colors.text, modifier = Modifier.offset(y = (-1).dp))
                                }
                            }
                        }
                        Text("🔍", fontSize = 20.sp, modifier = Modifier.clickable { isSearchMode = true })
                        Text("🔄", fontSize = 20.sp, modifier = Modifier.clickable { showSyncDialog = true })
                        Text("⚙️", fontSize = 20.sp, modifier = Modifier.clickable { navController.navigate(Routes.SETTINGS) })
                    }
                }
            }

            // タブ切り替え（日・週・月）
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                Text("<", fontSize = 18.sp, color = colors.text, modifier = Modifier.clickable { navigateTab(navController, Routes.WEEKLY, -1) }.padding(8.dp))
                Text("日", fontSize = 16.sp, color = colors.text, modifier = Modifier.clickable { navController.navigate(Routes.DAILY) { launchSingleTop = true } })
                Box(modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(colors.primaryAccent).padding(horizontal = 24.dp, vertical = 6.dp)) { Text("週", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White) }
                Text("月", fontSize = 16.sp, color = colors.text, modifier = Modifier.clickable { navController.navigate(Routes.MONTHLY) { launchSingleTop = true } })
                Text(">", fontSize = 18.sp, color = colors.text, modifier = Modifier.clickable { navigateTab(navController, Routes.WEEKLY, 1) }.padding(8.dp))
            }

            HorizontalDivider(thickness = 1.dp, color = colors.divider)

            // 曜日ごとのイベントリスト
            LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                val weekDates = (0..6).map { startOfWeek.plusDays(it.toLong()) }
                weekDates.forEach { date ->
                    item {
                        val dayEvents = filteredEvents.filter { event ->
                            val zone = if (event.isAllDay) ZoneOffset.UTC else ZoneId.systemDefault()
                            val eventStart = LocalDateTime.ofInstant(Instant.ofEpochMilli(event.startTime), zone).toLocalDate()
                            val adjustedEndTime = if (event.endTime > event.startTime) event.endTime - 1 else event.endTime
                            val eventEnd = LocalDateTime.ofInstant(Instant.ofEpochMilli(adjustedEndTime), zone).toLocalDate()
                            date in eventStart..eventEnd
                        }

                        val c = dayColors[date.dayOfWeek] ?: Color.Unspecified
                        val dayColor = if (c == Color.Unspecified) colors.text else c

                        Text("${date.monthValue}月${date.dayOfMonth}日 (${getJpDayOfWeek(date.dayOfWeek)})  ${dayEvents.size}件", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = dayColor, modifier = Modifier.padding(bottom = 8.dp, top = 16.dp))

                        if (dayEvents.isEmpty()) {
                            // 予定なし → タップで追加
                            Box(modifier = Modifier.fillMaxWidth().clickable { editingEvent = null; dialogDateForNewEvent = date; showEventDialog = true }.padding(vertical = 8.dp)) {
                                Text(if (isSearchMode && searchQuery.isNotBlank()) "該当する予定はありません" else "予定なし", color = colors.textGray, modifier = Modifier.padding(vertical = 4.dp))
                            }
                        } else {
                            dayEvents.forEach { event: Event ->
                                EventCard(event = event, colors = colors, onClick = { ev: Event -> viewingEvent = ev; viewingDate = date; showEventDetailDialog = true })
                            }
                        }

                        // 予定追加ボタン
                        Button(
                            onClick = { editingEvent = null; dialogDateForNewEvent = date; showEventDialog = true },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = colors.primaryAccent.copy(alpha = 0.15f), contentColor = colors.primaryAccent),
                            shape = RoundedCornerShape(12.dp)
                        ) { Text("+ 予定を追加", fontSize = 14.sp, fontWeight = FontWeight.Medium) }
                    }
                }

                // 検索結果が空の場合のメッセージ
                if (isSearchMode && searchQuery.isNotBlank() && filteredEvents.isEmpty()) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().clickable { editingEvent = null; dialogDateForNewEvent = selectedDate; showEventDialog = true }.padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
                            Text("該当する予定はありません\nタップして予定を追加", color = colors.textGray, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        }
                    }
                }
            }
        }
    }

    // 各種ダイアログ
    if (showSyncDialog) SyncConfirmDialog(colors, onDismiss = { showSyncDialog = false }, onConfirm = { viewModel.loadEvents() })

    if (showDatePickerDialog) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = selectedDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { showDatePickerDialog = false },
            confirmButton = { TextButton(onClick = { datePickerState.selectedDateMillis?.let { millis -> viewModel.selectDate(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()) }; showDatePickerDialog = false }) { Text("OK", color = colors.primaryAccent) } },
            dismissButton = { TextButton(onClick = { showDatePickerDialog = false }) { Text("キャンセル", color = colors.textGray) } },
            colors = DatePickerDefaults.colors(containerColor = colors.surface)
        ) {
            DatePicker(state = datePickerState, colors = DatePickerDefaults.colors(containerColor = Color.Transparent, titleContentColor = colors.text, headlineContentColor = colors.text, weekdayContentColor = colors.textGray, subheadContentColor = colors.text, navigationContentColor = colors.text, yearContentColor = colors.text, dayContentColor = colors.text, selectedDayContainerColor = colors.primaryAccent, selectedDayContentColor = Color.White, currentYearContentColor = colors.primaryAccent, selectedYearContainerColor = colors.primaryAccent, selectedYearContentColor = Color.White, todayContentColor = colors.primaryAccent, todayDateBorderColor = colors.primaryAccent))
        }
    }

    if (showEventDetailDialog && viewingEvent != null && viewingDate != null) {
        EventDetailDialog(
            event = viewingEvent!!, currentDate = viewingDate!!, colors = colors,
            onDismiss = { showEventDetailDialog = false; viewingEvent = null; viewingDate = null },
            onEdit = { showEventDetailDialog = false; editingEvent = viewingEvent; showEventDialog = true },
            onSplitDelete = { viewModel.splitAndDeleteDay(viewingEvent!!, viewingDate!!); showEventDetailDialog = false; viewingEvent = null; viewingDate = null }
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
            onDelete = { ev: Event -> viewModel.deleteEvent(ev.id); showEventDialog = false }
        )
    }
}