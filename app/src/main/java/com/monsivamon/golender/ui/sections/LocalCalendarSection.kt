package com.monsivamon.golender.ui.sections

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.monsivamon.golender.R
import com.monsivamon.golender.data.LocalCalendar
import com.monsivamon.golender.ui.dialogs.CalendarColorPickerDialog
import com.monsivamon.golender.ui.dialogs.LocalCalendarEditDialog
import com.monsivamon.golender.ui.theme.AppColors
import com.monsivamon.golender.ui.theme.calendarColorPalette
import com.monsivamon.golender.ui.theme.resolveCalendarColor
import com.monsivamon.golender.viewmodel.CalendarViewModel

// 表示用のカレンダー名に解決する（既定カレンダーで名前が空文字なら locale 名へ）
@Composable
private fun LocalCalendar.resolvedName(): String =
    if (isDefault && name.isEmpty()) stringResource(R.string.default_calendar_name) else name

// 「カレンダーモード」セクション内に埋め込まれる Golendar カレンダー管理の中身。
// 見た目は Google モードのカレンダー一覧に寄せ、
//   - Checkbox で表示 ON/OFF
//   - 色丸タップで色変更（CalendarColorPickerDialog）
//   - 名前タップで編集（Golendar 固有）
// マイカレンダー（既定）は削除不可だが、非表示にはできる。
// 色が未設定(0) の場合は、リスト内のインデックスに応じて自動色を割り当てる。
@Composable
fun LocalCalendarSectionContent(
    viewModel: CalendarViewModel,
    colors: AppColors,
) {
    val localCalendars by viewModel.localCalendars.collectAsState()
    // 「最低1つは表示」ルール用: 現在の可視カレンダー数
    val visibleLocalCalendarCount = localCalendars.count { it.isVisible }
    val context = LocalContext.current
    var showAddDialog by remember { mutableStateOf(false) }
    var editingCalendar by remember { mutableStateOf<LocalCalendar?>(null) }
    var colorPickerCalendar by remember { mutableStateOf<LocalCalendar?>(null) }
    var pendingDeleteCalendar by remember { mutableStateOf<LocalCalendar?>(null) }

    Text(
        stringResource(R.string.sc_defaultCalendarNotDeletable),
        fontSize = 12.sp, color = colors.textGray, modifier = Modifier.padding(bottom = 8.dp)
    )

    // 一括表示／非表示ボタン（Google モードと同じ体裁）
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        TextButton(onClick = { viewModel.setAllLocalCalendarsVisible(true) }) {
            Text(stringResource(R.string.sc_showAll), color = colors.primaryAccent)
        }
    }

    // カレンダー一覧（Google モードと同じ体裁: Checkbox + 色丸 + 名前）
    localCalendars.forEachIndexed { index, cal ->
        val displayColor = colors.resolveCalendarColor(cal.colorArgb, index)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        ) {
            // 表示 ON/OFF チェックボックス
            Checkbox(
                checked = cal.isVisible,
                onCheckedChange = { checked ->
                    if (!checked && cal.isVisible && visibleLocalCalendarCount <= 1) {
                        Toast.makeText(context, context.getString(R.string.sc_minOneCalendar), Toast.LENGTH_SHORT).show()
                    } else {
                        viewModel.updateLocalCalendar(cal.copy(isVisible = checked))
                    }
                },
                colors = CheckboxDefaults.colors(checkedColor = colors.primaryAccent, uncheckedColor = colors.text),
            )
            // 色丸（タップで色変更）: 未設定なら自動色を表示する
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(displayColor)
                    .clickable { colorPickerCalendar = cal },
            )
            Spacer(modifier = Modifier.width(8.dp))
            // 名前（タップで編集ダイアログ）
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { editingCalendar = cal }
                    .padding(vertical = 4.dp, horizontal = 4.dp),
            ) {
                Text(
                    cal.resolvedName() + if (cal.isDefault) stringResource(R.string.sc_defaultSuffix) else "",
                    color = colors.text,
                    fontSize = 15.sp,
                )
            }
        }
    }

    Button(
        onClick = { showAddDialog = true },
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = colors.surface,
            contentColor = colors.primaryAccent,
        ),
    ) {
        Icon(Icons.Default.Add, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.sc_addCalendar))
    }

    // ---------- カレンダー追加ダイアログ ----------
    if (showAddDialog) {
        var newName by remember { mutableStateOf("") }
        var newColor by remember { mutableStateOf(colors.calendarColorPalette().random().toArgb()) }
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            containerColor = colors.surface,
            title = { Text(stringResource(R.string.sc_addCalendar), color = colors.text, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text(stringResource(R.string.sc_name)) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = colors.text, unfocusedTextColor = colors.text,
                            focusedBorderColor = colors.primaryAccent, unfocusedBorderColor = colors.textGray,
                            cursorColor = colors.primaryAccent,
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(stringResource(R.string.sc_color), color = colors.textGray, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        colors.calendarColorPalette().chunked(4).forEach { rowColors ->
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                rowColors.forEach { baseColor ->
                                    val argb = baseColor.toArgb()
                                    val isSelected = newColor == argb
                                    val checkColor = if (baseColor.luminance() > 0.5f) Color.Black else Color.White
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .aspectRatio(1f)
                                            .clip(CircleShape)
                                            .background(baseColor)
                                            .border(
                                                width = if (isSelected) 3.dp else 1.dp,
                                                color = if (isSelected) colors.primaryAccent else colors.textGray.copy(alpha = 0.4f),
                                                shape = CircleShape,
                                            )
                                            .clickable { newColor = argb },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        if (isSelected) {
                                            Text("✓", color = checkColor, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                                repeat(4 - rowColors.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newName.isNotBlank()) {
                        viewModel.addLocalCalendar(newName.trim(), newColor)
                        showAddDialog = false
                    }
                }) { Text(stringResource(R.string.sc_add), color = colors.primaryAccent, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) { Text(stringResource(R.string.sc_cancel), color = colors.textGray) }
            }
        )
    }

    // ---------- カレンダー編集ダイアログ ----------
    editingCalendar?.let { cal ->
        LocalCalendarEditDialog(
            calendar = cal,
            colors = colors,
            onDismiss = { editingCalendar = null },
            onSave = { updated ->
                viewModel.updateLocalCalendar(updated)
                editingCalendar = null
            },
            onDelete = { id ->
                // 削除前に所属予定の件数を数えて確認ダイアログを表示する
                pendingDeleteCalendar = cal
                editingCalendar = null
            }
        )
    }

    // ---------- 削除確認ダイアログ ----------
    pendingDeleteCalendar?.let { cal ->
        // 対象カレンダーに属する予定件数を非同期で取得する
        var eventCount by remember(cal.id) { mutableStateOf<Int?>(null) }
        LaunchedEffect(cal.id) {
            eventCount = viewModel.countEventsInLocalCalendar(cal.id)
        }

        AlertDialog(
            onDismissRequest = { pendingDeleteCalendar = null },
            containerColor = colors.surface,
            title = {
                Text(
                    stringResource(R.string.sc_deleteCalendarTitle),
                    color = colors.text, fontWeight = FontWeight.Bold,
                )
            },
            text = {
                Column {
                    Text(
                        stringResource(R.string.sc_deleteCalendarMessage, cal.resolvedName()),
                        color = colors.text, fontSize = 14.sp,
                    )
                    Spacer(Modifier.height(8.dp))
                    val count = eventCount
                    if (count == null) {
                        Text(
                            stringResource(R.string.sc_checkingEvents),
                            color = colors.textGray, fontSize = 13.sp,
                        )
                    } else if (count == 0) {
                        Text(
                            stringResource(R.string.sc_noEventsInCalendar),
                            color = colors.textGray, fontSize = 13.sp,
                        )
                    } else {
                        Text(
                            stringResource(R.string.sc_deleteWithEvents, count),
                            color = colors.sunRed, fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteLocalCalendar(cal.id)
                        pendingDeleteCalendar = null
                    },
                    enabled = eventCount != null,
                ) {
                    Text(stringResource(R.string.sc_delete), color = colors.sunRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteCalendar = null }) {
                    Text(stringResource(R.string.sc_cancel), color = colors.textGray)
                }
            },
        )
    }

    // ---------- 色変更ダイアログ（Google モードと同じものを流用） ----------
    colorPickerCalendar?.let { cal ->
        CalendarColorPickerDialog(
            calendarName = cal.resolvedName(),
            colors = colors,
            currentColorArgb = cal.colorArgb,
            onDismiss = { colorPickerCalendar = null },
            onColorSelected = { argb ->
                viewModel.updateLocalCalendar(cal.copy(colorArgb = argb))
                colorPickerCalendar = null
            },
        )
    }
}