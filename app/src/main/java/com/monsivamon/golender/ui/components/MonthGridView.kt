package com.monsivamon.golender.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.monsivamon.golender.data.Event
import com.monsivamon.golender.data.util.occursOn
import com.monsivamon.golender.ui.theme.AppColors
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

// 対象月のカレンダーグリッド（曜日ヘッダーと日付セル）を描画する
@Composable
fun MonthGridView(
    month: YearMonth,
    events: List<Event>,
    selectedDate: LocalDate,
    today: LocalDate,
    weekStartDay: DayOfWeek,
    dayColors: Map<DayOfWeek, Color>,
    colors: AppColors,
    onSelectDate: (LocalDate) -> Unit,
) {
    // 曜日文字列から DayOfWeek への変換テーブル
    val stringToDayOfWeek = mapOf(
        "日" to DayOfWeek.SUNDAY, "月" to DayOfWeek.MONDAY, "火" to DayOfWeek.TUESDAY,
        "水" to DayOfWeek.WEDNESDAY, "木" to DayOfWeek.THURSDAY, "金" to DayOfWeek.FRIDAY, "土" to DayOfWeek.SATURDAY
    )

    Column(modifier = Modifier.fillMaxSize()) {
        // 週の開始曜日に合わせて曜日ヘッダーを並び替える
        val allDays = listOf("日", "月", "火", "水", "木", "金", "土")
        val startIndex = if (weekStartDay == DayOfWeek.MONDAY) 1 else 0
        val orderedWeekDays = allDays.drop(startIndex) + allDays.take(startIndex)

        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
            orderedWeekDays.forEach { dayString ->
                val dayEnum = stringToDayOfWeek[dayString]!!
                val c = dayColors[dayEnum] ?: Color.Unspecified
                val finalColor = if (c == Color.Unspecified) colors.text else c
                Text(
                    dayString, modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center, color = finalColor,
                    fontSize = 14.sp, fontWeight = FontWeight.Bold,
                )
            }
        }

        // 月初の曜日オフセットと週数を計算する
        val firstDayOfMonth = month.atDay(1)
        val offset = (firstDayOfMonth.dayOfWeek.value - weekStartDay.value + 7) % 7
        val daysInMonth = month.lengthOfMonth()
        val totalCells = ((daysInMonth + offset + 6) / 7) * 7
        val numRows = totalCells / 7

        Column(modifier = Modifier.fillMaxSize()) {
            // 行ごとに 7 日分のセルを並べる
            repeat(numRows) { rowIndex ->
                Row(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    repeat(7) { colIndex ->
                        val index = rowIndex * 7 + colIndex
                        val dayOffset = index - offset
                        // 前月・当月・翌月に応じて実際の日付を算出する
                        val date = when {
                            dayOffset < 0 -> month.minusMonths(1).atEndOfMonth().plusDays((dayOffset + 1).toLong())
                            dayOffset < daysInMonth -> month.atDay(dayOffset + 1)
                            else -> month.plusMonths(1).atDay(dayOffset - daysInMonth + 1)
                        }
                        val isCurrentMonth = date.month == month.month
                        val isToday = date == today
                        val dailyEvents = events.filter { it.occursOn(date) }
                        val c = dayColors[date.dayOfWeek] ?: Color.Unspecified
                        // 1 日分のセル（日付＋予定タイトル）を描画する
                        CalendarCell(
                            date = date,
                            events = dailyEvents,
                            isSelected = selectedDate == date,
                            isCurrentMonth = isCurrentMonth,
                            isToday = isToday,
                            dayColor = c,
                            colors = colors,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            onClick = { onSelectDate(date) },
                        )
                    }
                }
            }
        }
    }
}