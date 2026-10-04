package com.monsivamon.golender.ui.dialogs

import coil.compose.AsyncImage
import androidx.compose.ui.res.painterResource
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.monsivamon.golender.R
import com.monsivamon.golender.ui.theme.AppColors
import com.monsivamon.golender.viewmodel.WelcomeInfo
import com.monsivamon.golender.viewmodel.WelcomeMode
import kotlinx.coroutines.delay

// Welcome 画面。初回起動時と、バージョンアップ直後の両方で表示する。
@Composable
fun WelcomeDialog(
    colors: AppColors,
    info: WelcomeInfo,
    onStart: () -> Unit,
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
    val subtitle = if (isUpdate) {
        "v${info.currentVersion} へアップデートしました"
    } else {
        "Your idea, your future."
    }
    val buttonLabel = if (isUpdate) "OK" else "はじめる"

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

                // メインボタン
                Button(
                    onClick = onStart,
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