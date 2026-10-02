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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.monsivamon.golender.data.LocalEvent
import com.monsivamon.golender.data.util.AiJsonConverter
import com.monsivamon.golender.data.util.AiPromptTemplate
import com.monsivamon.golender.ui.components.AiPreviewList
import com.monsivamon.golender.ui.theme.AppColors
import java.time.LocalDate
import java.time.ZoneId

// AIに画像を読み取らせて予定を一括登録するダイアログ（Golendarモード専用）
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
    // プロンプトのプレースホルダを今日の日付・TZに置換する
    val prompt = AiPromptTemplate.PROMPT_TEMPLATE
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
                    "解析結果に警告があります",
                    color = colors.text,
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    pendingWarnings.forEach { warning ->
                        Text(
                            "・$warning",
                            color = colors.textGray,
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showWarningDialog = false }) {
                    Text("確認しました", color = colors.primaryAccent, fontWeight = FontWeight.Bold)
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
                    // タイトルと説明文（Golendarモード専用を明示）
                    Text("AIに読み取らせる (BETA)", style = MaterialTheme.typography.titleLarge, color = colors.text)
                    Text(
                        "Golendarモード専用機能です。\n" +
                        "画像認識が可能なAI（ChatGPT / Gemini / Qwen / DeepSeek など）に、\n" +
                        "画像とプロンプトを渡してください。返ってきたJSONを貼り付けてください。",
                        color = colors.textGray,
                        fontSize = 14.sp
                    )
                    // プロンプトのコピー／AIアプリ起動ボタン
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("Golendar AI Prompt", prompt))
                            Toast.makeText(context, "コピーしました", Toast.LENGTH_SHORT).show()
                        }) { Text("コピー") }
                        Button(onClick = {
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, prompt)
                                putExtra(Intent.EXTRA_SUBJECT, "Golendar 予定解析プロンプト")
                            }
                            context.startActivity(Intent.createChooser(intent, "解析するAIを選択"))
                            Toast.makeText(context, "AIに画像と一緒に渡してください", Toast.LENGTH_LONG).show()
                        }) { Text("AIアプリを開く") }
                    }
                    // AIの回答JSONを貼り付ける入力欄
                    OutlinedTextField(
                        value = rawText,
                        onValueChange = { rawText = it },
                        label = { Text("AIの回答JSONを貼り付け") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 4,
                        maxLines = 8
                    )
                    // JSONを解析してプレビューを生成するボタン
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
                                Toast.makeText(context, "AI出力を解析できませんでした", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("解析して登録") }
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
                            )
                            Text(
                                "すべて選択",
                                color = colors.text,
                                fontSize = 14.sp,
                            )
                            Spacer(Modifier.weight(1f))
                            Text(
                                "選択中: ${selectedIndices.size}件",
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
                            "※ チェックした予定のみ追加されます",
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
                            "${previewEvents.size}件中 ${selectedIndices.size}件を選択中",
                            color = colors.textGray,
                            fontSize = 13.sp,
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    // 右: キャンセルと追加ボタン
                    TextButton(onClick = onDismiss) { Text("キャンセル") }
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
                        Text("追加")
                    }
                }
            }
        }
    }
}