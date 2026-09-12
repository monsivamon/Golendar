package com.monsivamon.golender.ui.dialogs

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import com.monsivamon.golender.ui.theme.AppColors

// 同期確認ダイアログ（カレンダーデータを再読み込みする前に確認）
@Composable
fun SyncConfirmDialog(colors: AppColors, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("同期の確認", color = colors.text) },
        text = { Text("端末内のアカウントのカレンダー情報と同期します。\nよろしいですか？", color = colors.text) },
        confirmButton = {
            TextButton(onClick = { onConfirm(); onDismiss() }) {
                Text("同期する", color = colors.primaryAccent, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("キャンセル", color = colors.textGray) } },
        containerColor = colors.surface,
    )
}