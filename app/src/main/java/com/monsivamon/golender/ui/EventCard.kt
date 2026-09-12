package com.monsivamon.golender.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.monsivamon.golender.data.Event
import com.monsivamon.golender.data.util.accentColor
import com.monsivamon.golender.data.util.getJpDayOfWeek
import com.monsivamon.golender.data.util.localStartDateTime
import com.monsivamon.golender.data.util.timeRangeString
import com.monsivamon.golender.ui.theme.AppColors

// 予定カード（日・週表示用）
@Composable
fun EventCard(event: Event, colors: AppColors, onClick: (Event) -> Unit) =
    EventCardBase(
        event = event,
        colors = colors,
        timeLine = event.timeRangeString(),
        lineHeight = 40.dp,
        onClick = onClick,
    )

// 検索結果用カード（日付情報を追加表示）
@Composable
fun SearchResultCard(event: Event, colors: AppColors, onClick: (Event) -> Unit) {
    val dt = event.localStartDateTime()
    val dateLabel = "${dt.monthValue}月${dt.dayOfMonth}日(${getJpDayOfWeek(dt.dayOfWeek)})"
    EventCardBase(
        event = event,
        colors = colors,
        timeLine = "$dateLabel  ${event.timeRangeString()}",
        lineHeight = 50.dp,
        onClick = onClick,
    )
}

// 予定カードの共通レイアウト（アクセントライン＋タイトル＋時刻＋メモ）
@Composable
private fun EventCardBase(
    event: Event,
    colors: AppColors,
    timeLine: String,
    lineHeight: Dp,
    onClick: (Event) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).clickable { onClick(event) },
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            // 種別ごとのアクセントライン（誕生日/文化イベント/通常予定）
            Box(
                Modifier.width(4.dp).height(lineHeight)
                    .clip(RoundedCornerShape(2.dp))
                    .background(event.accentColor(colors))
            )
            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    event.title, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                    color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(timeLine, fontSize = 14.sp, color = colors.textGray)
            }

            // メモがあれば右端に最大2行表示
            if (event.description.isNotBlank()) {
                Spacer(Modifier.width(8.dp))
                Text(
                    event.description, fontSize = 12.sp, color = colors.textGray,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 120.dp),
                    textAlign = TextAlign.End,
                )
            }
        }
    }
}