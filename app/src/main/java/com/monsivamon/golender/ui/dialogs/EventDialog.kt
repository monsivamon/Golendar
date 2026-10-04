package com.monsivamon.golender.ui.dialogs

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.monsivamon.golender.data.Event
import com.monsivamon.golender.data.EventPhoto
import com.monsivamon.golender.data.SelectedCalendar
import com.monsivamon.golender.data.util.RruleExpander
import com.monsivamon.golender.ui.common.GolendarDatePickerDialog
import com.monsivamon.golender.ui.common.GolendarTimePickerDialog
import com.monsivamon.golender.ui.dialogs.event.CalendarSelector
import com.monsivamon.golender.ui.dialogs.event.DateTimeRow
import com.monsivamon.golender.ui.dialogs.event.PhotoAttachmentSection
import com.monsivamon.golender.ui.dialogs.event.RecurrenceChips
import com.monsivamon.golender.ui.theme.AppColors
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

// 開始／終了／終了日ラベル共通の幅
internal val LABEL_WIDTH: Dp = 60.dp

// 予定の追加・編集ダイアログを表示する
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EventDialog(
    event: Event?,
    selectedDate: LocalDate,
    colors: AppColors,
    fromCalendar: Boolean = false,
    initialPhotos: List<EventPhoto> = emptyList(),
    photoAttachEnabled: Boolean = false,
    aiParseEnabled: Boolean = false,
    availableCalendars: List<SelectedCalendar> = emptyList(),
    onDismiss: () -> Unit,
    onSave: (
        title: String, startMillis: Long, endMillis: Long, isAllDay: Boolean,
        location: String, description: String, rrule: String?,
        newPhotoUris: List<Uri>, keptPhotoIds: List<Long>,
        targetCalendarId: Long?,
    ) -> Unit,
    onDelete: (Event) -> Unit,
    onAiParse: () -> Unit,
) {
    // ---------- 基本入力 ----------
    var title by remember(event) { mutableStateOf(event?.title ?: "") }
    var location by remember(event) { mutableStateOf(event?.location ?: "") }
    var description by remember(event) { mutableStateOf(event?.description ?: "") }
    var isAllDay by remember(event) { mutableStateOf(event?.isAllDay ?: false) }
    val eventKey = event?.id ?: -1L

    // ---------- 追加先カレンダー ----------
    var selectedCalendarId by remember(eventKey, availableCalendars) {
        mutableStateOf(
            event?.calendarId?.takeIf { it > 0L }
                ?: availableCalendars.firstOrNull()?.calendarId
        )
    }
    val showCalendarSelector = availableCalendars.size >= 2

    // ---------- 繰り返し ----------
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
    var hasRecurrenceEnd by remember(eventKey) {
        mutableStateOf(RruleExpander.parseUntilDate(event?.rrule) != null)
    }
    var recurrenceEndDate by remember(eventKey) {
        val parsed = RruleExpander.parseUntilDate(event?.rrule)
        mutableStateOf(parsed ?: LocalDate.now().plusMonths(1))
    }

    // ---------- 開始／終了 ----------
    val initialZone = if (event?.isAllDay == true) ZoneOffset.UTC else ZoneId.systemDefault()
    val initialStart = event?.let {
        LocalDateTime.ofInstant(Instant.ofEpochMilli(it.startTime), initialZone)
    } ?: selectedDate.atTime(10, 0)
    val initialEnd = event?.let {
        val adjustedEnd = if (it.isAllDay && it.endTime > it.startTime) it.endTime - 1 else it.endTime
        LocalDateTime.ofInstant(Instant.ofEpochMilli(adjustedEnd), initialZone)
    } ?: selectedDate.atTime(11, 0)

    var startDate by remember(eventKey, selectedDate) { mutableStateOf(initialStart.toLocalDate()) }
    var startTime by remember(eventKey, selectedDate) { mutableStateOf(initialStart.toLocalTime()) }
    var endDate by remember(eventKey, selectedDate) { mutableStateOf(initialEnd.toLocalDate()) }
    var endTime by remember(eventKey, selectedDate) { mutableStateOf(initialEnd.toLocalTime()) }

    // ---------- ピッカー表示フラグ ----------
    var showStartDatePicker by remember { mutableStateOf(false) }
    var showEndDatePicker by remember { mutableStateOf(false) }
    var showStartTimePicker by remember { mutableStateOf(false) }
    var showEndTimePicker by remember { mutableStateOf(false) }
    var showRecurrenceEndPicker by remember { mutableStateOf(false) }
    var showPlacePicker by remember { mutableStateOf(false) }

    // ---------- 写真 ----------
    var keptPhotoIds by remember(event?.id, initialPhotos) {
        mutableStateOf(initialPhotos.map { it.id }.toSet())
    }
    var newPhotoUris by remember(event?.id) { mutableStateOf<List<Uri>>(emptyList()) }
    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(
            maxItems = EventPhoto.MAX_PHOTOS_PER_EVENT,
        )
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            val existingCount = initialPhotos.count { it.id in keptPhotoIds }
            val room = (EventPhoto.MAX_PHOTOS_PER_EVENT - existingCount - newPhotoUris.size)
                .coerceAtLeast(0)
            newPhotoUris = newPhotoUris + uris.take(room)
        }
    }

    val isLightBackground = colors.bg.luminance() > 0.5f
    val startDateLocked = fromCalendar
    val endDateLocked = fromCalendar && isRecurring

    // ---------- ダイアログ本体 ----------
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        title = { Text(if (event == null) "予定の追加" else "予定の編集", color = colors.text) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // 追加先カレンダー（Google モードで 2 件以上のときのみ）
                if (showCalendarSelector) {
                    CalendarSelector(
                        calendars = availableCalendars,
                        selectedId = selectedCalendarId,
                        colors = colors,
                        onSelect = { selectedCalendarId = it },
                    )
                }

                // AI 解析ボタン（Golendar モード時のみ）
                if (aiParseEnabled) {
                    TextButton(onClick = onAiParse) {
                        Text("AIに読み取らせる (BETA)", color = colors.primaryAccent)
                    }
                }

                // タイトル
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
                    ),
                )

                // 終日・繰り返しチェック＋繰り返し種別
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
                        RecurrenceChips(
                            recurringType = recurringType,
                            onTypeChange = { recurringType = it },
                            colors = colors,
                            isLightBackground = isLightBackground,
                        )
                    }

                    if (startDateLocked) {
                        Text(
                            "※カレンダーで選んだ日付に追加されます（日付は変更できません）",
                            color = colors.textGray,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(start = 12.dp, top = 4.dp, bottom = 4.dp),
                        )
                    }

                    // 開始日時
                    DateTimeRow(
                        label = "開始",
                        date = startDate,
                        time = startTime,
                        isAllDay = isAllDay,
                        isDateLocked = startDateLocked,
                        topPadding = 8.dp,
                        colors = colors,
                        onDateClick = { showStartDatePicker = true },
                        onTimeClick = { showStartTimePicker = true },
                    )

                    // 終了日時
                    DateTimeRow(
                        label = "終了",
                        date = endDate,
                        time = endTime,
                        isAllDay = isAllDay,
                        isDateLocked = endDateLocked,
                        topPadding = 16.dp,
                        colors = colors,
                        onDateClick = { showEndDatePicker = true },
                        onTimeClick = { showEndTimePicker = true },
                    )

                    // 繰り返し終了日
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
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text("終了日", color = colors.textGray, modifier = Modifier.width(LABEL_WIDTH))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .border(1.dp, colors.primaryAccent, RoundedCornerShape(8.dp))
                                        .clickable { showRecurrenceEndPicker = true }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                ) {
                                    Text(
                                        "${recurrenceEndDate.year}年${recurrenceEndDate.monthValue}月${recurrenceEndDate.dayOfMonth}日",
                                        color = colors.text,
                                    )
                                }
                            }
                        }
                    }
                }

                // 場所
                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it },
                    label = { Text("場所", color = colors.textGray) },
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        IconButton(onClick = { showPlacePicker = true }) {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = "地図から選択",
                                tint = colors.primaryAccent,
                            )
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = colors.text,
                        unfocusedTextColor = colors.text,
                        focusedBorderColor = colors.primaryAccent,
                        unfocusedBorderColor = colors.textGray,
                        cursorColor = colors.primaryAccent,
                    ),
                )

                // メモ
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("メモ / 内容", color = colors.textGray) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 2,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = colors.text,
                        unfocusedTextColor = colors.text,
                        focusedBorderColor = colors.primaryAccent,
                        unfocusedBorderColor = colors.textGray,
                        cursorColor = colors.primaryAccent,
                    ),
                )

                // 写真添付（Golendar モードのみ）
                if (photoAttachEnabled) {
                    PhotoAttachmentSection(
                        colors = colors,
                        existingPhotos = initialPhotos.filter { it.id in keptPhotoIds },
                        newPhotoUris = newPhotoUris,
                        onAddClick = {
                            photoPicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        onRemoveExisting = { photo -> keptPhotoIds = keptPhotoIds - photo.id },
                        onRemoveNew = { uri -> newPhotoUris = newPhotoUris - uri },
                    )
                }
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
                val finalTargetCalendarId = if (showCalendarSelector) selectedCalendarId else null
                onSave(
                    finalTitle, startMillis, endMillis, isAllDay, location, description, finalRrule,
                    newPhotoUris, keptPhotoIds.toList(), finalTargetCalendarId,
                )
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
        },
    )

    // ---------- ピッカー ----------
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
    if (showPlacePicker) {
        PlacePickerDialog(
            initialLatitude = null,
            initialLongitude = null,
            colors = colors,
            onDismiss = { showPlacePicker = false },
            onPlaceSelected = { picked ->
                location = picked
                showPlacePicker = false
            },
        )
    }
}