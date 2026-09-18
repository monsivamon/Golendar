package com.monsivamon.golender.ui.dialogs

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.monsivamon.golender.ui.theme.AppColors

// Googleカレンダーへのアクセス許可を案内するダイアログを表示する。
@Composable
fun CalendarPermissionDialog(
    colors: AppColors,
    title: String,
    message: String,
    confirmLabel: String = "許可する",
    dismissLabel: String = "あとで",
    onResult: (granted: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val readGranted = permissions[Manifest.permission.READ_CALENDAR] == true
        val writeGranted = permissions[Manifest.permission.WRITE_CALENDAR] == true
        onResult(readGranted && writeGranted)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        title = {
            Text(title, color = colors.text, fontWeight = FontWeight.Bold)
        },
        text = {
            Text(message, color = colors.text, fontSize = 14.sp)
        },
        confirmButton = {
            TextButton(onClick = {
                launcher.launch(
                    arrayOf(
                        Manifest.permission.READ_CALENDAR,
                        Manifest.permission.WRITE_CALENDAR,
                    )
                )
            }) {
                Text(confirmLabel, color = colors.primaryAccent, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(dismissLabel, color = colors.textGray)
            }
        },
    )
}