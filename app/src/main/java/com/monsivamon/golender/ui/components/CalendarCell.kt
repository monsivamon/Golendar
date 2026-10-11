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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.monsivamon.golender.data.Event
import com.monsivamon.golender.data.util.accentColor
import com.monsivamon.golender.ui.theme.AppColors
import java.time.LocalDate


// 月間カレンダーの 1 日分セル。
// 予定バーは maxEvents 本まで表示し、超過分は「+N」を高さ 10dp の
// 細い行に詰めて表示することで、セル枠からのはみ出しを防ぐ。
@Composable
fun CalendarCell(
    date: LocalDate, events: List<Event>,
    isSelected: Boolean, isCurrentMonth: Boolean, isToday: Boolean,
    dayColor: Color, colors: AppColors,
    modifier: Modifier = Modifier.aspectRatio(0.6f),
    maxEvents: Int = 3,
    onClick: () -> Unit,
) {
    // 日付文字色を選択状態・当月内外・曜日色から決定する
    val dateColor = when {
        isSelected -> Color.White
        !isCurrentMonth -> colors.textGray
        dayColor != Color.Unspecified -> dayColor
        else -> colors.text
    }
    // 今日セルは強調ボーダーを付ける
    val borderColor = if (isToday) colors.primaryAccent else colors.divider
    val borderWidth = if (isToday) 1.5.dp else 0.5.dp

    // maxEvents を超える予定の件数（0 ならオーバーフローなし）。
    // ▼ ヒント判定からも参照できるよう、Composable 冒頭で宣言する。
    val overflow = (events.size - maxEvents).coerceAtLeast(0)

    Box(
        modifier
            .padding(2.dp)
            .border(borderWidth, borderColor, RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
    ) {
        // 選択中はアクセント色の半透明背景を重ねる
        if (isSelected) {
            Box(
                Modifier.fillMaxSize()
                    .clip(RoundedCornerShape(4.dp))
                    .background(colors.primaryAccent.copy(alpha = 0.5f))
            )
        }
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            // 日付の数字
            Text(
                date.dayOfMonth.toString(), fontSize = 14.sp, color = dateColor,
                fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
            )
            // 予定バーを maxEvents 本まで表示する
            events.take(maxEvents).forEach { event ->
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
            // maxEvents を超える場合のみ「+N」を細い行に詰めて表示する。
            // 高さを 10dp に固定し、上下パディングを最小化することで
            // セル枠の超過を防ぐ。
            if (overflow > 0) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .height(10.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(
                            if (isSelected) Color.White.copy(alpha = 0.55f)
                            else colors.primaryAccent.copy(alpha = 0.22f)
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "+$overflow",
                        fontSize = 8.sp,
                        color = colors.primaryAccent,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                    )
                }
            }
        }

    }
}