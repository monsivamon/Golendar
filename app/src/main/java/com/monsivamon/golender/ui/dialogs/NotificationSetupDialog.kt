package com.monsivamon.golender.ui.dialogs

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.monsivamon.golender.ui.theme.AppColors

// 初回起動時の通知セットアップ進行状態
private enum class NotificationSetupStep { INTRO, EXACT_ALARM, BATTERY }

// 初回起動時に通知関連の権限を順番に案内するダイアログを表示する
@Composable
fun NotificationSetupDialog(
    colors: AppColors,
    onComplete: () -> Unit,
) {
    val context = LocalContext.current

    // 通知権限が許可されているかを返す（Android 13 未満は常に true）
    fun isNotificationGranted(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

    // 正確なアラーム権限が許可されているかを返す（Android 12 未満は常に true）
    fun isExactAlarmGranted(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager)
                .canScheduleExactAlarms()
        } else {
            true
        }

    // バッテリー最適化が無効化されているかを返す
    fun isBatteryOptimizationIgnored(): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    // 未許可の項目に応じて開始ステップを決定する
    val initialStep: NotificationSetupStep? = remember {
        when {
            !isNotificationGranted() -> NotificationSetupStep.INTRO
            !isExactAlarmGranted() -> NotificationSetupStep.EXACT_ALARM
            !isBatteryOptimizationIgnored() -> NotificationSetupStep.BATTERY
            else -> null
        }
    }

    // 既に全て許可済みなら即座に完了する
    LaunchedEffect(initialStep) {
        if (initialStep == null) onComplete()
    }

    if (initialStep == null) return

    // 現在のステップを保持する
    var step by remember { mutableStateOf(initialStep) }

    // 通知権限要求のランチャー（結果後に正確なアラームステップへ進む）
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        step = NotificationSetupStep.EXACT_ALARM
    }

    // ステップごとに表示するタイトル・本文・ボタン文言を用意する
    val title: String
    val message: String
    val confirmLabel: String
    val onConfirm: () -> Unit

    when (step) {
        NotificationSetupStep.INTRO -> {
            title = "Golendarへようこそ"
            message = "Golendar は通知へのアクセスを必要とします。\n\n" +
                    "予定の開始時刻や10分前に通知を受け取るために、通知の許可をお願いします。\n" +
                    "すべての機能を使用したい場合は「許可する」を選んでください。"
            confirmLabel = "許可する"
            onConfirm = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    step = NotificationSetupStep.EXACT_ALARM
                }
            }
        }

        NotificationSetupStep.EXACT_ALARM -> {
            title = "正確なアラームの許可"
            message = "予定時刻ぴったりに通知を届けるために、「正確なアラーム」の許可が必要です。\n\n" +
                    "次の画面で「アラームとリマインダー」を許可してください。"
            confirmLabel = "設定を開く"
            onConfirm = {
                // 正確なアラームの設定画面を開く
                try {
                    context.startActivity(
                        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                            data = Uri.parse("package:${context.packageName}")
                        }
                    )
                } catch (_: Exception) { }
                step = NotificationSetupStep.BATTERY
            }
        }

        NotificationSetupStep.BATTERY -> {
            title = "バッテリー最適化の無効化"
            message = "スリープ中の通知遅延を防ぐために、バッテリー最適化を無効化することをおすすめします。\n\n" +
                    "次の画面で「許可」を選ぶと、Golendar が最適化の対象外になります。"
            confirmLabel = "設定を開く"
            onConfirm = {
                // バッテリー最適化除外の要求画面を開く
                try {
                    context.startActivity(
                        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                            data = Uri.parse("package:${context.packageName}")
                        }
                    )
                } catch (_: Exception) { }
                onComplete()
            }
        }
    }

    AlertDialog(
        onDismissRequest = { onComplete() },
        containerColor = colors.surface,
        title = {
            Text(title, color = colors.text, fontWeight = FontWeight.Bold)
        },
        text = {
            Text(message, color = colors.text, fontSize = 14.sp)
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmLabel, color = colors.primaryAccent, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            // 「あとで」で全工程をスキップする
            TextButton(onClick = onComplete) {
                Text("あとで", color = colors.textGray)
            }
        },
    )
}