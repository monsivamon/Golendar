package com.monsivamon.golender.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.monsivamon.golender.viewmodel.CalendarViewModel
import com.monsivamon.golender.data.Event
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

// 月間カレンダー画面
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonthlyCalendarScreen(viewModel: CalendarViewModel, navController: NavController) {
    val currentMonth by viewModel.currentMonth.collectAsState()
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

    var showDatePickerForFAB by remember { mutableStateOf(false) }
    var tempFABDate by remember { mutableStateOf<LocalDate?>(null) }

    var showEventDetailDialog by remember { mutableStateOf(false) }
    var viewingEvent by remember { mutableStateOf<Event?>(null) }
    var viewingDate by remember { mutableStateOf<LocalDate?>(null) }

    // 曜日文字列→DayOfWeek変換用
    val stringToDayOfWeek = mapOf(
        "日" to DayOfWeek.SUNDAY, "月" to DayOfWeek.MONDAY, "火" to DayOfWeek.TUESDAY,
        "水" to DayOfWeek.WEDNESDAY, "木" to DayOfWeek.THURSDAY, "金" to DayOfWeek.FRIDAY, "土" to DayOfWeek.SATURDAY
    )

    Scaffold(
        containerColor = colors.bg,
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    editingEvent = null
                    showDatePickerForFAB = true
                },
                containerColor = colors.primaryAccent,
                contentColor = Color.White
            ) { Text("+", fontSize = 24.sp) }
        }
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {

            // ヘッダー
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                if (isSearchMode) {
                    Box(modifier = Modifier.weight(1f).height(40.dp).border(1.dp, colors.textGray, RoundedCornerShape(8.dp)).padding(horizontal = 12.dp), contentAlignment = Alignment.CenterStart) {
                        if (searchQuery.isEmpty()) Text("予定を検索...", color = colors.textGray, fontSize = 14.sp)
                        BasicTextField(value = searchQuery, onValueChange = { viewModel.updateSearchQuery(it) }, singleLine = true, textStyle = TextStyle(color = colors.text, fontSize = 14.sp), modifier = Modifier.fillMaxWidth())
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("✕", fontSize = 18.sp, color = colors.textGray, modifier = Modifier.clickable { isSearchMode = false; viewModel.updateSearchQuery("") }.padding(8.dp))
                } else {
                    DateTitleWithPicker(title = "${currentMonth.year}年 ${currentMonth.monthValue}月", colors = colors, onClick = { showDatePickerDialog = true })
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        // 今日ボタン
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .border(1.5.dp, colors.text, RoundedCornerShape(4.dp))
                                .clickable { viewModel.resetToToday() }
                        ) {
                            Column(modifier = Modifier.fillMaxSize()) {
                                Box(modifier = Modifier.fillMaxWidth().height(6.dp).background(colors.sunRed))
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text(
                                        text = LocalDate.now().dayOfMonth.toString(),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.text,
                                        modifier = Modifier.offset(y = (-1).dp)
                                    )
                                }
                            }
                        }
                        Text("🔍", fontSize = 20.sp, modifier = Modifier.clickable { isSearchMode = true })
                        Text("🔄", fontSize = 20.sp, modifier = Modifier.clickable { showSyncDialog = true })
                        Text("⚙️", fontSize = 20.sp, modifier = Modifier.clickable { navController.navigate(Routes.SETTINGS) })
                    }
                }
            }

            // 検索結果表示
            if (isSearchMode && searchQuery.isNotBlank()) {
                val filteredEvents = events.filter { it.title.contains(searchQuery, ignoreCase = true) }
                LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                    item { Text("検索結果: ${filteredEvents.size}件", color = colors.textGray, modifier = Modifier.padding(bottom = 8.dp)) }
                    items(filteredEvents) { event: Event ->
                        SearchResultCard(event = event, colors = colors, onClick = { ev: Event ->
                            viewingEvent = ev
                            val zone = if (ev.isAllDay) ZoneOffset.UTC else ZoneId.systemDefault()
                            viewingDate = LocalDateTime.ofInstant(Instant.ofEpochMilli(ev.startTime), zone).toLocalDate()
                            showEventDetailDialog = true
                        })
                    }
                }
            } else {
                // タブ切り替え
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    Text("<", fontSize = 18.sp, color = colors.text, modifier = Modifier.clickable { navigateTab(navController, Routes.MONTHLY, -1) }.padding(8.dp))
                    Text("日", fontSize = 16.sp, color = colors.text, modifier = Modifier.clickable { navController.navigate(Routes.DAILY) { launchSingleTop = true } })
                    Text("週", fontSize = 16.sp, color = colors.text, modifier = Modifier.clickable { navController.navigate(Routes.WEEKLY) { launchSingleTop = true } })
                    Box(modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(colors.primaryAccent).padding(horizontal = 24.dp, vertical = 6.dp)) {
                        Text("月", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                    Text(">", fontSize = 18.sp, color = colors.text, modifier = Modifier.clickable { navigateTab(navController, Routes.MONTHLY, 1) }.padding(8.dp))
                }

                // 月間カレンダーグリッド
                Column(modifier = Modifier.weight(1.2f)) {
                    // 曜日ヘッダー
                    val allDays = listOf("日", "月", "火", "水", "木", "金", "土")
                    val startIndex = when (weekStartDay) { DayOfWeek.SUNDAY -> 0; DayOfWeek.MONDAY -> 1; else -> 0 }
                    val orderedWeekDays = allDays.drop(startIndex) + allDays.take(startIndex)

                    Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                        orderedWeekDays.forEachIndexed { _, dayString ->
                            val dayEnum = stringToDayOfWeek[dayString]!!
                            val c = dayColors[dayEnum] ?: Color.Unspecified
                            val finalColor = if (c == Color.Unspecified) colors.text else c
                            Text(dayString, modifier = Modifier.weight(1f), textAlign = TextAlign.Center, color = finalColor, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    // 日付セルを生成
                    val firstDayOfMonth = currentMonth.atDay(1)
                    val offset = (firstDayOfMonth.dayOfWeek.value - weekStartDay.value + 7) % 7
                    val daysInMonth = currentMonth.lengthOfMonth()
                    val totalCells = ((daysInMonth + offset + 6) / 7) * 7
                    val today = LocalDate.now()

                    LazyVerticalGrid(columns = GridCells.Fixed(7), modifier = Modifier.fillMaxSize()) {
                        items(count = totalCells) { index: Int ->
                            val dayOffset = index - offset
                            val date = when {
                                dayOffset < 0 -> currentMonth.minusMonths(1).atEndOfMonth().plusDays((dayOffset + 1).toLong())
                                dayOffset < daysInMonth -> currentMonth.atDay(dayOffset + 1)
                                else -> currentMonth.plusMonths(1).atDay(dayOffset - daysInMonth + 1)
                            }
                            val isCurrentMonth = date.month == currentMonth.month
                            val isToday = date == today

                            val dailyEvents = events.filter { event ->
                                val zone = if (event.isAllDay) ZoneOffset.UTC else ZoneId.systemDefault()
                                val eventStart = LocalDateTime.ofInstant(Instant.ofEpochMilli(event.startTime), zone).toLocalDate()
                                val adjustedEndTime = if (event.endTime > event.startTime) event.endTime - 1 else event.endTime
                                val eventEnd = LocalDateTime.ofInstant(Instant.ofEpochMilli(adjustedEndTime), zone).toLocalDate()
                                date in eventStart..eventEnd
                            }

                            val c = dayColors[date.dayOfWeek] ?: Color.Unspecified

                            CalendarCell(
                                date = date,
                                events = dailyEvents,
                                isSelected = selectedDate == date,
                                isCurrentMonth = isCurrentMonth,
                                isToday = isToday,
                                dayColor = c,
                                colors = colors,
                                onClick = { viewModel.selectDate(date) }
                            )
                        }
                    }
                }

                HorizontalDivider(thickness = 1.dp, color = colors.divider)

                // 選択日のイベントリスト（下部）
                Column(modifier = Modifier.weight(0.8f).fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    val jpDayOfWeek = getJpDayOfWeek(selectedDate.dayOfWeek)

                    val dailyEvents = events.filter { event ->
                        val zone = if (event.isAllDay) ZoneOffset.UTC else ZoneId.systemDefault()
                        val eventStart = LocalDateTime.ofInstant(Instant.ofEpochMilli(event.startTime), zone).toLocalDate()
                        val adjustedEndTime = if (event.endTime > event.startTime) event.endTime - 1 else event.endTime
                        val eventEnd = LocalDateTime.ofInstant(Instant.ofEpochMilli(adjustedEndTime), zone).toLocalDate()
                        selectedDate in eventStart..eventEnd
                    }

                    Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(bottom = 12.dp, top = 8.dp)) {
                        val c = dayColors[selectedDate.dayOfWeek] ?: Color.Unspecified
                        val finalBottomColor = if (c == Color.Unspecified) colors.text else c
                        Text("${selectedDate.monthValue}月${selectedDate.dayOfMonth}日 ($jpDayOfWeek)", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = finalBottomColor)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("${dailyEvents.size}件", fontSize = 14.sp, color = colors.textGray)
                    }

                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        if (dailyEvents.isEmpty()) {
                            item { Text("予定なし", color = colors.textGray, modifier = Modifier.padding(top = 8.dp)) }
                        } else {
                            items(dailyEvents) { event: Event ->
                                EventCard(event = event, colors = colors, onClick = { ev: Event ->
                                    viewingEvent = ev
                                    viewingDate = selectedDate
                                    showEventDetailDialog = true
                                })
                            }
                        }
                        // 予定追加ボタン
                        item {
                            Button(
                                onClick = {
                                    editingEvent = null
                                    showEventDialog = true
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp),
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
    }

    // 各種ダイアログ
    if (showDatePickerDialog) {
        YearMonthPickerDialog(
            currentYear = currentMonth.year,
            currentMonth = currentMonth.monthValue,
            colors = colors,
            onDismiss = { showDatePickerDialog = false },
            onDateSelected = { year: Int, month: Int -> viewModel.selectDate(LocalDate.of(year, month, 1)); showDatePickerDialog = false }
        )
    }

    if (showDatePickerForFAB) {
        val datePickerState = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showDatePickerForFAB = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        tempFABDate = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                        showDatePickerForFAB = false
                        showEventDialog = true
                    }
                }) { Text("OK", color = colors.primaryAccent) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePickerForFAB = false }) { Text("キャンセル", color = colors.textGray) }
            },
            colors = DatePickerDefaults.colors(containerColor = colors.surface)
        ) {
            DatePicker(
                state = datePickerState,
                colors = DatePickerDefaults.colors(
                    containerColor = Color.Transparent, titleContentColor = colors.text, headlineContentColor = colors.text, weekdayContentColor = colors.textGray,
                    subheadContentColor = colors.text, navigationContentColor = colors.text, yearContentColor = colors.text, dayContentColor = colors.text,
                    selectedDayContainerColor = colors.primaryAccent, selectedDayContentColor = Color.White, currentYearContentColor = colors.primaryAccent,
                    selectedYearContainerColor = colors.primaryAccent, selectedYearContentColor = Color.White, todayContentColor = colors.primaryAccent, todayDateBorderColor = colors.primaryAccent
                )
            )
        }
    }

    if (showSyncDialog) SyncConfirmDialog(colors, onDismiss = { showSyncDialog = false }, onConfirm = { viewModel.loadEvents() })

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
            onDelete = { ev: Event -> viewModel.deleteEvent(ev.id); showEventDialog = false; tempFABDate = null }
        )
    }
}

// カレンダーの1日分のセル
@Composable
fun CalendarCell(date: LocalDate, events: List<Event>, isSelected: Boolean, isCurrentMonth: Boolean, isToday: Boolean, dayColor: Color, colors: AppColors, onClick: () -> Unit) {
    val dateColor = when {
        isSelected -> Color.White
        !isCurrentMonth -> colors.textGray
        dayColor != Color.Unspecified -> dayColor
        else -> colors.text
    }

    val borderColor = if (isToday) colors.primaryAccent else colors.divider
    val borderWidth = if (isToday) 1.5.dp else 0.5.dp

    Box(modifier = Modifier.aspectRatio(0.6f).padding(2.dp).border(borderWidth, borderColor, RoundedCornerShape(4.dp)).clickable { onClick() }) {
        if (isSelected) Box(modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(4.dp)).background(colors.primaryAccent.copy(alpha = 0.5f)))
        Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(date.dayOfMonth.toString(), fontSize = 14.sp, color = dateColor, fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.padding(top = 4.dp, bottom = 2.dp))
            // 予定を最大4件まで表示（誕生日・文化イベントは専用色）
            events.take(4).forEach { event: Event ->
                val bgColor = when {
                    event.isBirthdayCalendar -> Color(0xFFFF9800)
                    event.isCulturalEvent -> Color(0xFF4CAF50)
                    else -> colors.primaryAccent
                }
                Box(modifier = Modifier.fillMaxWidth(0.9f).padding(vertical = 1.dp).clip(RoundedCornerShape(2.dp)).background(bgColor).padding(horizontal = 2.dp, vertical = 1.dp)) {
                    Text(event.title, fontSize = 8.sp, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}