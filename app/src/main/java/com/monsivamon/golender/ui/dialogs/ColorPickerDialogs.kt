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
    currentColor: Color,
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

                StandardRow(
                    colors = colors,
                    isSelected = currentColor == Color.Unspecified,
                ) {
                    onColorSelected(Color.Unspecified)
                    onDismiss()
                }

                Spacer(Modifier.height(12.dp))

                ColorSwatchGrid(
                    palette = PastelColorPalette,
                    colors = colors,
                    isDarkTheme = isDarkTheme,
                    currentColor = currentColor,
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
    currentColor: Color,
    onDismiss: () -> Unit,
    onColorSelected: (Color) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        title = { Text("${day.jpShort()}曜日の色", color = colors.text, fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                StandardRow(
                    colors = colors,
                    isSelected = currentColor == Color.Unspecified,
                ) {
                    onColorSelected(Color.Unspecified)
                    onDismiss()
                }
                Spacer(Modifier.height(12.dp))
                ColorSwatchGrid(
                    palette = DayColorPalette,
                    colors = colors,
                    isDarkTheme = false,
                    currentColor = currentColor,
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
private fun StandardRow(
    colors: AppColors,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .then(
                if (isSelected) {
                    Modifier.border(2.dp, colors.primaryAccent, RoundedCornerShape(8.dp))
                } else Modifier
            )
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("標準（カスタムなし）", color = colors.text, fontSize = 15.sp)
            if (isSelected) {
                Spacer(Modifier.width(8.dp))
                Text("✓ 選択中", color = colors.primaryAccent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
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
    colors: AppColors,
    isDarkTheme: Boolean,
    currentColor: Color,
    onColorSelected: (Color) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        palette.chunked(4).forEach { rowColors ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                rowColors.forEach { baseColor ->
                    val previewed = previewColor(baseColor, isDarkTheme)
                    val isSelected = currentColor == baseColor

                    val checkColor = if (previewed.luminance() > 0.5f) Color.Black else Color.White

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clip(CircleShape)
                            .background(previewed)
                            .border(
                                width = if (isSelected) 3.dp else 1.dp,
                                color = if (isSelected) colors.primaryAccent
                                else colors.textGray.copy(alpha = 0.4f),
                                shape = CircleShape,
                            )
                            .clickable { onColorSelected(baseColor) },
                        contentAlignment = Alignment.Center,
                    ) {
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
                repeat(4 - rowColors.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}