package com.monsivamon.golender.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.monsivamon.golender.ui.theme.AppColors
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

// 初期日付付きの日付選択ダイアログ
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GolendarDatePickerDialog(
    initialDate: LocalDate,
    colors: AppColors,
    onDismiss: () -> Unit,
    onDateSelected: (LocalDate) -> Unit,
) = GolendarDatePickerDialogImpl(
    colors, onDismiss,
    initialMillis = initialDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
    onDateSelected = onDateSelected,
)

// 初期日付なしの日付選択ダイアログ（今日が初期値）
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GolendarDatePickerDialog(
    colors: AppColors,
    onDismiss: () -> Unit,
    onDateSelected: (LocalDate) -> Unit,
) = GolendarDatePickerDialogImpl(colors, onDismiss, initialMillis = null, onDateSelected = onDateSelected)

// 日付選択ダイアログの共通実装
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GolendarDatePickerDialogImpl(
    colors: AppColors, onDismiss: () -> Unit,
    initialMillis: Long?, onDateSelected: (LocalDate) -> Unit,
) {
    val state = if (initialMillis != null) rememberDatePickerState(initialSelectedDateMillis = initialMillis)
    else rememberDatePickerState()
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let {
                    onDateSelected(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate())
                }
                onDismiss()
            }) { Text("OK", color = colors.primaryAccent) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("キャンセル", color = colors.textGray) } },
        colors = DatePickerDefaults.colors(containerColor = colors.surface),
    ) {
        DatePicker(state = state, colors = DatePickerDefaults.colors(
            containerColor = Color.Transparent, titleContentColor = colors.text,
            headlineContentColor = colors.text, weekdayContentColor = colors.textGray,
            subheadContentColor = colors.text, navigationContentColor = colors.text,
            yearContentColor = colors.text, dayContentColor = colors.text,
            selectedDayContainerColor = colors.primaryAccent, selectedDayContentColor = Color.White,
            currentYearContentColor = colors.primaryAccent, selectedYearContainerColor = colors.primaryAccent,
            selectedYearContentColor = Color.White, todayContentColor = colors.primaryAccent,
            todayDateBorderColor = colors.primaryAccent,
        ))
    }
}

// 時刻選択ダイアログ（24時間表示）
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GolendarTimePickerDialog(
    initialTime: LocalTime,
    colors: AppColors,
    onDismiss: () -> Unit,
    onTimeSelected: (LocalTime) -> Unit,
) {
    val state = rememberTimePickerState(initialTime.hour, initialTime.minute, true)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        text = {
            TimePicker(state = state, colors = TimePickerDefaults.colors(
                clockDialColor = colors.bg, clockDialSelectedContentColor = Color.White,
                clockDialUnselectedContentColor = colors.text, selectorColor = colors.primaryAccent,
                containerColor = colors.surface,
                timeSelectorSelectedContainerColor = colors.primaryAccent.copy(alpha = 0.2f),
                timeSelectorUnselectedContainerColor = colors.bg,
                timeSelectorSelectedContentColor = colors.primaryAccent,
                timeSelectorUnselectedContentColor = colors.text,
            ))
        },
        confirmButton = {
            TextButton(onClick = {
                onTimeSelected(LocalTime.of(state.hour, state.minute)); onDismiss()
            }) { Text("OK", color = colors.primaryAccent) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("キャンセル", color = colors.textGray) } },
    )
}

// 年月選択ダイアログ（年送りボタンと12ヶ月グリッド）
@Composable
fun YearMonthPickerDialog(
    currentYear: Int, currentMonth: Int,
    colors: AppColors, onDismiss: () -> Unit,
    onDateSelected: (Int, Int) -> Unit,
) {
    var year by remember { mutableIntStateOf(currentYear) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        title = { Text("年月へジャンプ", color = colors.text) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                ) {
                    IconButton(onClick = { year-- }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "前年", tint = colors.primaryAccent) }
                    Text("$year 年", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = colors.text)
                    IconButton(onClick = { year++ }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "翌年", tint = colors.primaryAccent) }
                }
                LazyVerticalGrid(GridCells.Fixed(4),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(12) { i ->
                        val m = i + 1
                        val selected = (year == currentYear && m == currentMonth)
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.aspectRatio(1f).clip(RoundedCornerShape(8.dp))
                                .background(if (selected) colors.primaryAccent else colors.bg)
                                .clickable { onDateSelected(year, m) },
                        ) { Text("${m}月", color = if (selected) Color.White else colors.text) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("キャンセル", color = colors.textGray) } },
    )
}