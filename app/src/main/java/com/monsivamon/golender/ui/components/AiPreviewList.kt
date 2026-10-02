package com.monsivamon.golender.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.monsivamon.golender.data.LocalEvent
import com.monsivamon.golender.ui.theme.AppColors
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// AI解析結果のプレビュー一覧（チェックボックスで選択可能）
@Composable
fun AiPreviewList(
    events: List<LocalEvent>,
    selected: Set<Int>,
    colors: AppColors,
    onToggle: (Int) -> Unit,
) {
    // イベント一覧をスクロール可能なリストで表示する
    LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp)) {
        itemsIndexed(events) { index, event ->
            // 終日は UTC、時間指定はシステムTZで日時を解釈する
            val zone = if (event.isAllDay) ZoneOffset.UTC else ZoneId.systemDefault()
            val startDt = LocalDateTime.ofInstant(Instant.ofEpochMilli(event.startTime), zone)
            val endDt = LocalDateTime.ofInstant(Instant.ofEpochMilli(event.endTime), zone)
            val dateStr = startDt.format(DateTimeFormatter.ofPattern("yyyy/MM/dd"))
            val timeStr = if (event.isAllDay) "終日" else "${startDt.format(DateTimeFormatter.ofPattern("HH:mm"))}-${endDt.format(DateTimeFormatter.ofPattern("HH:mm"))}"
            // 1件分の行（チェックボックス＋日付・タイトル・時刻）
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = index in selected,
                    onCheckedChange = { onToggle(index) }
                )
                Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                    Text(dateStr, fontSize = 12.sp, color = colors.textGray)
                    Text(event.title, fontSize = 14.sp, color = colors.text)
                    Text(timeStr, fontSize = 12.sp, color = colors.textGray)
                }
            }
        }
    }
}