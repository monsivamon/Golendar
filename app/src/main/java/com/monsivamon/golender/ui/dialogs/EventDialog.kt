package com.monsivamon.golender.ui.dialogs

import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.monsivamon.golender.data.Event
import com.monsivamon.golender.data.EventPhoto
import com.monsivamon.golender.data.util.PhotoStorage
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

// ログタグとラベル幅の共通定数
private const val TAG = "Golendar"
private val LABEL_WIDTH = 60.dp

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
    // Golendar モード時のみ AI 解析ボタンを表示するフラグ
    aiParseEnabled: Boolean = false,
    onDismiss: () -> Unit,
    onSave: (
        title: String, startMillis: Long, endMillis: Long, isAllDay: Boolean,
        location: String, description: String, rrule: String?,
        newPhotoUris: List<Uri>, keptPhotoIds: List<Long>,
    ) -> Unit,
    onDelete: (Event) -> Unit,
    onAiParse: () -> Unit
) {
    // 基本入力項目（タイトル・場所・メモ・終日フラグ）の状態
    var title by remember(event) { mutableStateOf(event?.title ?: "") }
    var location by remember(event) { mutableStateOf(event?.location ?: "") }
    var description by remember(event) { mutableStateOf(event?.description ?: "") }
    var isAllDay by remember(event) { mutableStateOf(event?.isAllDay ?: false) }

    // 既存予定の ID（新規時は -1）
    val eventKey = event?.id ?: -1L

    // 繰り返し ON/OFF と種別（DAILY/WEEKDAYS/WEEKLY/MONTHLY/YEARLY）
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

    // 繰り返しの終了日指定 ON/OFF と日付
    var hasRecurrenceEnd by remember(eventKey) { mutableStateOf(RruleExpander.parseUntilDate(event?.rrule) != null) }
    var recurrenceEndDate by remember(eventKey) {
        val parsed = RruleExpander.parseUntilDate(event?.rrule)
        mutableStateOf(parsed ?: LocalDate.now().plusMonths(1))
    }

    // 繰り返し種別の選択肢（値とラベル）
    val recurringOptions = listOf(
        "DAILY" to "毎日",
        "WEEKDAYS" to "平日",
        "WEEKLY" to "毎週",
        "MONTHLY" to "毎月",
        "YEARLY" to "毎年",
    )

    // 既存予定の開始/終了から初期日時を算出する
    val initialZone = if (event?.isAllDay == true) ZoneOffset.UTC else ZoneId.systemDefault()
    val initialStart = event?.let { LocalDateTime.ofInstant(Instant.ofEpochMilli(it.startTime), initialZone) }
        ?: selectedDate.atTime(10, 0)
    val initialEnd = event?.let {
        val adjustedEnd = if (it.isAllDay && it.endTime > it.startTime) it.endTime - 1 else it.endTime
        LocalDateTime.ofInstant(Instant.ofEpochMilli(adjustedEnd), initialZone)
    } ?: selectedDate.atTime(11, 0)

    // 開始・終了の日付と時刻の状態
    var startDate by remember(eventKey, selectedDate) { mutableStateOf(initialStart.toLocalDate()) }
    var startTime by remember(eventKey, selectedDate) { mutableStateOf(initialStart.toLocalTime()) }
    var endDate by remember(eventKey, selectedDate) { mutableStateOf(initialEnd.toLocalDate()) }
    var endTime by remember(eventKey, selectedDate) { mutableStateOf(initialEnd.toLocalTime()) }

    // 各ピッカー・場所選択ダイアログの表示フラグ
    var showStartDatePicker by remember { mutableStateOf(false) }
    var showEndDatePicker by remember { mutableStateOf(false) }
    var showStartTimePicker by remember { mutableStateOf(false) }
    var showEndTimePicker by remember { mutableStateOf(false) }
    var showRecurrenceEndPicker by remember { mutableStateOf(false) }
    var showPlacePicker by remember { mutableStateOf(false) }

    // 添付写真の既存 ID 集合と新規追加 URI リスト
    var keptPhotoIds by remember(event?.id, initialPhotos) {
        mutableStateOf(initialPhotos.map { it.id }.toSet())
    }
    var newPhotoUris by remember(event?.id) { mutableStateOf<List<Uri>>(emptyList()) }

    // 写真ピッカー（最大 5 枚まで、既存分を差し引いて追加）
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

    // 背景輝度でチェックマーク色などを見分ける
    val isLightBackground = colors.bg.luminance() > 0.5f

    // カレンダー起点の場合は日付をロック（繰り返し時は終了日もロック）
    val startDateLocked = fromCalendar
    val endDateLocked = fromCalendar && isRecurring

    // メインの予定編集ダイアログ枠
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        title = { Text(if (event == null) "予定の追加" else "予定の編集", color = colors.text) },
        text = {
            // 縦スクロールの入力フォーム本体
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Golendar モード時のみ AI 解析ダイアログを開くボタンを表示する
                if (aiParseEnabled) {
                    TextButton(onClick = onAiParse) {
                        Text("AIに読み取らせる (BETA)", color = colors.primaryAccent)
                    }
                }

                // タイトル入力欄
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

                // 終日・繰り返しチェックと繰り返し種別の選択 UI
                Column(modifier = Modifier.fillMaxWidth()) {
                    val checkboxColors = CheckboxDefaults.colors(
                        checkedColor = colors.primaryAccent,
                        uncheckedColor = colors.text,
                        checkmarkColor = if (isLightBackground) Color.White else Color(0xFF1A1A1A),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // 終日チェック
                        Checkbox(
                            checked = isAllDay,
                            onCheckedChange = { isAllDay = it },
                            colors = checkboxColors,
                        )
                        Text("終日", color = colors.text)
                        Spacer(modifier = Modifier.width(16.dp))
                        // 繰り返しチェック（カレンダー起点時は終了日を同期させる）
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

                    // 繰り返し種別（毎日/平日/毎週/毎月/毎年）の選択チップ
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

                    // カレンダー起点時の日付ロック注意書き
                    if (startDateLocked) {
                        Text(
                            "※カレンダーで選んだ日付に追加されます（日付は変更できません）",
                            color = colors.textGray,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(start = 12.dp, top = 4.dp, bottom = 4.dp),
                        )
                    }

                    // 開始日時行（日付＋時刻、ロック時はグレーアウト）
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
                            // 開始日付ボックス（タップで日付ピッカー）
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
                            // 終日でない場合のみ開始時刻を表示する
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

                    // 終了日時行（日付＋時刻、ロック時はグレーアウト）
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
                            // 終了日付ボックス（タップで日付ピッカー）
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
                            // 終日でない場合のみ終了時刻を表示する
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

                    // 繰り返しの終了日指定（チェック＋日付選択）
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
                        // 終了日指定 ON のとき日付ボックスを表示する
                        if (hasRecurrenceEnd) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(start = 12.dp, top = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("終了日", color = colors.textGray, modifier = Modifier.width(LABEL_WIDTH))
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

                // 場所入力欄（右端のアイコンで地図ピッカーを起動）
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
                    )
                )

                // メモ／内容入力欄
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
                    )
                )

                // Golendar モードのみ写真添付セクションを表示する
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
            // 保存ボタン（終日/時間指定のミリ秒換算と RRULE 組立を行う）
            TextButton(onClick = {
                val effectiveEndDate = if (endDateLocked) startDate else endDate
                val finalTitle = title.ifBlank { "名称未設定" }
                val saveZone = if (isAllDay) ZoneOffset.UTC else ZoneId.systemDefault()
                val startDateTime = startDate.atTime(if (isAllDay) LocalTime.MIDNIGHT else startTime)
                val endDateTime = effectiveEndDate.atTime(if (isAllDay) LocalTime.MIDNIGHT else endTime)
                val startMillis = startDateTime.atZone(saveZone).toInstant().toEpochMilli()
                val endMillis = endDateTime.atZone(saveZone).toInstant().toEpochMilli()
                // 繰り返し ON のときのみ RRULE を組み立てる
                val finalRrule = when {
                    !isRecurring -> null
                    else -> {
                        val base = if (recurringType == "WEEKDAYS") RruleExpander.RRULE_WEEKDAYS else "FREQ=$recurringType"
                        if (hasRecurrenceEnd) "$base;UNTIL=${RruleExpander.formatUntil(recurrenceEndDate)}" else base
                    }
                }
                onSave(
                    finalTitle, startMillis, endMillis, isAllDay, location, description, finalRrule,
                    newPhotoUris, keptPhotoIds.toList(),
                )
            }) {
                Text("保存", color = colors.primaryAccent, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            // 削除（既存時のみ）／キャンセルボタン
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (event != null) {
                    TextButton(onClick = { onDelete(event) }) { Text("削除", color = colors.sunRed) }
                    Spacer(modifier = Modifier.width(8.dp))
                }
                TextButton(onClick = onDismiss) { Text("キャンセル", color = colors.textGray) }
            }
        }
    )

    // 開始日ピッカー（変更時は終了日を自動補正）
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

    // 終了日ピッカー（変更時は開始日を自動補正）
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

    // 開始時刻ピッカー
    if (showStartTimePicker) {
        GolendarTimePickerDialog(
            initialTime = startTime,
            colors = colors,
            onDismiss = { showStartTimePicker = false },
            onTimeSelected = { startTime = it },
        )
    }

    // 終了時刻ピッカー
    if (showEndTimePicker) {
        GolendarTimePickerDialog(
            initialTime = endTime,
            colors = colors,
            onDismiss = { showEndTimePicker = false },
            onTimeSelected = { endTime = it },
        )
    }

    // 繰り返し終了日ピッカー
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

    // 場所ピッカー（地図から住所を選択して location へ反映）
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

// 予定に添付する写真の選択・プレビューセクションを描画する
@Composable
private fun PhotoAttachmentSection(
    colors: AppColors,
    existingPhotos: List<EventPhoto>,
    newPhotoUris: List<Uri>,
    onAddClick: () -> Unit,
    onRemoveExisting: (EventPhoto) -> Unit,
    onRemoveNew: (Uri) -> Unit,
) {
    // コンテキストと合計枚数（既存＋新規）を取得する
    val context = LocalContext.current
    val total = existingPhotos.size + newPhotoUris.size

    Column(modifier = Modifier.fillMaxWidth()) {
        // 見出し（写真ラベルと枚数カウンタ）
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("写真", color = colors.textGray, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Spacer(Modifier.width(8.dp))
            Text("$total / ${EventPhoto.MAX_PHOTOS_PER_EVENT}", color = colors.textGray, fontSize = 12.sp)
        }
        Spacer(Modifier.height(6.dp))

        // 既存写真・新規 URI・「+」ボタンを横スクロールで並べる
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(existingPhotos, key = { "existing_${it.id}" }) { photo ->
                PhotoThumbnail(
                    colors = colors,
                    onRemove = { onRemoveExisting(photo) },
                ) {
                    val file = PhotoStorage.getFile(context, photo.fileName)
                    AsyncImage(
                        model = file,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            items(newPhotoUris, key = { "new_${it}" }) { uri ->
                PhotoThumbnail(
                    colors = colors,
                    onRemove = { onRemoveNew(uri) },
                ) {
                    AsyncImage(
                        model = uri,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            // 上限未満なら写真追加ボタンを表示する
            if (total < EventPhoto.MAX_PHOTOS_PER_EVENT) {
                item {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .border(1.dp, colors.textGray, RoundedCornerShape(8.dp))
                            .clickable(onClick = onAddClick),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("+", color = colors.primaryAccent, fontSize = 28.sp)
                    }
                }
            }
        }
        // 保存仕様の注記
        Text(
            "写真はアプリ内に保存されます（長辺 ${PhotoStorage.MAX_LONG_EDGE}px に縮小）",
            color = colors.textGray, fontSize = 11.sp,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

// 添付写真のサムネイル枠（右上に削除ボタン付き）を描画する
@Composable
private fun PhotoThumbnail(
    colors: AppColors,
    onRemove: () -> Unit,
    onClick: () -> Unit = {},
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(80.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(colors.bg)
            .border(1.dp, colors.divider, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
    ) {
        content()
        // 右上の×ボタンで写真を削除する
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(2.dp)
                .size(20.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.6f))
                .clickable(onClick = onRemove),
            contentAlignment = Alignment.Center,
        ) {
            Text("×", color = Color.White, fontSize = 12.sp)
        }
    }
}