package com.monsivamon.golender.ui

import android.app.TimePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.monsivamon.golender.data.Event
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

// 予定の追加・編集ダイアログ
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventDialog(
    event: Event?,
    selectedDate: LocalDate,
    colors: AppColors,
    onDismiss: () -> Unit,
    onSave: (title: String, startMillis: Long, endMillis: Long, isAllDay: Boolean, location: String, description: String, rrule: String?) -> Unit,
    onDelete: (Event) -> Unit
) {
    val context = LocalContext.current

    var title by remember(event) { mutableStateOf(event?.title ?: "") }
    var location by remember(event) { mutableStateOf(event?.location ?: "") }
    var description by remember(event) { mutableStateOf(event?.description ?: "") }
    var isAllDay by remember(event) { mutableStateOf(event?.isAllDay ?: false) }

    var isRecurring by remember(event) { mutableStateOf(event?.rrule != null) }
    var recurringType by remember(event) {
        mutableStateOf(
            when (event?.rrule) {
                "FREQ=DAILY" -> "DAILY"
                "FREQ=MONTHLY" -> "MONTHLY"
                "FREQ=YEARLY" -> "YEARLY"
                else -> "WEEKLY"
            }
        )
    }
    val recurringOptions = listOf("DAILY" to "毎日", "WEEKLY" to "毎週", "MONTHLY" to "毎月", "YEARLY" to "毎年")

    val initialStart = event?.let { LocalDateTime.ofInstant(Instant.ofEpochMilli(it.startTime), ZoneId.systemDefault()) }
        ?: selectedDate.atTime(10, 0)
    val initialEnd = event?.let { LocalDateTime.ofInstant(Instant.ofEpochMilli(it.endTime), ZoneId.systemDefault()) }
        ?: selectedDate.atTime(11, 0)

    var startDate by remember { mutableStateOf(initialStart.toLocalDate()) }
    var startTime by remember { mutableStateOf(initialStart.toLocalTime()) }
    var endDate by remember { mutableStateOf(initialEnd.toLocalDate()) }
    var endTime by remember { mutableStateOf(initialEnd.toLocalTime()) }

    var showStartDatePicker by remember { mutableStateOf(false) }
    var showEndDatePicker by remember { mutableStateOf(false) }
    var showStartTimePicker by remember { mutableStateOf(false) }
    var showEndTimePicker by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        title = { Text(if (event == null) "予定の追加" else "予定の編集", color = colors.text) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("タイトル", color = colors.textGray) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = colors.text, unfocusedTextColor = colors.text)
                )

                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = isAllDay, onCheckedChange = { isAllDay = it })
                        Text("終日", color = colors.text)

                        Spacer(modifier = Modifier.width(16.dp))

                        Checkbox(checked = isRecurring, onCheckedChange = { isRecurring = it })
                        Text("繰り返し", color = colors.text)
                    }

                    // 繰り返し種類選択
                    if (isRecurring) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(start = 12.dp, top = 4.dp, bottom = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            recurringOptions.forEach { (type, label) ->
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (recurringType == type) colors.primaryAccent else colors.bg)
                                        .clickable { recurringType = type }
                                        .padding(horizontal = 12.dp, vertical = 8.dp)
                                ) {
                                    Text(label, color = if (recurringType == type) Color.White else colors.text, fontSize = 12.sp)
                                }
                            }
                        }
                    }

                    // 開始日時
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 12.dp, top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("開始", color = colors.textGray, modifier = Modifier.width(40.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(colors.bg).clickable { showStartDatePicker = true }.padding(horizontal = 12.dp, vertical = 10.dp)) {
                                Text("${startDate.year}年${startDate.monthValue}月${startDate.dayOfMonth}日", color = colors.text)
                            }
                            if (!isAllDay) {
                                Box(modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(colors.bg).clickable { showStartTimePicker = true }.padding(horizontal = 12.dp, vertical = 10.dp)) {
                                    Text(String.format("%02d:%02d", startTime.hour, startTime.minute), color = colors.text)
                                }
                            }
                        }
                    }

                    // 終了日時
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 12.dp, top = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("終了", color = colors.textGray, modifier = Modifier.width(40.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(colors.bg).clickable { showEndDatePicker = true }.padding(horizontal = 12.dp, vertical = 10.dp)) {
                                Text("${endDate.year}年${endDate.monthValue}月${endDate.dayOfMonth}日", color = colors.text)
                            }
                            if (!isAllDay) {
                                Box(modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(colors.bg).clickable { showEndTimePicker = true }.padding(horizontal = 12.dp, vertical = 10.dp)) {
                                    Text(String.format("%02d:%02d", endTime.hour, endTime.minute), color = colors.text)
                                }
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it },
                    label = { Text("場所", color = colors.textGray) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = colors.text, unfocusedTextColor = colors.text)
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("メモ / 内容", color = colors.textGray) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 3,
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = colors.text, unfocusedTextColor = colors.text)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val finalTitle = title.ifBlank { "名称未設定" }
                val startDateTime = startDate.atTime(if (isAllDay) LocalTime.MIDNIGHT else startTime)
                val endDateTime = endDate.atTime(if (isAllDay) LocalTime.MAX else endTime)
                val startMillis = startDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                val endMillis = endDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

                val finalRrule = if (isRecurring) "FREQ=$recurringType" else null

                onSave(finalTitle, startMillis, endMillis, isAllDay, location, description, finalRrule)
            }) {
                Text("保存", color = colors.primaryAccent, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (event != null) {
                    TextButton(onClick = { onDelete(event) }) { Text("削除", color = colors.sunRed) }
                    Spacer(modifier = Modifier.width(8.dp))
                }
                TextButton(onClick = onDismiss) { Text("キャンセル", color = colors.textGray) }
            }
        }
    )

    // 日付ピッカー（開始日）
    if (showStartDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = startDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { showStartDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val newDate = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                        startDate = newDate
                        if (newDate.isAfter(endDate)) endDate = newDate
                    }
                    showStartDatePicker = false
                }) { Text("OK", color = colors.primaryAccent) }
            },
            dismissButton = { TextButton(onClick = { showStartDatePicker = false }) { Text("キャンセル", color = colors.textGray) } },
            colors = DatePickerDefaults.colors(containerColor = colors.surface)
        ) {
            DatePicker(
                state = datePickerState,
                colors = DatePickerDefaults.colors(containerColor = Color.Transparent, titleContentColor = colors.text, headlineContentColor = colors.text, weekdayContentColor = colors.textGray, subheadContentColor = colors.text, navigationContentColor = colors.text, yearContentColor = colors.text, dayContentColor = colors.text, selectedDayContainerColor = colors.primaryAccent, selectedDayContentColor = Color.White, currentYearContentColor = colors.primaryAccent, selectedYearContainerColor = colors.primaryAccent, selectedYearContentColor = Color.White, todayContentColor = colors.primaryAccent, todayDateBorderColor = colors.primaryAccent)
            )
        }
    }

    // 日付ピッカー（終了日）
    if (showEndDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = endDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { showEndDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val newDate = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                        endDate = newDate
                        if (newDate.isBefore(startDate)) startDate = newDate
                    }
                    showEndDatePicker = false
                }) { Text("OK", color = colors.primaryAccent) }
            },
            dismissButton = { TextButton(onClick = { showEndDatePicker = false }) { Text("キャンセル", color = colors.textGray) } },
            colors = DatePickerDefaults.colors(containerColor = colors.surface)
        ) {
            DatePicker(
                state = datePickerState,
                colors = DatePickerDefaults.colors(containerColor = Color.Transparent, titleContentColor = colors.text, headlineContentColor = colors.text, weekdayContentColor = colors.textGray, subheadContentColor = colors.text, navigationContentColor = colors.text, yearContentColor = colors.text, dayContentColor = colors.text, selectedDayContainerColor = colors.primaryAccent, selectedDayContentColor = Color.White, currentYearContentColor = colors.primaryAccent, selectedYearContainerColor = colors.primaryAccent, selectedYearContentColor = Color.White, todayContentColor = colors.primaryAccent, todayDateBorderColor = colors.primaryAccent)
            )
        }
    }

    // 時刻ピッカー（開始）
    if (showStartTimePicker) {
        val timePickerState = rememberTimePickerState(initialHour = startTime.hour, initialMinute = startTime.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { showStartTimePicker = false },
            containerColor = colors.surface,
            text = {
                TimePicker(
                    state = timePickerState,
                    colors = TimePickerDefaults.colors(clockDialColor = colors.bg, clockDialSelectedContentColor = Color.White, clockDialUnselectedContentColor = colors.text, selectorColor = colors.primaryAccent, containerColor = colors.surface, timeSelectorSelectedContainerColor = colors.primaryAccent.copy(alpha = 0.2f), timeSelectorUnselectedContainerColor = colors.bg, timeSelectorSelectedContentColor = colors.primaryAccent, timeSelectorUnselectedContentColor = colors.text)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    startTime = LocalTime.of(timePickerState.hour, timePickerState.minute)
                    showStartTimePicker = false
                }) { Text("OK", color = colors.primaryAccent) }
            },
            dismissButton = { TextButton(onClick = { showStartTimePicker = false }) { Text("キャンセル", color = colors.textGray) } }
        )
    }

    // 時刻ピッカー（終了）
    if (showEndTimePicker) {
        val timePickerState = rememberTimePickerState(initialHour = endTime.hour, initialMinute = endTime.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { showEndTimePicker = false },
            containerColor = colors.surface,
            text = {
                TimePicker(
                    state = timePickerState,
                    colors = TimePickerDefaults.colors(clockDialColor = colors.bg, clockDialSelectedContentColor = Color.White, clockDialUnselectedContentColor = colors.text, selectorColor = colors.primaryAccent, containerColor = colors.surface, timeSelectorSelectedContainerColor = colors.primaryAccent.copy(alpha = 0.2f), timeSelectorUnselectedContainerColor = colors.bg, timeSelectorSelectedContentColor = colors.primaryAccent, timeSelectorUnselectedContentColor = colors.text)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    endTime = LocalTime.of(timePickerState.hour, timePickerState.minute)
                    showEndTimePicker = false
                }) { Text("OK", color = colors.primaryAccent) }
            },
            dismissButton = { TextButton(onClick = { showEndTimePicker = false }) { Text("キャンセル", color = colors.textGray) } }
        )
    }
}

// 予定詳細表示ダイアログ（編集・一部削除可能）
@Composable
fun EventDetailDialog(
    event: Event,
    currentDate: LocalDate,
    colors: AppColors,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onSplitDelete: () -> Unit
) {
    val formatter = DateTimeFormatter.ofPattern("HH:mm")
    val startDate = LocalDateTime.ofInstant(Instant.ofEpochMilli(event.startTime), ZoneId.systemDefault())
    val endDate = LocalDateTime.ofInstant(Instant.ofEpochMilli(event.endTime), ZoneId.systemDefault())

    val startLocalDate = startDate.toLocalDate()
    val endLocalDate = endDate.toLocalDate()
    val isMultiDay = startLocalDate != endLocalDate
    val isRecurring = event.rrule != null

    val dateString = if (!isMultiDay) {
        "${startDate.year}年${startDate.monthValue}月${startDate.dayOfMonth}日"
    } else {
        "${startDate.monthValue}月${startDate.dayOfMonth}日 - ${endDate.monthValue}月${endDate.dayOfMonth}日"
    }

    val timeString = if (event.isAllDay) "終日" else "${startDate.format(formatter)} - ${endDate.format(formatter)}"

    val recurringText = when (event.rrule) {
        "FREQ=DAILY" -> "（毎日）"
        "FREQ=WEEKLY" -> "（毎週）"
        "FREQ=MONTHLY" -> "（毎月）"
        "FREQ=YEARLY" -> "（毎年）"
        else -> ""
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        title = {
            Text(text = event.title + recurringText, color = colors.text, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🕒", fontSize = 16.sp, modifier = Modifier.padding(end = 8.dp))
                    Column {
                        Text(dateString, color = colors.text, fontSize = 14.sp)
                        Text(timeString, color = colors.textGray, fontSize = 14.sp)
                    }
                }

                if (event.location.isNotBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("📍", fontSize = 16.sp, modifier = Modifier.padding(end = 8.dp))
                        Text(event.location, color = colors.text, fontSize = 14.sp)
                    }
                }

                if (event.description.isNotBlank()) {
                    HorizontalDivider(color = colors.divider)
                    Column {
                        Text("メモ", color = colors.textGray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(event.description, color = colors.text, fontSize = 14.sp)
                    }
                }
            }
        },
        confirmButton = {
            if (!event.isReadOnly) {
                TextButton(onClick = onEdit) { Text("編集", color = colors.primaryAccent, fontWeight = FontWeight.Bold) }
            }
        },
        dismissButton = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 複数日またぎ＆繰り返しでない場合のみ「この日だけ削除」を表示
                if (!event.isReadOnly && isMultiDay && currentDate in startLocalDate..endLocalDate && !isRecurring) {
                    TextButton(onClick = onSplitDelete) {
                        Text("この日だけ削除", color = colors.sunRed)
                    }
                    Spacer(modifier = Modifier.weight(1f))
                }
                TextButton(onClick = onDismiss) { Text("閉じる", color = colors.textGray) }
            }
        }
    )
}