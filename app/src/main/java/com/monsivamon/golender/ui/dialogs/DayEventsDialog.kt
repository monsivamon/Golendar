package com.monsivamon.golender.ui.dialogs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.monsivamon.golender.data.Event
import com.monsivamon.golender.data.util.getJpDayOfWeek
import com.monsivamon.golender.ui.EventCard
import com.monsivamon.golender.ui.theme.AppColors
import java.time.LocalDate

// 指定日の予定一覧をポップアップ表示する。
@Composable
fun DayEventsDialog(
    date: LocalDate,
    events: List<Event>,
    colors: AppColors,
    onDismiss: () -> Unit,
    onEventClick: (Event) -> Unit,
    onAddEvent: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        title = {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    "${date.monthValue}月${date.dayOfMonth}日 (${getJpDayOfWeek(date.dayOfWeek)})",
                    color = colors.text,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                )
                Spacer(Modifier.width(8.dp))
                Text("${events.size}件", color = colors.textGray, fontSize = 14.sp)
            }
        },
        text = {
            if (events.isEmpty()) {
                Text(
                    "予定なし",
                    color = colors.textGray,
                    fontSize = 16.sp,
                    modifier = Modifier.padding(vertical = 16.dp),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp),
                ) {
                    items(events) { event ->
                        EventCard(
                            event = event,
                            colors = colors,
                            onClick = onEventClick,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onAddEvent) {
                Text("+ 予定を追加", color = colors.primaryAccent, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("閉じる", color = colors.textGray)
            }
        },
    )
}