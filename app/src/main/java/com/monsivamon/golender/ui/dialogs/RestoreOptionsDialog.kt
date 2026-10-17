package com.monsivamon.golender.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.monsivamon.golender.R
import com.monsivamon.golender.viewmodel.BackupManager
import com.monsivamon.golender.ui.theme.AppColors

// 復元先カレンダー 1 件分の選択肢
data class RestoreTargetCalendar(
    val id: Long,
    val name: String,
    val colorArgb: Int,
)

// 復元前に「復元先」と「復元する元カレンダー」を選ぶダイアログ
@Composable
fun RestoreOptionsDialog(
    colors: AppColors,
    metadata: BackupManager.Metadata,
    targets: List<RestoreTargetCalendar>,
    isAppend: Boolean,
    allowAutoCreate: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (targetId: Long?, selectedSourceIds: Set<Long>, autoCreateMissing: Boolean) -> Unit,
) {
    var selectedTargetId by remember(targets) {
        mutableStateOf<Long?>(targets.firstOrNull()?.id)
    }
    var selectedSources by remember(metadata) {
        mutableStateOf(metadata.sourceCalendars.map { it.id }.toSet())
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            color = colors.surface,
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp, vertical = 20.dp),
                ) {
                    Text(
                        if (isAppend) stringResource(R.string.dl2_backupAppend) else stringResource(R.string.dl2_backupRestore),
                        style = MaterialTheme.typography.titleLarge,
                        color = colors.text,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.dl2_backupSummary, metadata.sourceCalendars.size, metadata.eventCount) +
                            if (metadata.photoCount > 0) stringResource(R.string.dl2_backupPhotoSuffix, metadata.photoCount) else "",
                        color = colors.textGray,
                        fontSize = 13.sp,
                    )
                    Spacer(Modifier.height(16.dp))

                    // ---- 復元先の指定 ----
                    if (allowAutoCreate) {
                        // Golendar モード: 名前ベースで自動振り分け。
                        // 同名カレンダーが無ければ新規作成し、
                        // 同名カレンダーが既にあれば BackupSection 側で
                        // 上書き確認ダイアログが表示される。
                        Text(
                            stringResource(R.string.dl2_autoCreateExplanation),
                            color = colors.textGray,
                            fontSize = 13.sp,
                        )
                    } else {
                        // Google モード: 復元先カレンダーを選択
                        Text(if (isAppend) stringResource(R.string.dl2_appendTargetLabel) else stringResource(R.string.dl2_restoreTargetLabel), fontWeight = FontWeight.Bold, color = colors.text, fontSize = 14.sp)
                        Spacer(Modifier.height(8.dp))

                        if (targets.isEmpty()) {
                            Text(
                                stringResource(R.string.dl2_noRestoreTargets),
                                color = colors.sunRed,
                                fontSize = 13.sp,
                            )
                        } else {
                            targets.forEach { t ->
                                val isSel = t.id == selectedTargetId
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .selectable(selected = isSel, onClick = { selectedTargetId = t.id })
                                        .padding(vertical = 6.dp, horizontal = 4.dp),
                                ) {
                                    RadioButton(
                                        selected = isSel,
                                        onClick = null,
                                        colors = RadioButtonDefaults.colors(
                                            selectedColor = colors.primaryAccent,
                                            unselectedColor = colors.textGray,
                                        ),
                                    )
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clip(CircleShape)
                                            .background(if (t.colorArgb == 0) Color.Gray else Color(t.colorArgb)),
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Text(t.name.ifBlank { stringResource(R.string.dl2_unnamedCalendar) }, color = colors.text, fontSize = 15.sp)
                                }
                            }
                        }
                    }

                    if (!metadata.isLegacyFormat) {
                    Spacer(Modifier.height(20.dp))
                    HorizontalDivider(color = colors.divider)
                    Spacer(Modifier.height(16.dp))

                    // ---- 復元する元カレンダー (複数選択) ----
                    Text(stringResource(R.string.dl2_restoreSources), fontWeight = FontWeight.Bold, color = colors.text, fontSize = 14.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.dl2_restoreSourcesNote),
                        color = colors.textGray,
                        fontSize = 12.sp,
                    )
                    Spacer(Modifier.height(8.dp))

                    metadata.sourceCalendars.forEach { src ->
                        val isChecked = src.id in selectedSources
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        ) {
                            Checkbox(
                                checked = isChecked,
                                onCheckedChange = { checked ->
                                    selectedSources = if (checked) selectedSources + src.id
                                    else selectedSources - src.id
                                },
                                colors = CheckboxDefaults.colors(checkedColor = colors.primaryAccent, uncheckedColor = colors.text),
                            )
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .background(if (src.colorArgb == 0) Color.Gray else Color(src.colorArgb)),
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(src.name, color = colors.text, fontSize = 14.sp)
                        }
                    }

                    if (metadata.sourceCalendars.size > 1) {
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = {
                                selectedSources = metadata.sourceCalendars.map { it.id }.toSet()
                            }) { Text(stringResource(R.string.dl2_selectAllCalendar), color = colors.primaryAccent) }
                            TextButton(onClick = { selectedSources = emptySet() }) {
                                Text(stringResource(R.string.dl2_deselectAll), color = colors.primaryAccent)
                            }
                        }
                    }
                    }
                }

                HorizontalDivider(color = colors.divider)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.dl2_cancel), color = colors.textGray)
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (allowAutoCreate) {
                                onConfirm(null, selectedSources, true)
                            } else {
                                val t = selectedTargetId ?: return@Button
                                onConfirm(t, selectedSources, false)
                            }
                        },
                        enabled = selectedSources.isNotEmpty() &&
                            (allowAutoCreate || selectedTargetId != null),
                    ) {
                        Text(if (isAppend) stringResource(R.string.dl2_append) else stringResource(R.string.dl2_restore))
                    }
                }
            }
        }
    }
}