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
import com.monsivamon.golender.ui.theme.calendarColorPalette

// 個別カレンダーの色を選択するダイアログを表示する
@Composable
fun CalendarColorPickerDialog(
    calendarName: String,
    colors: AppColors,
    currentColorArgb: Int,
    onDismiss: () -> Unit,
    onColorSelected: (Int) -> Unit,
) {
    // 現在のテーマに応じた 16 色（ライト=濃いめ / ダーク=明るめ）
    val palette = colors.calendarColorPalette()

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        title = { Text("${calendarName} の色", color = colors.text, fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // 「カレンダーの色（既定）」= 0 を選ぶ行。
                // 既定時の実表示色（primaryAccent）を左に並べて、
                // 「何も選ばれていない」という誤解を避ける。
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
                    if (currentColorArgb == 0) {
                        Text("✓ 選択中", color = colors.primaryAccent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                // 4 列 x 4 行のカラースウォッチ
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    palette.chunked(4).forEach { rowColors ->
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