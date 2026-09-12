package com.monsivamon.golender.ui.dialogs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.monsivamon.golender.data.Event
import com.monsivamon.golender.data.util.*
import com.monsivamon.golender.ui.theme.AppColors
import java.time.LocalDate

// 予定詳細ダイアログ（編集・複数日予定の一部削除に対応）
@Composable
fun EventDetailDialog(
    event: Event, currentDate: LocalDate, colors: AppColors,
    onDismiss: () -> Unit, onEdit: () -> Unit, onSplitDelete: () -> Unit,
) {
    val s = event.localStartDate()
    val e = event.localEndDate()
    val multiDay = s != e
    // 繰り返し種別を日本語表記に変換
    val recurringText = when (event.rrule) {
        "FREQ=DAILY" -> "（毎日）"; "FREQ=WEEKLY" -> "（毎週）"
        "FREQ=MONTHLY" -> "（毎月）"; "FREQ=YEARLY" -> "（毎年）"
        else -> ""
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        title = { Text(event.title + recurringText, color = colors.text, fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // 日時
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🕒", fontSize = 16.sp, modifier = Modifier.padding(end = 8.dp))
                    Column {
                        Text(event.dateRangeString(), color = colors.text, fontSize = 14.sp)
                        Text(event.timeRangeString(), color = colors.textGray, fontSize = 14.sp)
                    }
                }
                // 場所
                if (event.location.isNotBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("📍", fontSize = 16.sp, modifier = Modifier.padding(end = 8.dp))
                        Text(event.location, color = colors.text, fontSize = 14.sp)
                    }
                }
                // メモ
                if (event.description.isNotBlank()) {
                    HorizontalDivider(color = colors.divider)
                    Column {
                        Text("メモ", color = colors.textGray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text(event.description, color = colors.text, fontSize = 14.sp)
                    }
                }
            }
        },
        confirmButton = {
            // 読み取り専用でなければ編集ボタンを表示
            if (!event.isReadOnly) TextButton(onClick = onEdit) {
                Text("編集", color = colors.primaryAccent, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 複数日予定かつ繰り返しでない場合のみ「この日だけ削除」を表示
                if (!event.isReadOnly && multiDay && currentDate in s..e && event.rrule == null) {
                    TextButton(onClick = onSplitDelete) { Text("この日だけ削除", color = colors.sunRed) }
                    Spacer(Modifier.weight(1f))
                }
                TextButton(onClick = onDismiss) { Text("閉じる", color = colors.textGray) }
            }
        },
    )
}