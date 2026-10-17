package com.monsivamon.golender.ui.dialogs

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import com.monsivamon.golender.R
import com.monsivamon.golender.ui.theme.AppColors

// 同期確認ダイアログ（カレンダーデータを再読み込みする前に確認）
@Composable
fun SyncConfirmDialog(colors: AppColors, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dl2_syncTitle), color = colors.text) },
        text = { Text(stringResource(R.string.dl2_syncMessage), color = colors.text) },
        confirmButton = {
            // 同期実行して閉じる
            TextButton(onClick = { onConfirm(); onDismiss() }) {
                Text(stringResource(R.string.dl2_syncConfirm), color = colors.primaryAccent, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.dl2_cancel), color = colors.textGray) } },
        containerColor = colors.surface,
    )
}