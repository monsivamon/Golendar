package com.monsivamon.golender.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.monsivamon.golender.ui.theme.AppColors

// 個別カレンダーの色を選択するダイアログを表示する
@Composable
fun CalendarColorPickerDialog(
    calendarName: String,
    colors: AppColors,
    currentColorArgb: Int,
    onDismiss: () -> Unit,
    onColorSelected: (Int) -> Unit,
) {
    // 選択候補となるパステル 16 色
    val pastelColors = listOf(
        Color(0xFFFFB3BA), Color(0xFFFFDFBA), Color(0xFFFFFFBA), Color(0xFFBAFFC9),
        Color(0xFFBAE1FF), Color(0xFFE6B3FF), Color(0xFFFFC6FF), Color(0xFFC4FAF8),
        Color(0xFFA0E8AF), Color(0xFFFFD1DC), Color(0xFFFDFD96), Color(0xFFE2F0CB),
        Color(0xFFB5EAD7), Color(0xFFC7CEEA), Color(0xFFF4C2C2), Color(0xFFFDECDA)
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        title = { Text("${calendarName} の色", color = colors.text, fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // 「カレンダーの色（既定）」= 0 を選ぶ行
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .then(
                            if (currentColorArgb == 0) Modifier.border(2.dp, colors.primaryAccent, RoundedCornerShape(8.dp))
                            else Modifier
                        )
                        .clickable { onColorSelected(0) }
                        .padding(vertical = 12.dp, horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("カレンダーの色（既定）", color = colors.text, fontSize = 15.sp)
                    if (currentColorArgb == 0) {
                        Text("✓ 選択中", color = colors.primaryAccent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                // 4 列 x 4 行のカラースウォッチ
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    pastelColors.chunked(4).forEach { rowColors ->
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            rowColors.forEach { baseColor ->
                                val argb = baseColor.toArgb()
                                val isSelected = currentColorArgb == argb
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
                                        .clickable { onColorSelected(argb) },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    // 選択中はチェックマークを表示する
                                    if (isSelected) {
                                        Text(
                                            text = "✓",
                                            color = checkColor,
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.Bold,
                                        )
                                    }
                                }
                            }
                            // 余り列をスペーサで埋める
                            repeat(4 - rowColors.size) {
                                Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("閉じる", color = colors.textGray) }
        }
    )
}