package com.monsivamon.golender.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.monsivamon.golender.viewmodel.CalendarViewModel
import java.time.DayOfWeek
import java.time.LocalDate

// 今日ボタン押下時：選択日を今日にリセットし月表示へ遷移
fun navigateToTodayMonth(viewModel: CalendarViewModel, navController: NavController) {
    viewModel.resetToToday()
    navController.navigate(Routes.MONTHLY) {
        popUpTo(Routes.MONTHLY) { inclusive = true }
    }
}

// 曜日名を日本語で返す
fun getJpDayOfWeek(dayOfWeek: DayOfWeek): String = when (dayOfWeek) {
    DayOfWeek.SUNDAY -> "日"
    DayOfWeek.MONDAY -> "月"
    DayOfWeek.TUESDAY -> "火"
    DayOfWeek.WEDNESDAY -> "水"
    DayOfWeek.THURSDAY -> "木"
    DayOfWeek.FRIDAY -> "金"
    DayOfWeek.SATURDAY -> "土"
}

// 曜日カラーパレット（16色）
val DayColorPalette = listOf(
    Color(0xFFE53935), Color(0xFFD81B60), Color(0xFF8E24AA), Color(0xFF5E35B1),
    Color(0xFF3949AB), Color(0xFF1E88E5), Color(0xFF039BE5), Color(0xFF00ACC1),
    Color(0xFF00897B), Color(0xFF43A047), Color(0xFF7CB342), Color(0xFFC0CA33),
    Color(0xFFFBC02D), Color(0xFFFFB300), Color(0xFFFB8C00), Color(0xFFF4511E)
)

// 背景色パステルパレット（16色）
val PastelColorPalette = listOf(
    Color(0xFFFFB3BA), Color(0xFFFFDFBA), Color(0xFFFFFFBA), Color(0xFFBAFFC9),
    Color(0xFFBAE1FF), Color(0xFFE6B3FF), Color(0xFFFFC6FF), Color(0xFFC4FAF8),
    Color(0xFFA0E8AF), Color(0xFFFFD1DC), Color(0xFFFDFD96), Color(0xFFE2F0CB),
    Color(0xFFB5EAD7), Color(0xFFC7CEEA), Color(0xFFF4C2C2), Color(0xFFFDECDA)
)

// 同期確認ダイアログ
@Composable
fun SyncConfirmDialog(colors: AppColors, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("同期の確認", color = colors.text) },
        text = { Text("端末内のアカウントのカレンダー情報と同期します。\nよろしいですか？", color = colors.text) },
        confirmButton = {
            TextButton(onClick = { onConfirm(); onDismiss() }) {
                Text("同期する", color = colors.primaryAccent, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("キャンセル", color = colors.textGray)
            }
        },
        containerColor = colors.surface
    )
}

// 日付タイトル＋ドロップダウンアイコン（年月選択用）
@Composable
fun DateTitleWithPicker(title: String, colors: AppColors, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(4.dp)
    ) {
        Text(text = title, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = colors.text)
        Text(" ▼", fontSize = 16.sp, color = colors.primaryAccent)
    }
}

// 年月選択ダイアログ（月表示用）
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YearMonthPickerDialog(
    currentYear: Int,
    currentMonth: Int,
    colors: AppColors,
    onDismiss: () -> Unit,
    onDateSelected: (Int, Int) -> Unit
) {
    var tempYear by remember { mutableIntStateOf(currentYear) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        title = { Text("年月へジャンプ", color = colors.text) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                ) {
                    IconButton(onClick = { tempYear-- }) { Text("◀", color = colors.primaryAccent) }
                    Text("$tempYear 年", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = colors.text)
                    IconButton(onClick = { tempYear++ }) { Text("▶", color = colors.primaryAccent) }
                }

                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(12) { i ->
                        val m = i + 1
                        val isSelected = (tempYear == currentYear && m == currentMonth)
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) colors.primaryAccent else colors.bg)
                                .clickable { onDateSelected(tempYear, m) }
                        ) {
                            Text("${m}月", color = if (isSelected) Color.White else colors.text)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("キャンセル", color = colors.textGray) }
        }
    )
}