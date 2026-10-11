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
import com.monsivamon.golender.ui.SettingsSection
import com.monsivamon.golender.ui.dialogs.RestoreOptionsDialog
import com.monsivamon.golender.ui.dialogs.RestoreTargetCalendar
import com.monsivamon.golender.ui.theme.AppColors
import com.monsivamon.golender.viewmodel.BackupManager
import com.monsivamon.golender.viewmodel.CalendarMode
import com.monsivamon.golender.viewmodel.CalendarViewModel
import kotlinx.coroutines.launch

// バックアップと復元セクション。
// ファイル選択 → peek で プレビュー → RestoreOptionsDialog で復元先と元カレンダーを選択 → 実行
// 可視カレンダーが複数ある場合は、保存前にまとめてバックアップする旨の確認ダイアログを表示する。
@Composable
fun BackupSection(
    viewModel: CalendarViewModel,
    colors: AppColors,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val calendarMode by viewModel.calendarMode.collectAsState()
    val backupPhotos by viewModel.backupPhotos.collectAsState()
    val localCalendars by viewModel.localCalendars.collectAsState()
    val selectedCalendars by viewModel.selectedCalendars.collectAsState()

    // バックアップ対象となる可視カレンダー一覧（モードに応じて切替）
    val visibleCalendars: List<Pair<Long, String>> = if (calendarMode == CalendarMode.GOOGLE) {
        selectedCalendars.filter { it.isVisible }.map { it.calendarId to it.displayName }
    } else {
        localCalendars.filter { it.isVisible }.map { it.id to it.name }
    }
    val visibleCount = visibleCalendars.size

    // 複数カレンダー確認ダイアログの表示フラグ
    var showMultiCalendarConfirm by remember { mutableStateOf(false) }

    // 復元ダイアログ用の保留状態
    var pendingUri by remember { mutableStateOf<Uri?>(null) }
    var pendingIsAppend by remember { mutableStateOf(false) }
    var pendingMetadata by remember { mutableStateOf<BackupManager.Metadata?>(null) }
    var pendingTargets by remember { mutableStateOf<List<RestoreTargetCalendar>>(emptyList()) }

    // 上書き確認ダイアログ用の保留状態
    // （Golendar モードで同名カレンダーが既存の場合に表示する）
    var pendingOverwriteNames by remember { mutableStateOf<List<String>>(emptyList()) }
    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }
    var pendingImportIsAppend by remember { mutableStateOf(false) }
    var pendingImportSelectedSources by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var showOverwriteConfirm by remember { mutableStateOf(false) }

    // バックアップのファイル選択ランチャー
    val backupMime = if (backupPhotos) "application/zip" else "application/json"
    val backupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(backupMime),
    ) { uri: Uri? -> uri?.let { viewModel.exportBackup(it) } }

    // ファイル名を決めてバックアップランチャーを起動する
    // ファイル名末尾に YYMMDD 形式の日付を付与する（例: golendar_backup_261010.json）
    fun launchBackupLauncher() {
        val stamp = java.time.LocalDate.now()
            .format(java.time.format.DateTimeFormatter.ofPattern("yyMMdd"))
        val name = if (backupPhotos) {
            "golendar_backup_$stamp.zip"
        } else {
            "golendar_backup_$stamp.json"
        }
        backupLauncher.launch(name)
    }

    // peek して復元オプションダイアログを表示する共通処理
    fun beginRestore(uri: Uri, isAppend: Boolean) {
        scope.launch {
            val meta = viewModel.peekBackup(uri)
            if (meta == null) {
                Toast.makeText(context, "このファイルは復元できません", Toast.LENGTH_SHORT).show()
                return@launch
            }
            // 旧形式バックアップ:
            //   追記 → Google / Golendar どちらも「追記先カレンダー」を選んで追記
            //          （写真入り ZIP は Google モードでは追記不可）
            //   復元 → Golendar モードのみ、マイカレンダーへ直接復元
            if (meta.isLegacyFormat) {
                if (isAppend) {
                    if (calendarMode == CalendarMode.GOOGLE && meta.isZip) {
                        Toast.makeText(context, "写真付きバックアップは Golendar モードでのみ追記できます", Toast.LENGTH_LONG).show()
                        return@launch
                    }
                    val targets: List<RestoreTargetCalendar> = if (calendarMode == CalendarMode.GOOGLE) {
                        viewModel.loadAllCalendarMetas()
                            .filter { it.accessLevel >= 500 && !it.isHoliday && !it.isBirthday && it.accountName.contains("@") }
                            .map { RestoreTargetCalendar(it.id, it.displayName.ifBlank { "名称未設定" }, it.defaultColor) }
                    } else {
                        localCalendars.map { RestoreTargetCalendar(it.id, it.name, it.colorArgb) }
                    }
                    pendingUri = uri
                    pendingIsAppend = true
                    pendingMetadata = meta
                    pendingTargets = targets
                    return@launch
                }
                if (calendarMode == CalendarMode.GOOGLE) {
                    Toast.makeText(context, "旧形式のバックアップは Google モードでは復元できません。追記をご利用ください。", Toast.LENGTH_LONG).show()
                    return@launch
                }
                viewModel.importBackup(uri = uri, isAppend = false, selectedSourceIds = setOf(0L), autoCreateMissingCalendars = true)
                return@launch
            }
            val targets: List<RestoreTargetCalendar> = if (calendarMode == CalendarMode.GOOGLE) {
                viewModel.loadAllCalendarMetas()
                    .filter { it.accessLevel >= 500 && !it.isHoliday && !it.isBirthday && it.accountName.contains("@") }
                    .map { RestoreTargetCalendar(it.id, it.displayName.ifBlank { "名称未設定" }, it.defaultColor) }
            } else {
                localCalendars.map { RestoreTargetCalendar(it.id, it.name, it.colorArgb) }
            }
            pendingUri = uri
            pendingIsAppend = isAppend
            pendingMetadata = meta
            pendingTargets = targets
        }
    }

    val appendLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? -> uri?.let { beginRestore(it, isAppend = true) } }
    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? -> uri?.let { beginRestore(it, isAppend = false) } }

    SettingsSection("バックアップと復元", colors) {
        Text(
            "保存: 可視カレンダーの予定をまとめて書き出します。\n" +
                "復元: 同名カレンダーを上書きします（Golendarモードのみ）。\n" +
                "追記: 既存の予定を残したまま追加します。",
            fontSize = 13.sp, color = colors.textGray, modifier = Modifier.padding(bottom = 12.dp),
        )

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

        // 3 ボタン: 保存 / 追記 / 復元
        // - 保存: 両モードで利用可
        // - 追記: 両モードで利用可
        // - 復元: Golendar モード専用。Google モードでは押下時にトースト案内
        val isRestoreEnabled = calendarMode == CalendarMode.GOLENDAR
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // 保存ボタン: 可視カレンダーが複数なら確認ダイアログを挟む
            Button(
                onClick = {
                    if (visibleCount >= 2) {
                        showMultiCalendarConfirm = true
                    } else {
                        launchBackupLauncher()
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.surface,
                    contentColor = colors.text,
                ),
                modifier = Modifier.weight(1f).border(1.dp, colors.divider, RoundedCornerShape(24.dp)),
                contentPadding = PaddingValues(0.dp),
            ) { Text("保存", fontSize = 14.sp) }

            // 追記ボタン
            Button(
                onClick = {
                    appendLauncher.launch(arrayOf("application/json", "application/zip", "*/*"))
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.surface,
                    contentColor = colors.text,
                ),
                modifier = Modifier.weight(1f).border(1.dp, colors.divider, RoundedCornerShape(24.dp)),
                contentPadding = PaddingValues(0.dp),
            ) { Text("追記", fontSize = 14.sp) }

            // 復元ボタン
            Button(
                onClick = {
                    if (isRestoreEnabled) {
                        restoreLauncher.launch(arrayOf("application/json", "application/zip", "*/*"))
                    } else {
                        Toast.makeText(
                            context,
                            "Googleモードでは復元できません。追記をご利用ください。",
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isRestoreEnabled) colors.surface else colors.bg,
                    contentColor = if (isRestoreEnabled) colors.sunRed else colors.textGray,
                ),
                modifier = Modifier.weight(1f).border(1.dp, colors.divider, RoundedCornerShape(24.dp)),
                contentPadding = PaddingValues(0.dp),
            ) { Text("復元", fontSize = 14.sp) }
        }
    }

    // ============================================================
    // 複数カレンダー確認ダイアログ
    // ============================================================
    if (showMultiCalendarConfirm) {
        AlertDialog(
            onDismissRequest = { showMultiCalendarConfirm = false },
            containerColor = colors.surface,
            title = {
                Text(
                    "複数のカレンダーをまとめてバックアップ",
                    color = colors.text, fontWeight = FontWeight.Bold,
                )
            },
            text = {
                Column {
                    Text(
                        "複数のカレンダーが表示されています。\n" +
                            "まとめてバックアップしてよろしいですか？",
                        color = colors.text, fontSize = 14.sp,
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "対象: ${visibleCount} 件",
                        color = colors.textGray, fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(4.dp))
                    visibleCalendars.forEach { (_, name) ->
                        Text(
                            "・${name.ifBlank { "名称未設定" }}",
                            color = colors.textGray, fontSize = 12.sp,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showMultiCalendarConfirm = false
                    launchBackupLauncher()
                }) {
                    Text("OK", color = colors.primaryAccent, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showMultiCalendarConfirm = false }) {
                    Text("キャンセル", color = colors.textGray)
                }
            },
        )
    }

    // ============================================================
    // 復元オプションダイアログ（既存処理）
    // ============================================================
    val uri = pendingUri
    val meta = pendingMetadata
    if (uri != null && meta != null) {
        RestoreOptionsDialog(
            colors = colors,
            metadata = meta,
            targets = pendingTargets,
            isAppend = pendingIsAppend,
            allowAutoCreate = calendarMode == CalendarMode.GOLENDAR && !meta.isLegacyFormat,
            onDismiss = {
                pendingUri = null
                pendingMetadata = null
                pendingTargets = emptyList()
            },
            onConfirm = { targetId, selectedSourceIds, autoCreateMissing ->
                // Golendar の自動振り分け時は、選択されたソースカレンダーの
                // 名前が既存のローカルカレンダーと一致するか検査する。
                // 一致するものがあれば、上書き確認ダイアログを挟む。
                val matched = if (calendarMode == CalendarMode.GOLENDAR && autoCreateMissing) {
                    val sourceNames = meta.sourceCalendars
                        .filter { it.id in selectedSourceIds && it.id != 0L }
                        .map { it.name }
                    val localNames = localCalendars.map { it.name }.toSet()
                    sourceNames.filter { it in localNames }.distinct()
                } else emptyList()

                if (matched.isNotEmpty()) {
                    pendingOverwriteNames = matched
                    pendingImportUri = uri
                    pendingImportIsAppend = pendingIsAppend
                    pendingImportSelectedSources = selectedSourceIds
                    showOverwriteConfirm = true
                    pendingUri = null
                    pendingMetadata = null
                    pendingTargets = emptyList()
                } else {
                    viewModel.importBackup(
                        uri = uri,
                        isAppend = pendingIsAppend,
                        targetGoogleCalendarId = if (calendarMode == CalendarMode.GOOGLE) targetId else null,
                        targetLocalCalendarId = if (calendarMode == CalendarMode.GOLENDAR) targetId else null,
                        selectedSourceIds = selectedSourceIds,
                        autoCreateMissingCalendars = autoCreateMissing,
                    )
                    pendingUri = null
                    pendingMetadata = null
                    pendingTargets = emptyList()
                }
            },
        )
    }

    // ============================================================
    // 同名カレンダー上書き確認ダイアログ
    // ============================================================
    if (showOverwriteConfirm) {
        AlertDialog(
            onDismissRequest = {
                showOverwriteConfirm = false
                pendingImportUri = null
                pendingOverwriteNames = emptyList()
                pendingImportSelectedSources = emptySet()
            },
            containerColor = colors.surface,
            title = {
                Text(
                    "同名カレンダーが存在します",
                    color = colors.text,
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                Column {
                    Text(
                        "以下のカレンダーは既に存在します。\n" +
                            "復元すると、もとのカレンダーの予定は上書きされます。\n" +
                            "よろしいですか？",
                        color = colors.text,
                        fontSize = 14.sp,
                    )
                    Spacer(Modifier.height(10.dp))
                    pendingOverwriteNames.forEach { name ->
                        Text(
                            "・$name",
                            color = colors.sunRed,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "※ バックアップに含まれないカレンダーはそのまま残ります。",
                        color = colors.textGray,
                        fontSize = 12.sp,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val u = pendingImportUri
                    if (u != null) {
                        viewModel.importBackup(
                            uri = u,
                            isAppend = pendingImportIsAppend,
                            selectedSourceIds = pendingImportSelectedSources,
                            autoCreateMissingCalendars = true,
                        )
                    }
                    showOverwriteConfirm = false
                    pendingImportUri = null
                    pendingOverwriteNames = emptyList()
                    pendingImportSelectedSources = emptySet()
                }) {
                    Text("上書きする", color = colors.sunRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showOverwriteConfirm = false
                    pendingImportUri = null
                    pendingOverwriteNames = emptyList()
                    pendingImportSelectedSources = emptySet()
                }) {
                    Text("キャンセル", color = colors.textGray)
                }
            },
        )
    }
}