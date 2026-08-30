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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.monsivamon.golender.data.Event
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// 予定カード（日・週表示用）
@Composable
fun EventCard(event: Event, colors: AppColors, onClick: (Event) -> Unit) {
    val formatter = DateTimeFormatter.ofPattern("HH:mm")
    val startTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(event.startTime), ZoneId.systemDefault())
    val endTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(event.endTime), ZoneId.systemDefault())
    val timeString = if (event.isAllDay) "終日" else "${startTime.format(formatter)} - ${endTime.format(formatter)}"

    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).clickable { onClick(event) },
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            // 左端のアクセントライン（種別で色分け）
            val accentColor = when {
                event.isBirthdayCalendar -> Color(0xFFFF9800)
                event.isCulturalEvent -> Color(0xFF4CAF50)
                else -> colors.primaryAccent
            }

            Box(modifier = Modifier.width(4.dp).height(40.dp).clip(RoundedCornerShape(2.dp)).background(accentColor))
            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(text = event.title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = timeString, fontSize = 14.sp, color = colors.textGray)
            }

            // 説明文がある場合のみ表示
            if (event.description.isNotBlank()) {
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = event.description,
                    fontSize = 12.sp,
                    color = colors.textGray,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 120.dp),
                    textAlign = TextAlign.End
                )
            }
        }
    }
}

// 検索結果用カード（日付情報を追加表示）
@Composable
fun SearchResultCard(event: Event, colors: AppColors, onClick: (Event) -> Unit) {
    val startTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(event.startTime), ZoneId.systemDefault())
    val endTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(event.endTime), ZoneId.systemDefault())
    val jpDay = getJpDayOfWeek(startTime.dayOfWeek)
    val dateString = "${startTime.monthValue}月${startTime.dayOfMonth}日($jpDay)"
    val timeString = if (event.isAllDay) "終日" else "${startTime.format(DateTimeFormatter.ofPattern("HH:mm"))} - ${endTime.format(DateTimeFormatter.ofPattern("HH:mm"))}"

    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).clickable { onClick(event) },
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            val accentColor = when {
                event.isBirthdayCalendar -> Color(0xFFFF9800)
                event.isCulturalEvent -> Color(0xFF4CAF50)
                else -> colors.primaryAccent
            }

            Box(modifier = Modifier.width(4.dp).height(50.dp).clip(RoundedCornerShape(2.dp)).background(accentColor))
            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(text = event.title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = "$dateString  $timeString", fontSize = 14.sp, color = colors.textGray)
            }

            if (event.description.isNotBlank()) {
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = event.description,
                    fontSize = 12.sp,
                    color = colors.textGray,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 120.dp),
                    textAlign = TextAlign.End
                )
            }
        }
    }
}