package com.monsivamon.golender.ui.sections

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.monsivamon.golender.R
import com.monsivamon.golender.ui.LIST_ROW_VERTICAL
import com.monsivamon.golender.ui.SECTION_DIVIDER_VERTICAL
import com.monsivamon.golender.ui.SECTION_LABEL_BOTTOM
import com.monsivamon.golender.ui.SettingsSection
import com.monsivamon.golender.ui.common.PermissionSettingsDialog
import com.monsivamon.golender.ui.common.isPermissionPermanentlyDenied
import com.monsivamon.golender.ui.common.openAppSettings
import com.monsivamon.golender.ui.theme.AppColors
import com.monsivamon.golender.viewmodel.CalendarViewModel

// 通知設定セクション。通知権限・正確なアラーム・バッテリー最適化の状態を自身で管理する
@Composable
fun NotificationSection(
    viewModel: CalendarViewModel,
    colors: AppColors,
) {
    val context = LocalContext.current
    val notifyAtStart by viewModel.notifyAtStart.collectAsState()
    val notify10MinBefore by viewModel.notify10MinBefore.collectAsState()

    // 権限状態（このセクションが自分で保持する）
    var notificationPermissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                    == PackageManager.PERMISSION_GRANTED,
        )
    }
    // 恒久拒否（Android 11+ の USER_FIXED）で「設定を開く」ダイアログを表示するフラグ
    var showNotificationPermanentDenied by remember { mutableStateOf(false) }
    var exactAlarmPermissionGranted by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).canScheduleExactAlarms()
            } else true,
        )
    }
    val powerManager = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
    var isIgnoringBatteryOptimizations by remember {
        mutableStateOf(powerManager.isIgnoringBatteryOptimizations(context.packageName))
    }

    // 通知権限要求ランチャー
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { isGranted ->
        notificationPermissionGranted = isGranted
        if (!isGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            isPermissionPermanentlyDenied(context, Manifest.permission.POST_NOTIFICATIONS)
        ) {
            showNotificationPermanentDenied = true
        }
    }

    // 権限状態を再取得して UI に反映する
    fun refreshPermissions() {
        notificationPermissionGranted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            exactAlarmPermissionGranted =
                (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).canScheduleExactAlarms()
        }
        isIgnoringBatteryOptimizations = powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }
    LaunchedEffect(Unit) { refreshPermissions() }

    SettingsSection(stringResource(R.string.sc_notificationSection), colors) {
        // 定刻通知 ON/OFF
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
            Switch(
                checked = notifyAtStart,
                onCheckedChange = { viewModel.setNotifyOptions(it, notify10MinBefore) },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = colors.primaryAccent,
                    checkedTrackColor = colors.primaryAccent.copy(alpha = 0.7f),
                ),
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(stringResource(R.string.sc_notifyAtStart), fontSize = 16.sp, color = colors.text)
        }
        // 10 分前通知 ON/OFF
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 16.dp)) {
            Switch(
                checked = notify10MinBefore,
                onCheckedChange = { viewModel.setNotifyOptions(notifyAtStart, it) },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = colors.primaryAccent,
                    checkedTrackColor = colors.primaryAccent.copy(alpha = 0.7f),
                ),
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(stringResource(R.string.sc_notify10MinBefore), fontSize = 16.sp, color = colors.text)
        }
        HorizontalDivider(color = colors.divider, modifier = Modifier.padding(vertical = SECTION_DIVIDER_VERTICAL))
        Text(
            stringResource(R.string.sc_bgNotificationReliability),
            fontSize = 14.sp, fontWeight = FontWeight.Bold,
            color = colors.textGray,
            modifier = Modifier.padding(bottom = SECTION_LABEL_BOTTOM),
        )
        // バッテリー最適化除外
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth().padding(vertical = LIST_ROW_VERTICAL),
        ) {
            Text(
                stringResource(R.string.sc_batteryOptimizationOff),
                fontSize = 14.sp, color = colors.text, modifier = Modifier.weight(1f),
            )
            if (isIgnoringBatteryOptimizations) {
                Text(stringResource(R.string.sc_batteryOptimizationDone), fontSize = 14.sp, color = colors.primaryAccent)
            } else {
                TextButton(onClick = {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                    context.startActivity(intent)
                }) { Text(stringResource(R.string.sc_openSettings), color = colors.primaryAccent) }
            }
        }
        // 通知権限
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth().padding(vertical = LIST_ROW_VERTICAL),
        ) {
            Text(stringResource(R.string.sc_notificationPermission), fontSize = 14.sp, color = colors.text, modifier = Modifier.weight(1f))
            if (notificationPermissionGranted) {
                Text(stringResource(R.string.sc_permissionGranted), fontSize = 14.sp, color = colors.primaryAccent)
            } else {
                TextButton(onClick = {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }) { Text(stringResource(R.string.sc_grantPermission), color = colors.primaryAccent) }
            }
        }
        // 正確なアラーム（Android 12+ のみ）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth().padding(vertical = LIST_ROW_VERTICAL),
            ) {
                Text(stringResource(R.string.sc_exactAlarmPermission), fontSize = 14.sp, color = colors.text, modifier = Modifier.weight(1f))
                if (exactAlarmPermissionGranted) {
                    Text(stringResource(R.string.sc_permissionGranted), fontSize = 14.sp, color = colors.primaryAccent)
                } else {
                    TextButton(onClick = {
                        context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                            data = Uri.parse("package:${context.packageName}")
                        })
                    }) { Text(stringResource(R.string.sc_grantPermission), color = colors.primaryAccent) }
                }
            }
        }
        // 設定状況の再チェック
        Button(
            onClick = { refreshPermissions() },
            colors = ButtonDefaults.buttonColors(
                containerColor = colors.surface,
                contentColor = colors.textGray,
            ),
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                .border(1.dp, colors.divider, RoundedCornerShape(12.dp)),
        ) {
            Text(stringResource(R.string.sc_recheckSettings), fontSize = 12.sp)
        }
    }

    if (showNotificationPermanentDenied) {
        PermissionSettingsDialog(
            colors = colors,
            title = stringResource(R.string.sc_notificationDeniedTitle),
            message = stringResource(R.string.sc_notificationDeniedMessage),
            onOpenSettings = { openAppSettings(context) },
            onDismiss = { showNotificationPermanentDenied = false },
        )
    }
}