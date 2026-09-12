package com.monsivamon.golender.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.monsivamon.golender.data.Event
import com.monsivamon.golender.data.util.accentColor
import com.monsivamon.golender.ui.theme.AppColors
import java.time.LocalDate

// 月間カレンダーの1日分セル（日付＋予定タイトルを最大4件表示）
@Composable
fun CalendarCell(
    date: LocalDate, events: List<Event>,
    isSelected: Boolean, isCurrentMonth: Boolean, isToday: Boolean,
    dayColor: Color, colors: AppColors, onClick: () -> Unit,
) {
    // 日付の文字色を選択状態・当月外・曜日色で決定
    val dateColor = when {
        isSelected -> Color.White
        !isCurrentMonth -> colors.textGray
        dayColor != Color.Unspecified -> dayColor
        else -> colors.text
    }
    // 今日はアクセントカラー、それ以外は区切り線色の枠線
    val borderColor = if (isToday) colors.primaryAccent else colors.divider
    val borderWidth = if (isToday) 1.5.dp else 0.5.dp

    Box(
        Modifier.aspectRatio(0.6f).padding(2.dp)
            .border(borderWidth, borderColor, RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
    ) {
        // 選択中の日付は背景色でハイライト
        if (isSelected) {
            Box(
                Modifier.fillMaxSize()
                    .clip(RoundedCornerShape(4.dp))
                    .background(colors.primaryAccent.copy(alpha = 0.5f))
            )
        }
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            // 日付（今日または選択中は太字）
            Text(
                date.dayOfMonth.toString(), fontSize = 14.sp, color = dateColor,
                fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
            )
            // 予定タイトルを最大4件、種別ごとのアクセント色で表示
            events.take(4).forEach { event ->
                Box(
                    Modifier.fillMaxWidth(0.9f).padding(vertical = 1.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(event.accentColor(colors))
                        .padding(horizontal = 2.dp, vertical = 1.dp)
                ) {
                    Text(
                        event.title, fontSize = 8.sp, color = Color.White,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}