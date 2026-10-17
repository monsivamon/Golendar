package com.monsivamon.golender.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.monsivamon.golender.R
import com.monsivamon.golender.data.LocalCalendar
import com.monsivamon.golender.ui.theme.AppColors
import com.monsivamon.golender.ui.theme.calendarColorPalette

// ローカルカレンダーの編集ダイアログ（既定は削除不可）
@Composable
fun LocalCalendarEditDialog(
    calendar: LocalCalendar,
    colors: AppColors,
    onDismiss: () -> Unit,
    onSave: (LocalCalendar) -> Unit,
    onDelete: (Long) -> Unit,
) {
    // 編集中の名前。既定カレンダーの空文字センチネルは locale 名に展開した初期値を用いる
    // stringResource は非 Composable ラムダ（remember の計算内）から呼べないため外で解決する
    // （既定カレンダーは onValueChange で書き換わらないため、保存時にはセンチネルへ戻す）。
    val displayedDefaultName = stringResource(R.string.default_calendar_name)
    // 既定カレンダーの場合、locale 名を初期表示に使うが、DB へはセンチネル（空文字）を維持する
    val keepSentinel = calendar.isDefault && calendar.name.isEmpty()
    var name by remember(calendar.name) { mutableStateOf(if (keepSentinel) displayedDefaultName else calendar.name) }
    // 編集中の色。0 はテーマ連動（アクセント色）を意味する。
    var colorArgb by remember { mutableStateOf(calendar.colorArgb) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        title = { Text(stringResource(R.string.dl2_editCalendarTitle), color = colors.text, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { if (!calendar.isDefault) name = it },
                    label = { Text(stringResource(R.string.dl2_nameLabel)) },
                    singleLine = true,
                    enabled = !calendar.isDefault,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = colors.text,
                        unfocusedTextColor = colors.text,
                        focusedBorderColor = colors.primaryAccent,
                        unfocusedBorderColor = colors.textGray,
                        cursorColor = colors.primaryAccent,
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                if (calendar.isDefault) {
                    Text(
                        stringResource(R.string.dl2_defaultCalendarNameLocked),
                        color = colors.textGray,
                        fontSize = 12.sp,
                    )
                }
                Text(stringResource(R.string.dl2_colorLabel), color = colors.textGray, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                // 既定色（colorArgb = 0）を選ぶ行。
                // ここが無いと、既定色（テーマ連動）のカレンダーを編集した際に
                // どのスウォッチも選択されておらず、ユーザーが既定色へ戻せなくなる。
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .then(
                            if (colorArgb == 0) Modifier.border(2.dp, colors.primaryAccent, RoundedCornerShape(8.dp))
                            else Modifier
                        )
                        .clickable { colorArgb = 0 }
                        .padding(vertical = 12.dp, horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(colors.primaryAccent)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.dl2_calendarColorDefault), color = colors.text, fontSize = 15.sp)
                    }
                    if (colorArgb == 0) {
                        Text(stringResource(R.string.dl2_selectedMark), color = colors.primaryAccent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.height(16.dp))
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    colors.calendarColorPalette().chunked(4).forEach { rowColors ->
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            rowColors.forEach { baseColor ->
                                val argb = baseColor.toArgb()
                                val isSelected = colorArgb == argb
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
                                        .clickable { colorArgb = argb },
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
                //既定カレンダーは名前をセンチネル（空文字）のまま保持する
                val savedName = if (keepSentinel) calendar.name else name.trim()
                if (savedName.isNotBlank() || keepSentinel) {
                    onSave(calendar.copy(name = savedName, colorArgb = colorArgb))
                }
            }) { Text(stringResource(R.string.dl2_save), color = colors.primaryAccent, fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            Row {
                if (!calendar.isDefault) {
                    TextButton(onClick = { onDelete(calendar.id) }) { Text(stringResource(R.string.dl2_delete), color = colors.sunRed) }
                    Spacer(Modifier.width(8.dp))
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.dl2_cancel), color = colors.textGray) }
            }
        }
    )
}