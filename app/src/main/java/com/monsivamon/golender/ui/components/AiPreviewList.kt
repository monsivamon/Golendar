package com.monsivamon.golender.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.monsivamon.golender.R
import com.monsivamon.golender.data.LocalEvent
import com.monsivamon.golender.data.prefs.AppTimeZone
import com.monsivamon.golender.ui.theme.AppColors
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// AI 解析結果のプレビュー一覧（チェックボックスで選択可能）
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
            // 終日は UTC、時間指定はシステム TZ で日時を解釈する
            val zone = if (event.isAllDay) ZoneOffset.UTC else AppTimeZone.effective()
            val startDt = LocalDateTime.ofInstant(Instant.ofEpochMilli(event.startTime), zone)
            val endDt = LocalDateTime.ofInstant(Instant.ofEpochMilli(event.endTime), zone)
            val dateStr = startDt.format(DateTimeFormatter.ofPattern("yyyy/MM/dd"))
            val allDayLabel = stringResource(R.string.co_all_day)
            val timeStr = if (event.isAllDay) allDayLabel else "${startDt.format(DateTimeFormatter.ofPattern("HH:mm"))}-${endDt.format(DateTimeFormatter.ofPattern("HH:mm"))}"

            // 1 件分の行（チェックボックス＋日付・タイトル・時刻）
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = index in selected,
                    onCheckedChange = { onToggle(index) },
                    colors = CheckboxDefaults.colors(
                        checkedColor = colors.primaryAccent,
                        uncheckedColor = colors.text,
                    )
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