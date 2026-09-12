package com.monsivamon.golender.ui

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
import com.monsivamon.golender.ui.dialogs.EventDetailDialog
import com.monsivamon.golender.ui.theme.getAppColors
import com.monsivamon.golender.viewmodel.CalendarViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

// 日間カレンダー画面（選択日の予定を時系列で表示）
// ヘッダー・タブ行・BackHandlerはAppNavigation側で共通化済み
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

    // 選択日に発生する予定のみを抽出
    val dailyEvents = events.filter { it.occursOn(selectedDate) }
    val filteredEvents = if (searchQuery.isNotBlank()) {
        dailyEvents.filter { it.title.contains(searchQuery, ignoreCase = true) }
    } else dailyEvents

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        // 日付見出しと件数表示
        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(bottom = 12.dp)) {
            val c = dayColors[selectedDate.dayOfWeek] ?: Color.Unspecified
            val dayColor = if (c == Color.Unspecified) colors.text else c
            Text(
                "${selectedDate.monthValue}月${selectedDate.dayOfMonth}日 (${getJpDayOfWeek(selectedDate.dayOfWeek)})",
                fontSize = 18.sp, fontWeight = FontWeight.Bold, color = dayColor
            )
            Spacer(modifier = Modifier.width(8.dp))
            if (isSearchMode && searchQuery.isNotBlank())
                Text("検索結果: ${filteredEvents.size}件", fontSize = 14.sp, color = colors.primaryAccent)
            else
                Text("${filteredEvents.size}件", fontSize = 14.sp, color = colors.textGray)
        }

        LazyColumn {
            // 予定なし or 検索結果なし
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
                // 検索中は検索結果カード、通常は予定カードを表示
                items(filteredEvents) { event: Event ->
                    if (isSearchMode && searchQuery.isNotBlank())
                        SearchResultCard(event = event, colors = colors, onClick = { ev ->
                            viewingEvent = ev; viewingDate = selectedDate; showEventDetailDialog = true
                        })
                    else
                        EventCard(event = event, colors = colors, onClick = { ev ->
                            viewingEvent = ev; viewingDate = selectedDate; showEventDetailDialog = true
                        })
                }
            }

            // 予定追加ボタン
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

    // 予定詳細ダイアログ
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

    // 予定追加・編集ダイアログ
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