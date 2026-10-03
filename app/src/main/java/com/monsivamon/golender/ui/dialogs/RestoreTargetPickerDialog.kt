package com.monsivamon.golender.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
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
import com.monsivamon.golender.data.source.CalendarMeta
import com.monsivamon.golender.ui.theme.AppColors

// 復元（追記）先のカレンダーを 1 つ選ぶダイアログ
// 書き込み可能（accessLevel >= 500）かつ祝日・誕生日以外のカレンダーのみを候補にする
@Composable
fun RestoreTargetPickerDialog(
    colors: AppColors,
    calendars: List<CalendarMeta>,
    onDismiss: () -> Unit,
    onConfirm: (CalendarMeta) -> Unit,
) {
    // 書き込み可能かつ特殊カレンダー以外を抽出する
    val writable = remember(calendars) {
        calendars.filter { it.accessLevel >= 500 && !it.isHoliday && !it.isBirthday }
    }
    // 初期選択は先頭カレンダー
    var selectedId by remember(writable) {
        mutableStateOf<Long?>(writable.firstOrNull()?.id)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            color = colors.surface,
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // スクロール可能なカレンダー一覧
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp, vertical = 20.dp),
                ) {
                    Text(
                        "復元先のカレンダーを選択",
                        style = MaterialTheme.typography.titleLarge,
                        color = colors.text,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "予定を追記するカレンダーを1つ選んでください。",
                        color = colors.textGray,
                        fontSize = 14.sp,
                    )
                    Spacer(Modifier.height(16.dp))

                    // 候補が無い場合のエラー表示
                    if (writable.isEmpty()) {
                        Text(
                            "書き込み可能なカレンダーが見つかりません。\n" +
                                    "設定でGoogleカレンダーの権限を確認してください。",
                            color = colors.sunRed,
                            fontSize = 14.sp,
                        )
                    } else {
                        // アカウント単位でグループ化して表示する
                        val grouped = writable.groupBy { it.accountName }
                        grouped.forEach { (account, cals) ->
                            Text(
                                account.ifBlank { "（アカウント名なし）" },
                                fontWeight = FontWeight.Bold,
                                color = colors.textGray,
                                fontSize = 14.sp,
                                modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
                            )
                            cals.forEach { meta ->
                                val isSelected = meta.id == selectedId
                                // ラジオボタン＋色見本＋表示名の 1 行
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .selectable(
                                            selected = isSelected,
                                            onClick = { selectedId = meta.id },
                                        )
                                        .padding(vertical = 6.dp, horizontal = 4.dp),
                                ) {
                                    RadioButton(
                                        selected = isSelected,
                                        onClick = null,
                                        colors = RadioButtonDefaults.colors(
                                            selectedColor = colors.primaryAccent,
                                            unselectedColor = colors.textGray,
                                        ),
                                    )
                                    // カレンダー標準色の丸
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (meta.defaultColor == 0) Color.Gray
                                                else Color(meta.defaultColor)
                                            ),
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Text(
                                        meta.displayName.ifBlank { "名称未設定" },
                                        color = colors.text,
                                        fontSize = 16.sp,
                                    )
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = colors.divider)
                // 下部のキャンセル／決定ボタン
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("キャンセル", color = colors.textGray)
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            // 選択中のメタ情報を返す
                            writable.firstOrNull { it.id == selectedId }?.let(onConfirm)
                        },
                        enabled = selectedId != null,
                    ) {
                        Text("決定")
                    }
                }
            }
        }
    }
}