package com.monsivamon.golender.ui.dialogs

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import com.monsivamon.golender.R
import com.monsivamon.golender.ui.theme.AppColors

// アプリ終了の確認ダイアログを表示する
@Composable
fun ExitConfirmDialog(
    colors: AppColors,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        title = { Text(stringResource(R.string.dl2_exitTitle), color = colors.text, fontWeight = FontWeight.Bold) },
        text = { Text(stringResource(R.string.dl2_exitMessage), color = colors.text) },
        confirmButton = {
            // 終了を実行する（赤色で強調）
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.dl2_exit), color = colors.sunRed, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dl2_cancel), color = colors.textGray)
            }
        },
    )
}