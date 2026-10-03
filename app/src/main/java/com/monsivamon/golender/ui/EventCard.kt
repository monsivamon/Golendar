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
import androidx.compose.ui.graphics.Color
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

// 予定カード（日・週・月下部リスト用）
@Composable
fun EventCard(
    event: Event,
    colors: AppColors,
    showCalendarName: Boolean = false,
    onClick: (Event) -> Unit,
) = EventCardBase(
    event = event,
    colors = colors,
    timeLine = event.timeRangeString(),
    lineHeight = 40.dp,
    showCalendarName = showCalendarName,
    onClick = onClick,
)

// 検索結果用カード（年月日＋曜日＋時刻＋カレンダー名を表示）
@Composable
fun SearchResultCard(
    event: Event,
    colors: AppColors,
    showCalendarName: Boolean = false,
    onClick: (Event) -> Unit,
) {
    val dt = event.localStartDateTime()
    val dateLabel = "${dt.year}/${dt.monthValue}/${dt.dayOfMonth}(${getJpDayOfWeek(dt.dayOfWeek)})"
    EventCardBase(
        event = event,
        colors = colors,
        timeLine = "$dateLabel  ${event.timeRangeString()}",
        lineHeight = 50.dp,
        showCalendarName = showCalendarName,
        onClick = onClick,
    )
}

// 予定カードの共通レイアウト（アクセントライン＋タイトル＋時刻＋カレンダー名＋メモ）
@Composable
private fun EventCardBase(
    event: Event,
    colors: AppColors,
    timeLine: String,
    lineHeight: Dp,
    showCalendarName: Boolean,
    onClick: (Event) -> Unit,
) {
    // 種別に応じた絵文字プレフィックス（誕生日／文化／祝日）
    val prefix = when {
        event.isBirthdayCalendar -> "🎂 "
        event.isCulturalEvent -> "🎌 "
        event.isHolidayCalendar -> "🗾 "
        else -> ""
    }
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).clickable { onClick(event) },
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            // 種別ごとのアクセントライン
            Box(
                Modifier.width(4.dp).height(lineHeight)
                    .clip(RoundedCornerShape(2.dp))
                    .background(event.accentColor(colors))
            )
            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                // タイトル（絵文字プレフィックス付き）
                Text(
                    prefix + event.title, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                    color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                // 日時 or 時刻範囲
                Text(timeLine, fontSize = 14.sp, color = colors.textGray)

                // カレンダー名（複数カレンダー表示時のみ／8 文字で省略）
                if (showCalendarName && event.calendarDisplayName.isNotBlank()) {
                    val displayName = if (event.calendarDisplayName.length > 8)
                        event.calendarDisplayName.take(8) + "…"
                    else event.calendarDisplayName
                    val nameColor = if (event.calendarColorArgb != null && event.calendarColorArgb != 0)
                        Color(event.calendarColorArgb!!) else colors.textGray
                    Text(
                        text = displayName, color = nameColor, fontSize = 11.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }

            // メモ（右端に最大 2 行）
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