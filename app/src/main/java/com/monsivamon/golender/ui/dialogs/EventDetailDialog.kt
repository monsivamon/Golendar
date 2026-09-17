package com.monsivamon.golender.ui.dialogs

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.monsivamon.golender.data.Event
import com.monsivamon.golender.data.util.*
import com.monsivamon.golender.ui.theme.AppColors
import java.time.LocalDate

// 予定詳細ダイアログを表示する（編集・複数日予定の一部削除・テキスト共有に対応）。
@Composable
fun EventDetailDialog(
    event: Event, currentDate: LocalDate, colors: AppColors,
    onDismiss: () -> Unit, onEdit: () -> Unit, onSplitDelete: () -> Unit,
) {
    val context = LocalContext.current
    val s = event.localStartDate()
    val e = event.localEndDate()
    val multiDay = s != e
    val recurringText = when (event.rrule) {
        "FREQ=DAILY" -> "（毎日）"
        "FREQ=WEEKLY" -> "（毎週）"
        "FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR" -> "（平日）"
        "FREQ=MONTHLY" -> "（毎月）"
        "FREQ=YEARLY" -> "（毎年）"
        else -> ""
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        title = { Text(event.title + recurringText, color = colors.text, fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🕒", fontSize = 16.sp, modifier = Modifier.padding(end = 8.dp))
                    Column {
                        Text(event.dateRangeString(), color = colors.text, fontSize = 14.sp)
                        Text(event.timeRangeString(), color = colors.textGray, fontSize = 14.sp)
                    }
                }
                if (event.location.isNotBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("📍", fontSize = 16.sp, modifier = Modifier.padding(end = 8.dp))
                        Text(event.location, color = colors.text, fontSize = 14.sp)
                    }
                }
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = {
                    val shareText = buildString {
                        appendLine(event.title + recurringText)
                        appendLine(event.dateRangeString())
                        appendLine(event.timeRangeString())
                        if (event.location.isNotBlank()) appendLine("📍 ${event.location}")
                        if (event.description.isNotBlank()) {
                            appendLine()
                            appendLine(event.description)
                        }
                    }.trimEnd()
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, event.title)
                        putExtra(Intent.EXTRA_TEXT, shareText)
                    }
                    context.startActivity(Intent.createChooser(intent, "予定を共有"))
                }) { Text("共有", color = colors.primaryAccent) }
                if (!event.isReadOnly) TextButton(onClick = onEdit) {
                    Text("編集", color = colors.primaryAccent, fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!event.isReadOnly && multiDay && currentDate in s..e && event.rrule == null) {
                    TextButton(onClick = onSplitDelete) { Text("この日だけ削除", color = colors.sunRed) }
                    Spacer(Modifier.weight(1f))
                }
                TextButton(onClick = onDismiss) { Text("閉じる", color = colors.textGray) }
            }
        },
    )
}