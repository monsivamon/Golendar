package com.monsivamon.golender.ui.common

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.monsivamon.golender.R
import com.monsivamon.golender.ui.theme.AppColors

// 恒久拒否（Android 11+ の USER_FIXED）を判定するヘルパー。
// requestPermissions の直後（ランチャーのコールバック）からのみ呼ぶこと。
// 未要求状態と恒久拒否は checkSelfPermission だけでは区別できないため、
// 「拒否されている かつ rationale=false」という条件で判定する。
fun isPermissionPermanentlyDenied(context: Context, vararg permissions: String): Boolean {
    val activity = context.findActivity() ?: return false
    return permissions.all { perm ->
        ContextCompat.checkSelfPermission(context, perm) != PackageManager.PERMISSION_GRANTED &&
            !ActivityCompat.shouldShowRequestPermissionRationale(activity, perm)
    }
}

// アプリの設定画面（権限一覧）を開く
fun openAppSettings(context: Context) {
    try {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (_: Exception) { }
}

// 恒久拒否時にユーザーを設定画面へ誘導するダイアログ
@Composable
fun PermissionSettingsDialog(
    colors: AppColors,
    title: String,
    message: String,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        title = { Text(title, color = colors.text, fontWeight = FontWeight.Bold) },
        text = { Text(message, color = colors.text, fontSize = 14.sp, lineHeight = 20.sp) },
        confirmButton = {
            TextButton(onClick = {
                onOpenSettings()
                onDismiss()
            }) {
                Text(stringResource(R.string.co_open_settings), color = colors.primaryAccent, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.co_later), color = colors.textGray)
            }
        },
    )
}