package com.monsivamon.golender.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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

// 日間カレンダー画面
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailyCalendarScreen(viewModel: CalendarViewModel, navController: NavController) {
    val selectedDate by viewModel.selectedDate.collectAsState()
    val events by viewModel.events.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val dayColors by viewModel.dayColors.collectAsState()
    val customBg by viewModel.calendarBgColor.collectAsState()
    val colors = getAppColors(themeMode, customBg)

    var isSearchMode by remember { mutableStateOf(false) }
    var showSyncDialog by remember { mutableStateOf(false) }
    var showDatePickerDialog by remember { mutableStateOf(false) }

    var showEventDialog by remember { mutableStateOf(false) }
    var editingEvent by remember { mutableStateOf<Event?>(null) }

    var showEventDetailDialog by remember { mutableStateOf(false) }
    var viewingEvent by remember { mutableStateOf<Event?>(null) }
    var viewingDate by remember { mutableStateOf<LocalDate?>(null) }

    // 選択日のイベントを抽出（終日予定はUTC、時間指定はシステムタイムゾーンで判定）
    val dailyEvents = events.filter { event ->
        val zone = if (event.isAllDay) ZoneOffset.UTC else ZoneId.systemDefault()
        val eventStart = LocalDateTime.ofInstant(Instant.ofEpochMilli(event.startTime), zone).toLocalDate()
        val adjustedEndTime = if (event.endTime > event.startTime) event.endTime - 1 else event.endTime
        val eventEnd = LocalDateTime.ofInstant(Instant.ofEpochMilli(adjustedEndTime), zone).toLocalDate()
        selectedDate in eventStart..eventEnd
    }

    val filteredEvents = if (searchQuery.isNotBlank()) {
        dailyEvents.filter { it.title.contains(searchQuery, ignoreCase = true) }
    } else {
        dailyEvents
    }

    Scaffold(
        containerColor = colors.bg
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
                    DateTitleWithPicker(title = "${selectedDate.year}年${selectedDate.monthValue}月${selectedDate.dayOfMonth}日", colors = colors, onClick = { showDatePickerDialog = true })
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

            // タブ切り替え
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                Text("<", fontSize = 18.sp, color = colors.text, modifier = Modifier.clickable { navigateTab(navController, Routes.DAILY, -1) }.padding(8.dp))
                Box(modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(colors.primaryAccent).padding(horizontal = 24.dp, vertical = 6.dp)) { Text("日", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White) }
                Text("週", fontSize = 16.sp, color = colors.text, modifier = Modifier.clickable { navController.navigate(Routes.WEEKLY) { launchSingleTop = true } })
                Text("月", fontSize = 16.sp, color = colors.text, modifier = Modifier.clickable { navController.navigate(Routes.MONTHLY) { launchSingleTop = true } })
                Text(">", fontSize = 18.sp, color = colors.text, modifier = Modifier.clickable { navigateTab(navController, Routes.DAILY, 1) }.padding(8.dp))
            }

            HorizontalDivider(thickness = 1.dp, color = colors.divider)

            // イベントリスト
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(bottom = 12.dp)) {
                    val c = dayColors[selectedDate.dayOfWeek] ?: Color.Unspecified
                    val dayColor = if (c == Color.Unspecified) colors.text else c
                    Text("${selectedDate.monthValue}月${selectedDate.dayOfMonth}日 (${getJpDayOfWeek(selectedDate.dayOfWeek)})", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = dayColor)
                    Spacer(modifier = Modifier.width(8.dp))
                    if (isSearchMode && searchQuery.isNotBlank()) Text("検索結果: ${filteredEvents.size}件", fontSize = 14.sp, color = colors.primaryAccent)
                    else Text("${filteredEvents.size}件", fontSize = 14.sp, color = colors.textGray)
                }

                LazyColumn {
                    if (filteredEvents.isEmpty()) {
                        item {
                            Box(modifier = Modifier.fillMaxWidth().clickable { editingEvent = null; showEventDialog = true }.padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
                                Text(if (isSearchMode && searchQuery.isNotBlank()) "該当する予定はありません" else "予定なし", color = colors.textGray, fontSize = 16.sp)
                            }
                        }
                    } else {
                        items(filteredEvents) { event: Event ->
                            if (isSearchMode && searchQuery.isNotBlank()) SearchResultCard(event = event, colors = colors, onClick = { ev: Event -> viewingEvent = ev; viewingDate = selectedDate; showEventDetailDialog = true })
                            else EventCard(event = event, colors = colors, onClick = { ev: Event -> viewingEvent = ev; viewingDate = selectedDate; showEventDetailDialog = true })
                        }
                    }

                    // 予定追加ボタン
                    item {
                        Button(
                            onClick = { editingEvent = null; showEventDialog = true },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = colors.primaryAccent.copy(alpha = 0.15f), contentColor = colors.primaryAccent),
                            shape = RoundedCornerShape(12.dp)
                        ) { Text("+ 予定を追加", fontSize = 16.sp, fontWeight = FontWeight.Medium) }
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
        val dialogDate = editingEvent?.let { Instant.ofEpochMilli(it.startTime).atZone(zone).toLocalDate() } ?: selectedDate

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