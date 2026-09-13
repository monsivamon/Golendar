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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.monsivamon.golender.data.util.jpShort
import com.monsivamon.golender.ui.theme.AppColors
import com.monsivamon.golender.ui.theme.DayColorPalette
import com.monsivamon.golender.ui.theme.PastelColorPalette
import com.monsivamon.golender.ui.theme.previewColor
import com.monsivamon.golender.viewmodel.ThemeMode
import java.time.DayOfWeek

// アプリ全体の背景色を選択するダイアログを表示する。
@Composable
fun BackgroundColorPickerDialog(
    colors: AppColors,
    themeMode: ThemeMode,
    isSystemDark: Boolean,
    onDismiss: () -> Unit,
    onColorSelected: (Color) -> Unit,
) {
    val isDarkTheme = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemDark
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        title = { Text("アプリ背景色を選択", color = colors.text, fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {

                Text(
                    text = if (isDarkTheme)
                        "ダークモードでは選択した色が自動的に暗く表示されます。"
                    else
                        "同じ色相のまま、ダークモード時は暗い背景になります。",
                    color = colors.textGray,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(bottom = 12.dp),
                )

                StandardRow(colors) {
                    onColorSelected(Color.Unspecified)
                    onDismiss()
                }

                Spacer(Modifier.height(12.dp))

                ColorSwatchGrid(
                    palette = PastelColorPalette,
                    isDarkTheme = isDarkTheme,
                ) { baseColor ->
                    onColorSelected(baseColor)
                    onDismiss()
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("閉じる", color = colors.textGray) }
        },
    )
}

// 指定曜日の文字色を選択するダイアログを表示する。
@Composable
fun DayColorPickerDialog(
    day: DayOfWeek,
    colors: AppColors,
    onDismiss: () -> Unit,
    onColorSelected: (Color) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        title = { Text("${day.jpShort()}曜日の色", color = colors.text, fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                StandardRow(colors) {
                    onColorSelected(Color.Unspecified)
                    onDismiss()
                }
                Spacer(Modifier.height(12.dp))
                ColorSwatchGrid(
                    palette = DayColorPalette,
                    isDarkTheme = false,
                ) { c ->
                    onColorSelected(c)
                    onDismiss()
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("閉じる", color = colors.textGray) }
        },
    )
}

// カスタム色を解除する「標準（カスタムなし）」行を表示する。
@Composable
private fun StandardRow(colors: AppColors, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("標準（カスタムなし）", color = colors.text, fontSize = 15.sp)
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .border(1.dp, colors.textGray, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text("/", color = colors.textGray, fontSize = 14.sp)
        }
    }
}

// 4列の色見本グリッドを表示し、選択された色をコールバックする。
@Composable
private fun ColorSwatchGrid(
    palette: List<Color>,
    isDarkTheme: Boolean,
    onColorSelected: (Color) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        palette.chunked(4).forEach { rowColors ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                rowColors.forEach { baseColor ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clip(CircleShape)
                            .background(previewColor(baseColor, isDarkTheme))
                            .clickable { onColorSelected(baseColor) },
                    )
                }
                repeat(4 - rowColors.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}