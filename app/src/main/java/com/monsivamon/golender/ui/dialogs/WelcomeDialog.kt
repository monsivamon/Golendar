package com.monsivamon.golender.ui.dialogs

import coil.compose.AsyncImage
import androidx.compose.ui.res.stringResource
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.monsivamon.golender.R
import com.monsivamon.golender.data.prefs.LanguageSetting
import com.monsivamon.golender.ui.theme.AppColors
import com.monsivamon.golender.viewmodel.WelcomeInfo
import com.monsivamon.golender.viewmodel.WelcomeMode
import kotlinx.coroutines.delay

// Welcome 画面。初回起動時と、バージョンアップ直後の両方で表示する。
//
// 言語選択は プルダウン（はじめる ボタンの直上）から行う。選択すると
// onLanguageSelected で選択値の保存を依頼し、選択中言語で文言を即時切り替える。
// onStart に言語が選択されたかどうかを渡し、選択されていれば Activity 再生成で
// アプリ全体に新しい言語を反映する。
@Composable
fun WelcomeDialog(
    colors: AppColors,
    info: WelcomeInfo,
    onLanguageSelected: (String) -> Unit,
    onStart: (languageChanged: Boolean) -> Unit,
) {
    // 表示直後にフェードイン + 軽いスケールアニメーション
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(60)
        visible = true
    }
    val contentAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(700, easing = FastOutSlowInEasing),
        label = "welcomeAlpha",
    )
    val contentScale by animateFloatAsState(
        targetValue = if (visible) 1f else 0.92f,
        animationSpec = tween(700, easing = FastOutSlowInEasing),
        label = "welcomeScale",
    )

    // mode に応じた表示内容
    val isUpdate = info.mode == WelcomeMode.VERSION_UPDATE

    // ---------- アプリ内言語の簡易選択（はじめる ボタンの直上のプルダウン） ----------
    val context = LocalContext.current
    // 現在の選択値（未設定なら端末言語から初期言語を判定する）
    var activeTag by remember {
        mutableStateOf(
            runCatching { LanguageSetting.sync(context) }
                .getOrDefault(LanguageSetting.defaultTag(context))
        )
    }
    // 選択中言語の locale を差し込んだ Context。プルダウンで選択した直後に
    // 「はじめる」等の文言を新しい言語へ即時切り替えるために使用する。
    val localizedCtx = remember(activeTag) { LanguageSetting.apply(context, activeTag) }
    // この Welcome セッション中に言語が変更されたかどうか（はじめる で Activity 再生成の要否）
    var languageChanged by remember { mutableStateOf(false) }
    // プルダウンの開閉フラグ
    var langMenuExpanded by remember { mutableStateOf(false) }

    // 言語ラベルは各言語のネイティブ表記（翻訳しない）
    val languageOptions = listOf(
        "ja" to "日本語",
        "en" to "English",
        "zh-CN" to "简体中文",
        "ko" to "한국어",
    )

    val subtitle = if (isUpdate) {
        // %1$s にバージョンを埋め込む（選択中言語の locale で解決）
        String.format(localizedCtx.getString(R.string.dl1_welcomeUpdateSubtitle), info.currentVersion)
    } else {
        "Your idea, your future."
    }
    val buttonLabel = if (isUpdate) {
        "OK"
    } else {
        localizedCtx.getString(R.string.dl1_welcomeStartButton)
    }

    // 背景グラデーション（アプリ背景 → アクセント色の薄いヴェール）
    val bgBrush = Brush.verticalGradient(
        colors = listOf(
            colors.bg,
            colors.primaryAccent.copy(alpha = 0.18f),
        ),
    )
    // グリッド模様の線色
    val gridColor = colors.divider.copy(alpha = 0.35f)

    Dialog(
        onDismissRequest = { /* 外側タップ/バックでは閉じない */ },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
        ),
    ) {
        Box(modifier = Modifier.fillMaxSize().background(bgBrush)) {
            // 薄いカレンダーグリッド模様
            Canvas(modifier = Modifier.fillMaxSize()) {
                val step = 42.dp.toPx()
                var x = 0f
                while (x < size.width) {
                    drawLine(gridColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
                    x += step
                }
                var y = 0f
                while (y < size.height) {
                    drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
                    y += step
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
                    .padding(horizontal = 40.dp)
                    .alpha(contentAlpha)
                    .scale(contentScale),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                // アプリアイコン風の丸角タイル
                // ランチャーアイコンをそのまま表示する
                AsyncImage(
                    model = R.mipmap.ic_launcher,
                    contentDescription = null,
                    modifier = Modifier
                        .size(104.dp)
                        .clip(RoundedCornerShape(28.dp)),
                )

                Spacer(Modifier.height(28.dp))

                // アプリ名
                Text(
                    text = "Golendar",
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.text,
                    letterSpacing = 1.5.sp,
                )

                Spacer(Modifier.height(10.dp))

                // サブタイトル（mode で切替）
                Text(
                    text = subtitle,
                    fontSize = 14.sp,
                    color = colors.textGray,
                    fontStyle = if (isUpdate) FontStyle.Normal else FontStyle.Italic,
                    letterSpacing = 0.5.sp,
                    textAlign = TextAlign.Center,
                )

                Spacer(Modifier.height(56.dp))

                // 言語選択プルダウン。選択すると onLanguageSelected で保存を依頼し、
                // 文言が即座に選択言語へ切り替わる。
                Box {
                    // 開閉トリガー（🌐 + 選択中言語のネイティブ名 + ▾）
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(24.dp))
                            .background(colors.text.copy(alpha = 0.06f))
                            .clickable { langMenuExpanded = true }
                            .padding(horizontal = 16.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(text = "🌐", fontSize = 14.sp)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = languageOptions.firstOrNull { it.first == activeTag }?.second ?: activeTag,
                            fontSize = 14.sp,
                            color = colors.text,
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(text = "▾", fontSize = 12.sp, color = colors.textGray)
                    }
                    // プルダウンメニュー（4言語をネイティブ表記で提示）
                    // Material3 の既定背景はライト固定の白になるため、
                    // ダークモードで文字が読めなくなる。surface 色を明示する。
                    DropdownMenu(
                        expanded = langMenuExpanded,
                        onDismissRequest = { langMenuExpanded = false },
                        containerColor = colors.surface,
                    ) {
                        languageOptions.forEach { (tag, label) ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = label,
                                        fontSize = 14.sp,
                                        fontWeight = if (tag == activeTag) FontWeight.Bold else FontWeight.Normal,
                                    )
                                },
                                onClick = {
                                    langMenuExpanded = false
                                    if (tag != activeTag) {
                                        activeTag = tag
                                        languageChanged = true
                                        onLanguageSelected(tag)
                                    }
                                },
                            )
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))

                // メインボタン（この Welcome 中に言語を変更していれば、タップ後に
                // Activity を再生成してアプリ全体へ新しい言語を反映する）
                Button(
                    onClick = { onStart(languageChanged) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.primaryAccent,
                        contentColor = Color.White,
                    ),
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                ) {
                    Text(buttonLabel, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                }

                // バージョン表示
                Spacer(Modifier.height(24.dp))
                Text(
                    text = "v${info.currentVersion}",
                    fontSize = 11.sp,
                    color = colors.textGray.copy(alpha = 0.7f),
                    letterSpacing = 0.5.sp,
                )
            }
        }
    }
}