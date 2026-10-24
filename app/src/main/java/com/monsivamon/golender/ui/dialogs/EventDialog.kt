package com.monsivamon.golender.ui.dialogs

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.monsivamon.golender.R
import com.monsivamon.golender.data.Event
import com.monsivamon.golender.data.EventPhoto
import com.monsivamon.golender.data.LocalCalendar
import com.monsivamon.golender.data.SelectedCalendar
import com.monsivamon.golender.data.util.RruleExpander
import com.monsivamon.golender.data.util.allDayMillisRange
import com.monsivamon.golender.data.util.isEndBeforeStart
import com.monsivamon.golender.data.util.resolveTimedEndDate
import com.monsivamon.golender.ui.common.GolendarDatePickerDialog
import com.monsivamon.golender.ui.common.GolendarTimePickerDialog
import com.monsivamon.golender.ui.dialogs.event.CalendarSelector
import com.monsivamon.golender.ui.dialogs.event.LocalCalendarSelector
import com.monsivamon.golender.ui.dialogs.event.DateTimeRow
import com.monsivamon.golender.ui.dialogs.event.PhotoAttachmentSection
import com.monsivamon.golender.ui.dialogs.event.RecurrenceChips
import com.monsivamon.golender.ui.theme.AppColors
import com.monsivamon.golender.data.prefs.AppTimeZone
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset

internal val LABEL_WIDTH: Dp = 60.dp

// 予定の追加・編集ダイアログ
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EventDialog(
    event: Event?,
    selectedDate: LocalDate,
    colors: AppColors,
    fromCalendar: Boolean = false,
    initialPhotos: List<EventPhoto> = emptyList(),
    photosLoaded: Boolean = true,
    photoAttachEnabled: Boolean = false,
    aiParseEnabled: Boolean = false,
    // Golendar モードのみ予定の任意色を設定できる
    eventColorEnabled: Boolean = false,
    availableCalendars: List<SelectedCalendar> = emptyList(),
    localCalendars: List<LocalCalendar> = emptyList(),
    onDismiss: () -> Unit,
    onSave: (
        title: String, startMillis: Long, endMillis: Long, isAllDay: Boolean,
        location: String, description: String, rrule: String?,
        colorArgb: Int,
        newPhotoUris: List<Uri>, keptPhotoIds: List<Long>,
        targetCalendarId: Long?,
        localCalendarId: Long?,
    ) -> Unit,
    onDelete: (Event) -> Unit,
    onAiParse: (Long?) -> Unit,
) {
    var title by remember(event) { mutableStateOf(event?.title ?: "") }
    val context = LocalContext.current
    var location by remember(event) { mutableStateOf(event?.location ?: "") }
    var description by remember(event) { mutableStateOf(event?.description ?: "") }
    var isAllDay by remember(event) { mutableStateOf(event?.isAllDay ?: false) }
    val eventKey = event?.id ?: -1L

    var selectedCalendarId by remember(eventKey, availableCalendars) {
        mutableStateOf(
            event?.calendarId?.takeIf { it > 0L }
                ?: availableCalendars.firstOrNull()?.calendarId
        )
    }
    val showCalendarSelector = availableCalendars.size >= 2

    // Golendar モード用ローカルカレンダー選択
    // 初期選択は「可視かつ既定 → 可視の先頭 → 既定 → 先頭（ fallback）」の優先順位。
    // 既定カレンダーが非表示のときに見えない場所へ保存される事故を防ぐ。
    var selectedLocalCalendarId by remember(eventKey, localCalendars) {
        mutableStateOf(
            event?.calendarId?.takeIf { it > 0L && localCalendars.any { c -> c.id == it } }
                ?: localCalendars.firstOrNull { it.isVisible && it.isDefault }?.id
                ?: localCalendars.firstOrNull { it.isVisible }?.id
                ?: localCalendars.firstOrNull { it.isDefault }?.id
                ?: localCalendars.firstOrNull()?.id
                ?: 0L
        )
    }
    val showLocalCalendarSelector =
        availableCalendars.isEmpty() && localCalendars.count { it.isVisible } >= 2

    var isRecurring by remember(eventKey) { mutableStateOf(event?.rrule != null) }
    // 予定ごとの任意色（0 = 未設定で既定色）
    var eventColorArgb by remember(eventKey) { mutableStateOf(event?.colorArgb ?: 0) }
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
        mutableStateOf(parsed ?: LocalDate.now(AppTimeZone.effective()).plusMonths(1))
    }

    // 終日=UTC / 時間指定=表示タイムゾーン基準で日時を解釈・保存する
    val initialZone = if (event?.isAllDay == true) ZoneOffset.UTC else AppTimeZone.effective()
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

    var showStartDatePicker by remember { mutableStateOf(false) }
    var showEndDatePicker by remember { mutableStateOf(false) }
    var showStartTimePicker by remember { mutableStateOf(false) }
    var showEndTimePicker by remember { mutableStateOf(false) }
    var showRecurrenceEndPicker by remember { mutableStateOf(false) }
    var showPlacePicker by remember { mutableStateOf(false) }
    var showEventColorPicker by remember { mutableStateOf(false) }

    var keptPhotoIds by remember(event?.id, initialPhotos) {
        mutableStateOf(if (event == null) emptySet() else initialPhotos.map { it.id }.toSet())
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
    // 繰り返しで終了日を固定している場合は開始日を基準にする
    val baseEndDate = if (endDateLocked) startDate else endDate
    // 時間指定で終了時刻が開始時刻より前なら翌日にまたぐ予定として扱う（23:50〜01:30 など）
    val effectiveEndDate =
        if (isAllDay) baseEndDate else resolveTimedEndDate(startDate, startTime, baseEndDate, endTime)
    // 終了日は開始日以降（時間指定は終了日時が開始日時以降）でなければ保存させない
    val endBeforeStart = isEndBeforeStart(startDate, startTime, effectiveEndDate, endTime, isAllDay)
    // 繰り返しの終了日が開始日より前だと展開結果が 0 件になり、予定が表示されない
    val recurrenceEndBeforeStart =
        isRecurring && hasRecurrenceEnd && recurrenceEndDate.isBefore(startDate)
    val canSave = (event == null || photosLoaded) && !endBeforeStart && !recurrenceEndBeforeStart

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        title = { Text(if (event == null) stringResource(R.string.dl2_addEventTitle) else stringResource(R.string.dl2_editEventTitle), color = colors.text) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (showCalendarSelector) {
                    CalendarSelector(
                        calendars = availableCalendars,
                        selectedId = selectedCalendarId,
                        colors = colors,
                        onSelect = { selectedCalendarId = it },
                    )
                }
                if (showLocalCalendarSelector) {
                    LocalCalendarSelector(
                        calendars = localCalendars.filter { it.isVisible },
                        selectedId = selectedLocalCalendarId,
                        colors = colors,
                        onSelect = { selectedLocalCalendarId = it },
                    )
                }

                if (aiParseEnabled) {
                    // LocalCalendarSelector で選択中のローカルカレンダーを
                    // AI 解析結果の登録先として引き渡す（null なら既定カレンダー）。
                    TextButton(onClick = { onAiParse(selectedLocalCalendarId.takeIf { it > 0L }) }) {
                        Text(stringResource(R.string.dl2_aiParseTitle), color = colors.primaryAccent)
                    }
                }

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.dl2_titleLabel), color = colors.textGray) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = colors.text, unfocusedTextColor = colors.text,
                        focusedBorderColor = colors.primaryAccent, unfocusedBorderColor = colors.textGray,
                        cursorColor = colors.primaryAccent,
                    ),
                )

                if (eventColorEnabled) {
                    // 予定の色。既定（未設定）は紫で表示し、タップでパレットを開く
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { showEventColorPicker = true }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            stringResource(R.string.dl2_eventColorLabel),
                            color = colors.textGray, fontSize = 14.sp,
                            modifier = Modifier.width(LABEL_WIDTH),
                        )
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(if (eventColorArgb != 0) Color(eventColorArgb) else colors.primaryAccent)
                                .border(1.dp, colors.textGray.copy(alpha = 0.4f), CircleShape),
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            stringResource(
                                if (eventColorArgb == 0) R.string.dl2_eventColorDefault
                                else R.string.dl2_eventColorChange
                            ),
                            color = colors.primaryAccent, fontSize = 14.sp,
                        )
                    }
                }

                Column(modifier = Modifier.fillMaxWidth()) {
                    val checkboxColors = CheckboxDefaults.colors(
                        checkedColor = colors.primaryAccent,
                        uncheckedColor = colors.text,
                        checkmarkColor = if (isLightBackground) Color.White else Color(0xFF1A1A1A),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = isAllDay, onCheckedChange = { isAllDay = it }, colors = checkboxColors)
                        Text(stringResource(R.string.dl2_allDay), color = colors.text)
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
                        Text(stringResource(R.string.dl2_recurring), color = colors.text)
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
                            stringResource(R.string.dl2_dateLockedNote),
                            color = colors.textGray, fontSize = 11.sp,
                            modifier = Modifier.padding(start = 12.dp, top = 4.dp, bottom = 4.dp),
                        )
                    }

                    DateTimeRow(
                        label = stringResource(R.string.dl2_startLabel), date = startDate, time = startTime, isAllDay = isAllDay,
                        isDateLocked = startDateLocked, topPadding = 8.dp, colors = colors,
                        onDateClick = { showStartDatePicker = true },
                        onTimeClick = { showStartTimePicker = true },
                    )
                    DateTimeRow(
                        label = stringResource(R.string.dl2_endLabel), date = effectiveEndDate, time = endTime, isAllDay = isAllDay,
                        isDateLocked = endDateLocked, topPadding = 16.dp, colors = colors,
                        onDateClick = { showEndDatePicker = true },
                        onTimeClick = { showEndTimePicker = true },
                    )
                    // 終了が開始より前のときは保存できない理由を表示する
                    if (endBeforeStart) {
                        Text(
                            stringResource(R.string.dl2_endBeforeStart),
                            color = colors.sunRed, fontSize = 11.sp,
                            modifier = Modifier.padding(start = 12.dp, top = 6.dp),
                        )
                    }

                    if (isRecurring) {
                        Text(
                            stringResource(R.string.dl2_recurringDurationNote),
                            color = colors.textGray, fontSize = 11.sp,
                            modifier = Modifier.padding(start = (12 + 60 + 8).dp, top = 6.dp),
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = hasRecurrenceEnd,
                                onCheckedChange = { hasRecurrenceEnd = it },
                                colors = checkboxColors,
                            )
                            Text(stringResource(R.string.dl2_specifyRecurrenceEnd), color = colors.text, fontSize = 14.sp)
                        }
                        if (hasRecurrenceEnd) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(start = 12.dp, top = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(stringResource(R.string.dl2_recurrenceEndDate), color = colors.textGray, modifier = Modifier.width(LABEL_WIDTH))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .border(1.dp, colors.primaryAccent, RoundedCornerShape(8.dp))
                                        .clickable { showRecurrenceEndPicker = true }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                ) {
                                    Text(
                                        stringResource(
                                            R.string.dl2_dateYmd,
                                            recurrenceEndDate.year,
                                            recurrenceEndDate.monthValue,
                                            recurrenceEndDate.dayOfMonth,
                                        ),
                                        color = colors.text,
                                    )
                                }
                            }
                            // 開始日より前の繰り返し終了日は受け付けない
                            if (recurrenceEndBeforeStart) {
                                Text(
                                    stringResource(R.string.dl2_recurrenceEndBeforeStart),
                                    color = colors.sunRed, fontSize = 11.sp,
                                    modifier = Modifier.padding(start = 12.dp, top = 4.dp),
                                )
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it },
                    label = { Text(stringResource(R.string.dl2_locationLabel), color = colors.textGray) },
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        IconButton(onClick = { showPlacePicker = true }) {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = stringResource(R.string.dl2_pickFromMap),
                                tint = colors.primaryAccent,
                            )
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = colors.text, unfocusedTextColor = colors.text,
                        focusedBorderColor = colors.primaryAccent, unfocusedBorderColor = colors.textGray,
                        cursorColor = colors.primaryAccent,
                    ),
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource(R.string.dl2_memoLabel), color = colors.textGray) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2, maxLines = 2,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = colors.text, unfocusedTextColor = colors.text,
                        focusedBorderColor = colors.primaryAccent, unfocusedBorderColor = colors.textGray,
                        cursorColor = colors.primaryAccent,
                    ),
                )

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
            TextButton(enabled = canSave, onClick = {
                val finalTitle = title.ifBlank { context.getString(R.string.dl2_unnamedCalendar) }
                // 終日は終了日を「その日を含む」日付として扱い、モデル上の exclusive な翌日 0 時へ変換する
                val (startMillis, endMillis) = if (isAllDay) {
                    val range = allDayMillisRange(startDate, effectiveEndDate)
                    range.start to range.end
                } else {
                    val saveZone = AppTimeZone.effective()
                    startDate.atTime(startTime).atZone(saveZone).toInstant().toEpochMilli() to
                        effectiveEndDate.atTime(endTime).atZone(saveZone).toInstant().toEpochMilli()
                }
                val finalRrule = when {
                    !isRecurring -> null
                    else -> {
                        val base = if (recurringType == "WEEKDAYS") RruleExpander.RRULE_WEEKDAYS else "FREQ=$recurringType"
                        if (hasRecurrenceEnd) "$base;UNTIL=${RruleExpander.formatUntil(recurrenceEndDate)}" else base
                    }
                }
                val finalTargetCalendarId = if (showCalendarSelector) selectedCalendarId else null
                // 単一カレンダー時も選択中カレンダーを必ず渡す。
                // null を渡すと EventCrudManager 側で 0L にフォールバックし、
                // 可視カレンダーのフィルタに弾かれて予定が表示されなくなるため。
                val finalLocalCalendarId = selectedLocalCalendarId.takeIf { it > 0L }
                onSave(
                    finalTitle, startMillis, endMillis, isAllDay, location, description, finalRrule,
                    if (eventColorEnabled) eventColorArgb else 0,
                    newPhotoUris, keptPhotoIds.toList(), finalTargetCalendarId, finalLocalCalendarId,
                )
            }) {
                Text(stringResource(R.string.dl2_save), color = if (canSave) colors.primaryAccent else colors.textGray, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (event != null) {
                    TextButton(onClick = { onDelete(event) }) { Text(stringResource(R.string.dl2_delete), color = colors.sunRed) }
                    Spacer(modifier = Modifier.width(8.dp))
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.dl2_cancel), color = colors.textGray) }
            }
        },
    )

    if (showStartDatePicker) {
        GolendarDatePickerDialog(
            initialDate = startDate, colors = colors,
            onDismiss = { showStartDatePicker = false },
            onDateSelected = { newDate ->
                startDate = newDate
                if (newDate.isAfter(endDate)) endDate = newDate
            },
        )
    }
    if (showEndDatePicker) {
        GolendarDatePickerDialog(
            initialDate = endDate, colors = colors,
            onDismiss = { showEndDatePicker = false },
            onDateSelected = { newDate ->
                endDate = newDate
                if (newDate.isBefore(startDate)) startDate = newDate
            },
        )
    }
    if (showStartTimePicker) {
        GolendarTimePickerDialog(
            initialTime = startTime, colors = colors,
            onDismiss = { showStartTimePicker = false },
            onTimeSelected = { startTime = it },
        )
    }
    if (showEndTimePicker) {
        GolendarTimePickerDialog(
            initialTime = endTime, colors = colors,
            onDismiss = { showEndTimePicker = false },
            onTimeSelected = { endTime = it },
        )
    }
    if (showRecurrenceEndPicker) {
        GolendarDatePickerDialog(
            initialDate = recurrenceEndDate, colors = colors,
            onDismiss = { showRecurrenceEndPicker = false },
            onDateSelected = { newDate ->
                recurrenceEndDate = newDate
                showRecurrenceEndPicker = false
            },
        )
    }
    if (showPlacePicker) {
        PlacePickerDialog(
            initialLatitude = null, initialLongitude = null, colors = colors,
            onDismiss = { showPlacePicker = false },
            onPlaceSelected = { picked ->
                location = picked
                showPlacePicker = false
            },
        )
    }
    if (showEventColorPicker) {
        EventColorPickerDialog(
            colors = colors,
            currentColorArgb = eventColorArgb,
            onDismiss = { showEventColorPicker = false },
            onColorSelected = { eventColorArgb = it; showEventColorPicker = false },
        )
    }
}