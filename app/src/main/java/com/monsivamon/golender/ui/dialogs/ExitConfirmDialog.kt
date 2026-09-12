package com.monsivamon.golender.ui.dialogs

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import com.monsivamon.golender.ui.theme.AppColors

// アプリ終了確認ダイアログ
@Composable
fun ExitConfirmDialog(
    colors: AppColors,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        title = { Text("終了しますか？", color = colors.text, fontWeight = FontWeight.Bold) },
        text = { Text("Golendarを終了します。", color = colors.text) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("終了", color = colors.sunRed, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("キャンセル", color = colors.textGray)
            }
        },
    )
}