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
    // 編集中の名前（既定カレンダーは変更不可だが、初期値として保持する）
    var name by remember { mutableStateOf(calendar.name) }
    // 編集中の色。0 はテーマ連動（アクセント色）を意味する。
    var colorArgb by remember { mutableStateOf(calendar.colorArgb) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        title = { Text("カレンダーの編集", color = colors.text, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { if (!calendar.isDefault) name = it },
                    label = { Text("名前") },
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
                        "マイカレンダーは名前を変更できません（色のみ変更できます）。",
                        color = colors.textGray,
                        fontSize = 12.sp,
                    )
                }
                Text("色", color = colors.textGray, fontSize = 14.sp, fontWeight = FontWeight.Bold)
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
                        Text("カレンダーの色（既定）", color = colors.text, fontSize = 15.sp)
                    }
                    if (colorArgb == 0) {
                        Text("✓ 選択中", color = colors.primaryAccent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
                if (name.isNotBlank()) {
                    onSave(calendar.copy(name = name.trim(), colorArgb = colorArgb))
                }
            }) { Text("保存", color = colors.primaryAccent, fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            Row {
                if (!calendar.isDefault) {
                    TextButton(onClick = { onDelete(calendar.id) }) { Text("削除", color = colors.sunRed) }
                    Spacer(Modifier.width(8.dp))
                }
                TextButton(onClick = onDismiss) { Text("キャンセル", color = colors.textGray) }
            }
        }
    )
}