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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.monsivamon.golender.ui.common.SetupTutorialScaffold
import com.monsivamon.golender.ui.common.TutorialIllustrationFrame
import com.monsivamon.golender.ui.common.TutorialPage
import com.monsivamon.golender.ui.theme.AppColors

// 初回起動時の通知セットアップで扱うステップ
private enum class NotifStep { INTRO, EXACT_ALARM, BATTERY }

// 初回起動時に通知関連の権限を順番に案内するダイアログ。
// ジェスチャー画面と同じ土台で表示する。
@Composable
fun NotificationSetupDialog(
    colors: AppColors,
    onComplete: () -> Unit,
) {
    val context = LocalContext.current

    fun isNotificationGranted(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else true

    fun isExactAlarmGranted(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager)
                .canScheduleExactAlarms()
        } else true

    fun isBatteryOptimizationIgnored(): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    // 未許可の項目だけを順番にステップ化する
    val steps: List<NotifStep> = remember {
        buildList {
            if (!isNotificationGranted()) add(NotifStep.INTRO)
            if (!isExactAlarmGranted()) add(NotifStep.EXACT_ALARM)
            if (!isBatteryOptimizationIgnored()) add(NotifStep.BATTERY)
        }
    }

    // 全て許可済みなら即座に完了
    LaunchedEffect(steps) {
        if (steps.isEmpty()) onComplete()
    }
    if (steps.isEmpty()) return

    // 各ステップをチュートリアルページに変換する
    val pages = steps.map { step ->
        when (step) {
            NotifStep.INTRO -> TutorialPage(
                title = "通知の許可",
                description = "予定の開始時刻や10分前に\n通知を受け取るための許可をお願いします。",
                illustration = { colors -> NotificationIllustration(colors) },
            )
            NotifStep.EXACT_ALARM -> TutorialPage(
                title = "正確なアラームの許可",
                description = "予定時刻ぴったりに通知を届けるために、\n「正確なアラーム」の許可が必要です。",
                illustration = { colors -> AlarmIllustration(colors) },
            )
            NotifStep.BATTERY -> TutorialPage(
                title = "バッテリー最適化の無効化",
                description = "スリープ中の通知遅延を防ぐために、\nバッテリー最適化を無効化することをおすすめします。",
                illustration = { colors -> BatteryIllustration(colors) },
            )
        }
    }

    // 権限リクエストの応答後に advance を呼ぶための保留フック
    var pendingAdvance by remember { mutableStateOf<(() -> Unit)?>(null) }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        pendingAdvance?.invoke()
        pendingAdvance = null
    }

    SetupTutorialScaffold(
        pages = pages,
        colors = colors,
        onComplete = onComplete,
        swipeEnabled = false,
        confirmLabelProvider = { pageIndex, _ ->
            when (steps[pageIndex]) {
                NotifStep.INTRO ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) "許可する" else "次へ"
                NotifStep.EXACT_ALARM -> "設定を開く"
                NotifStep.BATTERY -> "設定を開く"
            }
        },
        dismissLabel = "あとで",
        onConfirmOverride = { pageIndex, advance ->
            when (steps[pageIndex]) {
                NotifStep.INTRO -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        pendingAdvance = advance
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        advance()
                    }
                }
                NotifStep.EXACT_ALARM -> {
                    try {
                        context.startActivity(
                            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                                data = Uri.parse("package:${context.packageName}")
                            }
                        )
                    } catch (_: Exception) { }
                    advance()
                }
                NotifStep.BATTERY -> {
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
        },
    )
}

// 通知ベル風のイラスト（大きなベルと波紋）
@Composable
private fun NotificationIllustration(colors: AppColors) {
    TutorialIllustrationFrame(colors) {
        Box(contentAlignment = Alignment.Center) {
            // 外側の薄い円（波紋）
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(RoundedCornerShape(60.dp))
                    .background(colors.primaryAccent.copy(alpha = 0.12f)),
            )
            // 内側の円
            Box(
                modifier = Modifier
                    .size(84.dp)
                    .clip(RoundedCornerShape(42.dp))
                    .background(colors.primaryAccent.copy(alpha = 0.22f)),
            )
            // 中央のベル絵文字
            Text("🔔", fontSize = 44.sp)
        }
    }
}

// 時計風のイラスト（円形ダイヤル）
@Composable
private fun AlarmIllustration(colors: AppColors) {
    TutorialIllustrationFrame(colors) {
        Box(contentAlignment = Alignment.Center) {
            // 時計の外枠
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(RoundedCornerShape(48.dp))
                    .background(colors.surface)
                    .border(3.dp, colors.primaryAccent, RoundedCornerShape(48.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("10:00", color = colors.primaryAccent, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(2.dp))
                    Text("ON TIME", color = colors.textGray, fontSize = 10.sp, fontWeight = FontWeight.Medium)
                }
            }
            // 上部のアラーム突起
            Box(
                modifier = Modifier
                    .padding(bottom = 90.dp)
                    .width(28.dp)
                    .height(10.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(colors.primaryAccent),
            )
        }
    }
}

// バッテリー風のイラスト（横向きバッテリー＋稲妻）
@Composable
private fun BatteryIllustration(colors: AppColors) {
    TutorialIllustrationFrame(colors) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // バッテリー本体
            Box(
                modifier = Modifier
                    .width(120.dp)
                    .height(56.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.surface)
                    .border(3.dp, colors.primaryAccent, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center,
            ) {
                // 充填ゲージ
                Box(
                    modifier = Modifier
                        .padding(6.dp)
                        .width(84.dp)
                        .height(32.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(colors.primaryAccent.copy(alpha = 0.25f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("⚡", fontSize = 22.sp, color = Color(0xFFFFB300))
                }
            }
            // 右端の突起（バッテリー端子）
            Box(
                modifier = Modifier
                    .padding(start = 4.dp)
                    .width(8.dp)
                    .height(22.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(colors.primaryAccent),
            )
        }
    }
}