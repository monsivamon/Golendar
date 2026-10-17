package com.monsivamon.golender.ui.dialogs

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.monsivamon.golender.R
import com.monsivamon.golender.data.LocalEvent
import com.monsivamon.golender.data.util.AiJsonConverter
import com.monsivamon.golender.data.util.AiPromptTemplate
import com.monsivamon.golender.ui.components.AiPreviewList
import com.monsivamon.golender.ui.theme.AppColors
import java.time.LocalDate
import java.time.ZoneId

// AI に画像を読み取らせて予定を一括登録するダイアログ（Golendar モード専用）
@Composable
fun AiParseDialog(
    colors: AppColors,
    needsSetup: Boolean,
    onSetupComplete: () -> Unit,
    onDismiss: () -> Unit,
    onConfirm: (List<LocalEvent>) -> Unit,
) {
    // 初回説明が必要な場合はセットアップダイアログを表示する
    var showSetup by remember { mutableStateOf(needsSetup) }
    if (showSetup) {
        AiSetupDialog(
            colors = colors,
            onComplete = {
                showSetup = false
                onSetupComplete()
            },
        )
        return
    }
    // コンテキストとダイアログ内の各状態を保持する
    val context = LocalContext.current
    var rawText by remember { mutableStateOf("") }
    var previewEvents by remember { mutableStateOf<List<LocalEvent>>(emptyList()) }
    var selectedIndices by remember { mutableStateOf<Set<Int>>(emptySet()) }
    // 警告モーダルの状態
    var pendingWarnings by remember { mutableStateOf<List<String>>(emptyList()) }
    var showWarningDialog by remember { mutableStateOf(false) }
    // プロンプトのプレースホルダを今日の日付・TZ に置換する
    val prompt = AiPromptTemplate.promptTemplate(context)
        .replace("{{TODAY}}", LocalDate.now().toString())
        .replace("{{TZ}}", ZoneId.systemDefault().id)
    // 全件選択済みかどうかを判定する
    val allSelected = previewEvents.isNotEmpty() && selectedIndices.size == previewEvents.size
    // 警告モーダルを表示する
    if (showWarningDialog && pendingWarnings.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { },
            containerColor = colors.surface,
            title = {
                Text(
                    stringResource(R.string.dl2_aiParseWarningTitle),
                    color = colors.text,
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // 警告を 1 件ずつ列挙する
                    pendingWarnings.forEach { warning ->
                        Text(
                            stringResource(R.string.dl2_aiParseWarningItem, warning),
                            color = colors.textGray,
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showWarningDialog = false }) {
                    Text(stringResource(R.string.dl2_aiParseAcknowledge), color = colors.primaryAccent, fontWeight = FontWeight.Bold)
                }
            },
        )
    }
    // 全幅表示のカスタムダイアログ枠
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            color = colors.surface,
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        ) {
            // 上部スクロール領域＋下部固定フッターの構成
            Column(modifier = Modifier.fillMaxWidth()) {
                // 上部: タイトル・説明・入力欄（スクロール可）
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp, vertical = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // タイトルと説明文（Golendar モード専用を明示）
                    Text(stringResource(R.string.dl2_aiParseTitle), style = MaterialTheme.typography.titleLarge, color = colors.text)
                    Text(
                        stringResource(R.string.dl2_aiParseDescription),
                        color = colors.textGray,
                        fontSize = 14.sp
                    )
                    // プロンプトのコピー／AI アプリ起動ボタン
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("Golendar AI Prompt", prompt))
                            Toast.makeText(context, context.getString(R.string.dl2_copied), Toast.LENGTH_SHORT).show()
                        }) { Text(stringResource(R.string.dl2_copy)) }
                        Button(onClick = {
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, prompt)
                                putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.dl2_aiPromptSubject))
                            }
                            context.startActivity(Intent.createChooser(intent, context.getString(R.string.dl2_chooseAiApp)))
                            Toast.makeText(context, context.getString(R.string.dl2_passWithPhoto), Toast.LENGTH_LONG).show()
                        }) { Text(stringResource(R.string.dl2_openAiApp)) }
                    }
                    // AI の回答 JSON を貼り付ける入力欄
                    OutlinedTextField(
                        value = rawText,
                        onValueChange = { rawText = it },
                        label = { Text(stringResource(R.string.dl2_aiParseInputLabel)) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 4,
                        maxLines = 8,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = colors.text,
                            unfocusedTextColor = colors.text,
                            focusedBorderColor = colors.primaryAccent,
                            unfocusedBorderColor = colors.textGray,
                            cursorColor = colors.primaryAccent,
                            focusedLabelColor = colors.primaryAccent,
                            unfocusedLabelColor = colors.textGray,
                        ),
                    )
                    // JSON を解析してプレビューを生成するボタン
                    Button(
                        onClick = {
                            val result = AiJsonConverter.convert(rawText)
                            if (result.events.isNotEmpty()) {
                                previewEvents = result.events
                                selectedIndices = result.events.indices.toSet()
                                // 警告がある場合はモーダルで全件表示する
                                if (result.warnings.isNotEmpty()) {
                                    pendingWarnings = result.warnings
                                    showWarningDialog = true
                                }
                            } else {
                                Toast.makeText(context, context.getString(R.string.dl2_parseFailed), Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(stringResource(R.string.dl2_parseAndAdd)) }
                    // 中部: プレビューリスト（スクロール可）
                    if (previewEvents.isNotEmpty()) {
                        HorizontalDivider(color = colors.divider)
                        // 全選択トグルと選択件数表示
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = allSelected,
                                onCheckedChange = { checked ->
                                    selectedIndices = if (checked) {
                                        previewEvents.indices.toSet()
                                    } else {
                                        emptySet()
                                    }
                                },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = colors.primaryAccent,
                                    uncheckedColor = colors.text,
                                ),
                            )
                            Text(
                                stringResource(R.string.dl2_selectAllKana),
                                color = colors.text,
                                fontSize = 14.sp,
                            )
                            Spacer(Modifier.weight(1f))
                            Text(
                                stringResource(R.string.dl2_selectionCount, selectedIndices.size),
                                color = colors.textGray,
                                fontSize = 13.sp,
                            )
                        }
                        AiPreviewList(
                            events = previewEvents,
                            selected = selectedIndices,
                            colors = colors,
                            onToggle = { idx ->
                                selectedIndices = if (idx in selectedIndices) {
                                    selectedIndices - idx
                                } else {
                                    selectedIndices + idx
                                }
                            }
                        )
                        // チェックと追加の関係を示す注記
                        Text(
                            stringResource(R.string.dl2_aiParseCheckboxNote),
                            color = colors.textGray,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
                // 下部: 固定フッター（常時表示）
                HorizontalDivider(color = colors.divider)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // 左: 選択件数のステータス表示
                    if (previewEvents.isNotEmpty()) {
                        Text(
                            stringResource(R.string.dl2_footerSelectionStatus, previewEvents.size, selectedIndices.size),
                            color = colors.textGray,
                            fontSize = 13.sp,
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    // 右: キャンセルと追加ボタン
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.dl2_cancel), color = colors.textGray) }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val toAdd = selectedIndices.mapNotNull { idx ->
                                previewEvents.getOrNull(idx)
                            }
                            onConfirm(toAdd)
                        },
                        enabled = selectedIndices.isNotEmpty(),
                    ) {
                        Text(stringResource(R.string.dl2_add))
                    }
                }
            }
        }
    }
}