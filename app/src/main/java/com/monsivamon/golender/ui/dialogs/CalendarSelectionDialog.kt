package com.monsivamon.golender.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.monsivamon.golender.data.SelectedCalendar
import com.monsivamon.golender.data.source.CalendarMeta
import com.monsivamon.golender.ui.theme.AppColors

// 表示対象の Google カレンダーを選択するダイアログを表示する
@Composable
fun CalendarSelectionDialog(
    colors: AppColors,
    calendars: List<CalendarMeta>,
    onComplete: (List<SelectedCalendar>) -> Unit,
) {
    // 初期状態は全カレンダー選択済み
    var selectedIds by remember(calendars) { mutableStateOf(calendars.map { it.id }.toSet()) }
    var showWarning by remember { mutableStateOf(false) }

    // 1 つも選択されなかった場合の警告ダイアログ
    if (showWarning) {
        AlertDialog(
            onDismissRequest = { showWarning = false },
            containerColor = colors.surface,
            title = { Text("警告", color = colors.text, fontWeight = FontWeight.Bold) },
            text = { Text("カレンダーが1つも選択されていません。このままでは予定が表示されません。続行しますか？", color = colors.text) },
            confirmButton = {
                TextButton(onClick = {
                    showWarning = false
                    onComplete(emptyList())
                }) { Text("続行", color = colors.sunRed, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showWarning = false }) { Text("戻る", color = colors.primaryAccent) }
            }
        )
    }

    Dialog(
        onDismissRequest = { },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            color = colors.surface,
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // スクロール可能なカレンダー一覧
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp, vertical = 20.dp)
                ) {
                    Text("表示するカレンダーを選択", style = androidx.compose.material3.MaterialTheme.typography.titleLarge, color = colors.text)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("表示したいカレンダーにチェックを入れてください。後から設定画面で変更できます。", color = colors.textGray, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(16.dp))

                    // 全て選択／全て解除ボタン
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { selectedIds = calendars.map { it.id }.toSet() }) { Text("全て選択", color = colors.primaryAccent) }
                        TextButton(onClick = { selectedIds = emptySet() }) { Text("全て解除", color = colors.primaryAccent) }
                    }
                    Spacer(modifier = Modifier.height(16.dp))

                    // アカウント単位でカレンダーをグループ化して一覧表示する
                    val grouped = calendars.groupBy { it.accountName }
                    grouped.forEach { (account, cals) ->
                        Text(account, fontWeight = FontWeight.Bold, color = colors.textGray, fontSize = 14.sp, modifier = Modifier.padding(top = 12.dp, bottom = 8.dp))
                        cals.forEach { meta ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
                            ) {
                                // 表示 ON/OFF 用チェックボックス
                                Checkbox(
                                    checked = meta.id in selectedIds,
                                    onCheckedChange = { checked ->
                                        selectedIds = if (checked) selectedIds + meta.id else selectedIds - meta.id
                                    },
                                    colors = CheckboxDefaults.colors(checkedColor = colors.primaryAccent)
                                )
                                // カレンダー標準色の丸
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(if (meta.defaultColor == 0) Color.Gray else Color(meta.defaultColor))
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    meta.displayName.ifBlank { "名称未設定" },
                                    color = colors.text,
                                    fontSize = 16.sp
                                )
                            }
                        }
                    }
                }
                HorizontalDivider(color = colors.divider)
                // 決定ボタン
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = {
                        if (selectedIds.isEmpty()) {
                            showWarning = true
                        } else {
                            // 選択されたカレンダーを SelectedCalendar に変換する
                            val result = calendars.filter { it.id in selectedIds }.map { meta ->
                                SelectedCalendar(
                                    calendarId = meta.id,
                                    accountName = meta.accountName,
                                    displayName = meta.displayName,
                                    colorArgb = meta.defaultColor,
                                    isVisible = true
                                )
                            }
                            onComplete(result)
                        }
                    }) {
                        Text("決定", color = colors.primaryAccent, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }
            }
        }
    }
}