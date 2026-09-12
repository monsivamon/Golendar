package com.monsivamon.golender.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.monsivamon.golender.data.Event
import com.monsivamon.golender.ui.common.GolendarDatePickerDialog
import com.monsivamon.golender.ui.common.GolendarTimePickerDialog
import com.monsivamon.golender.ui.theme.AppColors
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

// 予定の追加・編集ダイアログ
@Composable
fun EventDialog(
    event: Event?,
    selectedDate: LocalDate,
    colors: AppColors,
    onDismiss: () -> Unit,
    onSave: (title: String, startMillis: Long, endMillis: Long, isAllDay: Boolean, location: String, description: String, rrule: String?) -> Unit,
    onDelete: (Event) -> Unit
) {
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

    // 終日予定はUTC、時間指定はシステムタイムゾーンで初期値を復元
    val initialZone = if (event?.isAllDay == true) ZoneOffset.UTC else ZoneId.systemDefault()

    val initialStart = event?.let { LocalDateTime.ofInstant(Instant.ofEpochMilli(it.startTime), initialZone) }
        ?: selectedDate.atTime(10, 0)

    // 終日予定の終了時刻は当日表示に収めるため1ミリ秒減算
    val initialEnd = event?.let {
        val adjustedEnd = if (it.isAllDay && it.endTime > it.startTime) it.endTime - 1 else it.endTime
        LocalDateTime.ofInstant(Instant.ofEpochMilli(adjustedEnd), initialZone)
    } ?: selectedDate.atTime(11, 0)

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
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
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
                    // 終日・繰り返しのチェックボックス
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = isAllDay, onCheckedChange = { isAllDay = it })
                        Text("終日", color = colors.text)

                        Spacer(modifier = Modifier.width(16.dp))

                        Checkbox(checked = isRecurring, onCheckedChange = { isRecurring = it })
                        Text("繰り返し", color = colors.text)
                    }

                    // 繰り返し種類の選択
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

                    // 開始日時（枠線スタイルのボタンで選択）
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 12.dp, top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("開始", color = colors.textGray, modifier = Modifier.width(40.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(modifier = Modifier.clip(RoundedCornerShape(8.dp)).border(1.dp, colors.textGray, RoundedCornerShape(8.dp)).clickable { showStartDatePicker = true }.padding(horizontal = 12.dp, vertical = 10.dp)) {
                                Text("${startDate.year}年${startDate.monthValue}月${startDate.dayOfMonth}日", color = colors.text)
                            }
                            if (!isAllDay) {
                                Box(modifier = Modifier.clip(RoundedCornerShape(8.dp)).border(1.dp, colors.textGray, RoundedCornerShape(8.dp)).clickable { showStartTimePicker = true }.padding(horizontal = 12.dp, vertical = 10.dp)) {
                                    Text(String.format("%02d:%02d", startTime.hour, startTime.minute), color = colors.text)
                                }
                            }
                        }
                    }

                    // 終了日時（枠線スタイルのボタンで選択）
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 12.dp, top = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("終了", color = colors.textGray, modifier = Modifier.width(40.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(modifier = Modifier.clip(RoundedCornerShape(8.dp)).border(1.dp, colors.textGray, RoundedCornerShape(8.dp)).clickable { showEndDatePicker = true }.padding(horizontal = 12.dp, vertical = 10.dp)) {
                                Text("${endDate.year}年${endDate.monthValue}月${endDate.dayOfMonth}日", color = colors.text)
                            }
                            if (!isAllDay) {
                                Box(modifier = Modifier.clip(RoundedCornerShape(8.dp)).border(1.dp, colors.textGray, RoundedCornerShape(8.dp)).clickable { showEndTimePicker = true }.padding(horizontal = 12.dp, vertical = 10.dp)) {
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
                // 終日予定はUTC、それ以外はシステムタイムゾーンで保存
                val saveZone = if (isAllDay) ZoneOffset.UTC else ZoneId.systemDefault()
                val startDateTime = startDate.atTime(if (isAllDay) LocalTime.MIDNIGHT else startTime)
                val endDateTime = endDate.atTime(if (isAllDay) LocalTime.MIDNIGHT else endTime)
                val startMillis = startDateTime.atZone(saveZone).toInstant().toEpochMilli()
                val endMillis = endDateTime.atZone(saveZone).toInstant().toEpochMilli()

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

    // 共通化されたピッカーを呼び出す（日付・時刻選択）
    if (showStartDatePicker) {
        GolendarDatePickerDialog(
            initialDate = startDate,
            colors = colors,
            onDismiss = { showStartDatePicker = false },
            onDateSelected = { newDate ->
                startDate = newDate
                if (newDate.isAfter(endDate)) endDate = newDate
            },
        )
    }
    if (showEndDatePicker) {
        GolendarDatePickerDialog(
            initialDate = endDate,
            colors = colors,
            onDismiss = { showEndDatePicker = false },
            onDateSelected = { newDate ->
                endDate = newDate
                if (newDate.isBefore(startDate)) startDate = newDate
            },
        )
    }
    if (showStartTimePicker) {
        GolendarTimePickerDialog(
            initialTime = startTime,
            colors = colors,
            onDismiss = { showStartTimePicker = false },
            onTimeSelected = { startTime = it },
        )
    }
    if (showEndTimePicker) {
        GolendarTimePickerDialog(
            initialTime = endTime,
            colors = colors,
            onDismiss = { showEndTimePicker = false },
            onTimeSelected = { endTime = it },
        )
    }
}