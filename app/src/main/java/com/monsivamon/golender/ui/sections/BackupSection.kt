package com.monsivamon.golender.ui.sections

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.monsivamon.golender.data.source.CalendarMeta
import com.monsivamon.golender.ui.SettingsSection
import com.monsivamon.golender.ui.dialogs.RestoreTargetPickerDialog
import com.monsivamon.golender.ui.theme.AppColors
import com.monsivamon.golender.viewmodel.CalendarMode
import com.monsivamon.golender.viewmodel.CalendarViewModel

// バックアップと復元セクション。写真オプション・ファイル選択ランチャー・復元先ピッカーを内包する
@Composable
fun BackupSection(
    viewModel: CalendarViewModel,
    colors: AppColors,
) {
    val context = LocalContext.current
    val calendarMode by viewModel.calendarMode.collectAsState()
    val backupPhotos by viewModel.backupPhotos.collectAsState()

    // 復元先カレンダー選択ダイアログの状態
    var showRestoreTargetDialog by remember { mutableStateOf(false) }
    var pendingRestoreTargetCalendarId by remember { mutableStateOf<Long?>(null) }

    // 保存／追記／復元のファイル選択ランチャー
    val backupMime = if (backupPhotos) "application/zip" else "application/json"
    val backupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(backupMime),
    ) { uri: Uri? -> uri?.let { viewModel.exportBackup(it) } }
    val appendLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        val target = pendingRestoreTargetCalendarId
        pendingRestoreTargetCalendarId = null
        uri?.let { viewModel.importBackup(it, isAppend = true, targetCalendarId = target) }
    }
    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? -> uri?.let { viewModel.importBackup(it, isAppend = false) } }

    SettingsSection("バックアップと復元", colors) {
        Text(
            "現在選択されているカレンダーの予定と設定をJSONで保存します。保存したファイルから別アカウントやGolendarモードへの「追記」が可能です。\n※Googleモードでの「復元（上書き）」はデータ保護のため実行できません。",
            fontSize = 13.sp, color = colors.textGray, modifier = Modifier.padding(bottom = 12.dp),
        )
        // Golendar モード時のみ写真もバックアップの選択を表示
        if (calendarMode == CalendarMode.GOLENDAR) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.setBackupPhotos(!backupPhotos) }
                    .padding(vertical = 8.dp),
            ) {
                Checkbox(
                    checked = backupPhotos,
                    onCheckedChange = { viewModel.setBackupPhotos(it) },
                    colors = CheckboxDefaults.colors(
                        checkedColor = colors.primaryAccent,
                        uncheckedColor = colors.textGray,
                    ),
                )
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(
                        "写真もバックアップ",
                        fontSize = 15.sp, color = colors.text, fontWeight = FontWeight.Medium,
                    )
                    Text(
                        if (backupPhotos)
                            "予定に添付した写真も ZIP ファイルに含めます（Golendarモードのみ）"
                        else
                            "OFF の場合は従来通り JSON で保存されます",
                        fontSize = 12.sp, color = colors.textGray,
                    )
                }
            }
            HorizontalDivider(color = colors.divider, modifier = Modifier.padding(vertical = 8.dp))
        }
        // 保存／追記／復元ボタン
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    val name = if (backupPhotos) "golendar_backup.zip" else "golendar_backup.json"
                    backupLauncher.launch(name)
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.surface,
                    contentColor = colors.text,
                ),
                modifier = Modifier.weight(1f).border(1.dp, colors.divider, RoundedCornerShape(24.dp)),
                contentPadding = PaddingValues(0.dp),
            ) { Text("保存", fontSize = 14.sp) }
            Button(
                onClick = {
                    if (calendarMode == CalendarMode.GOOGLE) {
                        showRestoreTargetDialog = true
                    } else {
                        pendingRestoreTargetCalendarId = null
                        appendLauncher.launch(arrayOf("application/json", "*/*"))
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.surface,
                    contentColor = colors.text,
                ),
                modifier = Modifier.weight(1f).border(1.dp, colors.divider, RoundedCornerShape(24.dp)),
                contentPadding = PaddingValues(0.dp),
            ) { Text("追記", fontSize = 14.sp) }
            Button(
                onClick = {
                    if (calendarMode == CalendarMode.GOOGLE) {
                        Toast.makeText(context, "Googleモードでは追記のみ可能です", Toast.LENGTH_SHORT).show()
                    } else {
                        restoreLauncher.launch(arrayOf("application/json", "*/*"))
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (calendarMode == CalendarMode.GOOGLE) colors.bg else colors.surface,
                    contentColor = if (calendarMode == CalendarMode.GOOGLE) colors.textGray else colors.sunRed,
                ),
                modifier = Modifier.weight(1f).border(1.dp, colors.divider, RoundedCornerShape(24.dp)),
                contentPadding = PaddingValues(0.dp),
            ) { Text("復元", fontSize = 14.sp) }
        }
    }

    // 復元先カレンダー選択ダイアログ
    if (showRestoreTargetDialog) {
        val calendarsState = remember { mutableStateOf<List<CalendarMeta>>(emptyList()) }
        LaunchedEffect(Unit) {
            calendarsState.value = viewModel.loadAllCalendarMetas()
        }
        RestoreTargetPickerDialog(
            colors = colors,
            calendars = calendarsState.value,
            onDismiss = { showRestoreTargetDialog = false },
            onConfirm = { meta ->
                showRestoreTargetDialog = false
                pendingRestoreTargetCalendarId = meta.id
                appendLauncher.launch(arrayOf("application/json", "*/*"))
            },
        )
    }
}