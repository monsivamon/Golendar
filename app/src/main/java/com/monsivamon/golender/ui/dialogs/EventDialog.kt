package com.monsivamon.golender.ui.dialogs

import android.util.Log
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.monsivamon.golender.data.Event
import com.monsivamon.golender.data.util.RruleExpander
import com.monsivamon.golender.ui.common.GolendarDatePickerDialog
import com.monsivamon.golender.ui.common.GolendarTimePickerDialog
import com.monsivamon.golender.ui.theme.AppColors
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale

private const val TAG = "Golendar"

private val LABEL_WIDTH = 60.dp

// 予定の追加・編集ダイアログを表示する。
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EventDialog(
    event: Event?,
    selectedDate: LocalDate,
    colors: AppColors,
    fromCalendar: Boolean = false,
    onDismiss: () -> Unit,
    onSave: (title: String, startMillis: Long, endMillis: Long, isAllDay: Boolean, location: String, description: String, rrule: String?) -> Unit,
    onDelete: (Event) -> Unit
) {
    var title by remember(event) { mutableStateOf(event?.title ?: "") }
    var location by remember(event) { mutableStateOf(event?.location ?: "") }
    var description by remember(event) { mutableStateOf(event?.description ?: "") }
    var isAllDay by remember(event) { mutableStateOf(event?.isAllDay ?: false) }

    val eventKey = event?.id ?: -1L

    var isRecurring by remember(eventKey) { mutableStateOf(event?.rrule != null) }
    var recurringType by remember(eventKey) {
        mutableStateOf(
            when (event?.rrule?.let { RruleExpander.baseRuleOf(it) }) {
                "FREQ=DAILY" -> "DAILY"
                RruleExpander.RRULE_WEEKDAYS -> "WEEKDAYS"
                "FREQ=MONTHLY" -> "MONTHLY"
                "FREQ=YEARLY" -> "YEARLY"
                "FREQ=WEEKLY" -> "WEEKLY"
                else -> "WEEKLY"
            }
        )
    }

    var hasRecurrenceEnd by remember(eventKey) { mutableStateOf(RruleExpander.parseUntilDate(event?.rrule) != null) }
    var recurrenceEndDate by remember(eventKey) {
        val parsed = RruleExpander.parseUntilDate(event?.rrule)
        mutableStateOf(parsed ?: LocalDate.now().plusMonths(1))
    }

    val recurringOptions = listOf(
        "DAILY" to "毎日",
        "WEEKDAYS" to "平日",
        "WEEKLY" to "毎週",
        "MONTHLY" to "毎月",
        "YEARLY" to "毎年",
    )

    val initialZone = if (event?.isAllDay == true) ZoneOffset.UTC else ZoneId.systemDefault()

    val initialStart = event?.let { LocalDateTime.ofInstant(Instant.ofEpochMilli(it.startTime), initialZone) }
        ?: selectedDate.atTime(10, 0)

    val initialEnd = event?.let {
        val adjustedEnd = if (it.isAllDay && it.endTime > it.startTime) it.endTime - 1 else it.endTime
        LocalDateTime.ofInstant(Instant.ofEpochMilli(adjustedEnd), initialZone)
    } ?: selectedDate.atTime(11, 0)

    var startDate by remember(eventKey, selectedDate) { mutableStateOf(initialStart.toLocalDate()) }
    var startTime by remember(eventKey, selectedDate) { mutableStateOf(initialStart.toLocalTime()) }
    var endDate by remember(eventKey, selectedDate) { mutableStateOf(initialEnd.toLocalDate()) }
    var endTime by remember(eventKey, selectedDate) { mutableStateOf(initialEnd.toLocalTime()) }

    var showStartDatePicker by remember { mutableStateOf(false) }
    var showEndDatePicker by remember { mutableStateOf(false) }
    var showStartTimePicker by remember { mutableStateOf(false) }
    var showEndTimePicker by remember { mutableStateOf(false) }
    var showRecurrenceEndPicker by remember { mutableStateOf(false) }

    val isLightBackground = colors.bg.luminance() > 0.5f

    val startDateLocked = fromCalendar
    val endDateLocked = fromCalendar && isRecurring

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
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = colors.text,
                        unfocusedTextColor = colors.text,
                        focusedBorderColor = colors.primaryAccent,
                        unfocusedBorderColor = colors.textGray,
                        cursorColor = colors.primaryAccent,
                    )
                )

                Column(modifier = Modifier.fillMaxWidth()) {
                    val checkboxColors = CheckboxDefaults.colors(
                        checkedColor = colors.primaryAccent,
                        uncheckedColor = colors.text,
                        checkmarkColor = if (isLightBackground) Color.White else Color(0xFF1A1A1A),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = isAllDay,
                            onCheckedChange = { isAllDay = it },
                            colors = checkboxColors,
                        )
                        Text("終日", color = colors.text)

                        Spacer(modifier = Modifier.width(16.dp))

                        Checkbox(
                            checked = isRecurring,
                            onCheckedChange = { checked ->
                                isRecurring = checked
                                if (checked && fromCalendar) {
                                    endDate = startDate
                                    if (!isAllDay && endTime <= startTime) {
                                        endTime = startTime.plusHours(1)
                                    }
                                }
                            },
                            colors = checkboxColors,
                        )
                        Text("繰り返し", color = colors.text)
                    }

                    if (isRecurring) {
                        FlowRow(
                            modifier = Modifier.fillMaxWidth().padding(start = 12.dp, top = 4.dp, bottom = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            recurringOptions.forEach { (type, label) ->
                                val isSelected = recurringType == type
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSelected) colors.primaryAccent else colors.bg)
                                        .clickable {
                                            Log.d(TAG, "tapped: $label ($type), previous=$recurringType")
                                            recurringType = type
                                        }
                                        .padding(horizontal = 12.dp, vertical = 8.dp)
                                ) {
                                    Text(
                                        label,
                                        color = if (isSelected) {
                                            if (isLightBackground) Color.White else Color(0xFF1A1A1A)
                                        } else colors.text,
                                        fontSize = 12.sp,
                                    )
                                }
                            }
                        }
                    }

                    if (startDateLocked) {
                        Text(
                            "※カレンダーで選んだ日付に追加されます（日付は変更できません）",
                            color = colors.textGray,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(start = 12.dp, top = 4.dp, bottom = 4.dp),
                        )
                    }

                    val startDateBorder = if (startDateLocked) colors.textGray.copy(alpha = 0.3f) else colors.textGray
                    val startDateText = colors.text.copy(alpha = if (startDateLocked) 0.4f else 1f)

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 12.dp, top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "開始",
                            color = colors.textGray.copy(alpha = if (startDateLocked) 0.4f else 1f),
                            modifier = Modifier.width(LABEL_WIDTH),
                        )
                        Row(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f, fill = false)
                                    .clip(RoundedCornerShape(8.dp))
                                    .border(1.dp, startDateBorder, RoundedCornerShape(8.dp))
                                    .then(
                                        if (!startDateLocked) Modifier.clickable { showStartDatePicker = true }
                                        else Modifier
                                    )
                                    .padding(horizontal = 10.dp, vertical = 10.dp),
                                contentAlignment = Alignment.CenterStart,
                            ) {
                                Text(
                                    "${startDate.year}年${startDate.monthValue}月${startDate.dayOfMonth}日",
                                    color = startDateText,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            if (!isAllDay) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .border(1.dp, colors.textGray, RoundedCornerShape(8.dp))
                                        .clickable { showStartTimePicker = true }
                                        .padding(horizontal = 10.dp, vertical = 10.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        String.format(Locale.ROOT, "%02d:%02d", startTime.hour, startTime.minute),
                                        color = colors.text,
                                        maxLines = 1,
                                        softWrap = false,
                                    )
                                }
                            }
                        }
                    }

                    val endDateBorder = if (endDateLocked) colors.textGray.copy(alpha = 0.3f) else colors.textGray
                    val endDateText = colors.text.copy(alpha = if (endDateLocked) 0.4f else 1f)

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 12.dp, top = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "終了",
                            color = colors.textGray.copy(alpha = if (endDateLocked) 0.4f else 1f),
                            modifier = Modifier.width(LABEL_WIDTH),
                        )
                        Row(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f, fill = false)
                                    .clip(RoundedCornerShape(8.dp))
                                    .border(1.dp, endDateBorder, RoundedCornerShape(8.dp))
                                    .then(
                                        if (!endDateLocked) Modifier.clickable { showEndDatePicker = true }
                                        else Modifier
                                    )
                                    .padding(horizontal = 10.dp, vertical = 10.dp),
                                contentAlignment = Alignment.CenterStart,
                            ) {
                                Text(
                                    "${endDate.year}年${endDate.monthValue}月${endDate.dayOfMonth}日",
                                    color = endDateText,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            if (!isAllDay) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .border(1.dp, colors.textGray, RoundedCornerShape(8.dp))
                                        .clickable { showEndTimePicker = true }
                                        .padding(horizontal = 10.dp, vertical = 10.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        String.format(Locale.ROOT, "%02d:%02d", endTime.hour, endTime.minute),
                                        color = colors.text,
                                        maxLines = 1,
                                        softWrap = false,
                                    )
                                }
                            }
                        }
                    }

                    if (isRecurring) {
                        Text(
                            "※「終了」は1回分の所要時間です。繰り返しの終了日は下で指定します。",
                            color = colors.textGray,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(start = (12 + 60 + 8).dp, top = 6.dp),
                        )

                        Spacer(Modifier.height(8.dp))

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = hasRecurrenceEnd,
                                onCheckedChange = { hasRecurrenceEnd = it },
                                colors = checkboxColors,
                            )
                            Text("繰り返しの終了日を指定", color = colors.text, fontSize = 14.sp)
                        }

                        if (hasRecurrenceEnd) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(start = 12.dp, top = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    "終了日",
                                    color = colors.textGray,
                                    modifier = Modifier.width(LABEL_WIDTH),
                                )
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .border(1.dp, colors.primaryAccent, RoundedCornerShape(8.dp))
                                        .clickable { showRecurrenceEndPicker = true }
                                        .padding(horizontal = 12.dp, vertical = 10.dp)
                                ) {
                                    Text("${recurrenceEndDate.year}年${recurrenceEndDate.monthValue}月${recurrenceEndDate.dayOfMonth}日", color = colors.text)
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
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = colors.text,
                        unfocusedTextColor = colors.text,
                        focusedBorderColor = colors.primaryAccent,
                        unfocusedBorderColor = colors.textGray,
                        cursorColor = colors.primaryAccent,
                    )
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("メモ / 内容", color = colors.textGray) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 3,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = colors.text,
                        unfocusedTextColor = colors.text,
                        focusedBorderColor = colors.primaryAccent,
                        unfocusedBorderColor = colors.textGray,
                        cursorColor = colors.primaryAccent,
                    )
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val effectiveEndDate = if (endDateLocked) startDate else endDate

                val finalTitle = title.ifBlank { "名称未設定" }
                val saveZone = if (isAllDay) ZoneOffset.UTC else ZoneId.systemDefault()
                val startDateTime = startDate.atTime(if (isAllDay) LocalTime.MIDNIGHT else startTime)
                val endDateTime = effectiveEndDate.atTime(if (isAllDay) LocalTime.MIDNIGHT else endTime)
                val startMillis = startDateTime.atZone(saveZone).toInstant().toEpochMilli()
                val endMillis = endDateTime.atZone(saveZone).toInstant().toEpochMilli()

                val finalRrule = when {
                    !isRecurring -> null
                    else -> {
                        val base = if (recurringType == "WEEKDAYS") RruleExpander.RRULE_WEEKDAYS else "FREQ=$recurringType"
                        if (hasRecurrenceEnd) "$base;UNTIL=${RruleExpander.formatUntil(recurrenceEndDate)}" else base
                    }
                }

                Log.d(TAG, "保存: fromCalendar=$fromCalendar, startDateLocked=$startDateLocked, endDateLocked=$endDateLocked, finalRrule=$finalRrule")

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
    if (showRecurrenceEndPicker) {
        GolendarDatePickerDialog(
            initialDate = recurrenceEndDate,
            colors = colors,
            onDismiss = { showRecurrenceEndPicker = false },
            onDateSelected = { newDate ->
                recurrenceEndDate = newDate
                showRecurrenceEndPicker = false
            },
        )
    }
}