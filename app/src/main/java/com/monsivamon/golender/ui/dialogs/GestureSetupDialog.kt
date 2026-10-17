package com.monsivamon.golender.ui.dialogs

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.monsivamon.golender.R
import com.monsivamon.golender.ui.theme.AppColors
import kotlinx.coroutines.launch

// ジェスチャー案内の 1 ページ分を表すデータクラス
private data class GesturePage(
    val title: String,
    val description: String,
    val overlayName: String,
    val baseIllustration: @Composable (AppColors) -> Unit,
)

// 初回起動時にジェスチャー操作を 3 ページで案内するダイアログを表示する
@Composable
fun GestureSetupDialog(
    colors: AppColors,
    onComplete: () -> Unit,
) {
    // ページャー状態とスクロール用スコープ
    val pagerState = rememberPagerState(pageCount = { 3 })
    val scope = rememberCoroutineScope()

    // 3 ページ分の内容を定義する
    val pages = listOf(
        GesturePage(
            title = stringResource(R.string.dl1_gestureTitleVertical),
            description = stringResource(R.string.dl1_gestureDescVertical),
            overlayName = "gesture_overlay_vertical",
            baseIllustration = { c -> VerticalSwipeBase(c) },
        ),
        GesturePage(
            title = stringResource(R.string.dl1_gestureTitleHorizontal),
            description = stringResource(R.string.dl1_gestureDescHorizontal),
            overlayName = "gesture_overlay_horizontal",
            baseIllustration = { c -> HorizontalSwipeBase(c) },
        ),
        GesturePage(
            title = stringResource(R.string.dl1_gestureTitleReady),
            description = stringResource(R.string.dl1_gestureDescReady),
            overlayName = "gesture_overlay_ready",
            baseIllustration = { c -> ReadyBase(c) },
        ),
    )

    // 最終ページかどうかを判定する
    val isLastPage = pagerState.currentPage == pages.lastIndex

    AlertDialog(
        onDismissRequest = onComplete,
        containerColor = colors.surface,
        title = null,
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // ページごとにイラスト・タイトル・説明を切り替える
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxWidth().height(340.dp),
                ) { page ->
                    val p = pages[page]
                    Column(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        // ベースイラスト＋オーバーレイ画像を重ねて表示する
                        IllustrationWithOverlay(
                            colors = colors,
                            overlayName = p.overlayName,
                            base = p.baseIllustration,
                        )

                        Spacer(Modifier.height(24.dp))

                        Text(
                            text = p.title,
                            color = colors.text,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                        )

                        Spacer(Modifier.height(12.dp))

                        Text(
                            text = p.description,
                            color = colors.textGray,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center,
                            lineHeight = 20.sp,
                        )
                    }
                }

                // ページインジケータ
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    repeat(pages.size) { i ->
                        val isActive = i == pagerState.currentPage
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 4.dp)
                                .size(if (isActive) 10.dp else 8.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isActive) colors.primaryAccent
                                    else colors.textGray.copy(alpha = 0.35f)
                                ),
                        )
                    }
                }
            }
        },
        confirmButton = {
            // 次へ／始めるボタン
            TextButton(onClick = {
                if (!isLastPage) {
                    scope.launch {
                        pagerState.animateScrollToPage(pagerState.currentPage + 1)
                    }
                } else {
                    onComplete()
                }
            }) {
                Text(
                    text = if (isLastPage) stringResource(R.string.dl1_start) else stringResource(R.string.dl1_next),
                    color = colors.primaryAccent,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                )
            }
        },
        dismissButton = {
            // スキップ／戻るボタン
            if (!isLastPage) {
                TextButton(onClick = onComplete) {
                    Text(stringResource(R.string.dl1_skip), color = colors.textGray)
                }
            } else {
                TextButton(onClick = {
                    scope.launch {
                        pagerState.animateScrollToPage(pagerState.currentPage - 1)
                    }
                }) {
                    Text(stringResource(R.string.dl1_back), color = colors.textGray)
                }
            }
        },
    )
}

// ベースイラストの上に透過 PNG オーバーレイを重ねて描画する
@Composable
private fun IllustrationWithOverlay(
    colors: AppColors,
    overlayName: String,
    base: @Composable (AppColors) -> Unit,
) {
    val context = LocalContext.current
    // 文字列名から drawable リソース ID を解決する
    val overlayResId = remember(overlayName) {
        context.resources.getIdentifier(overlayName, "drawable", context.packageName)
    }

    Box(
        modifier = Modifier
            .width(240.dp)
            .height(180.dp),
        contentAlignment = Alignment.Center,
    ) {
        base(colors)

        // リソースが見つかった場合のみオーバーレイを描画する
        if (overlayResId != 0) {
            Image(
                painter = painterResource(id = overlayResId),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

// リソース存在チェック（デバッグ用）
@Suppress("unused")
private fun Context.hasDrawable(name: String): Boolean =
    resources.getIdentifier(name, "drawable", packageName) != 0

// 上下スワイプのベースイラストを描画する
@Composable
private fun VerticalSwipeBase(colors: AppColors) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(20.dp))
            .background(colors.bg)
            .border(2.dp, colors.divider, RoundedCornerShape(20.dp)),
        contentAlignment = Alignment.Center,
    ) {
        // 中央の「×2」バッジ
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(colors.primaryAccent),
            contentAlignment = Alignment.Center,
        ) {
            Text("×2", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }

        // 上方向の矢印
        Icon(
            imageVector = Icons.Default.KeyboardArrowUp,
            contentDescription = null,
            tint = colors.primaryAccent,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 12.dp)
                .size(48.dp),
        )

        // 下方向の矢印
        Icon(
            imageVector = Icons.Default.KeyboardArrowDown,
            contentDescription = null,
            tint = colors.primaryAccent,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 12.dp)
                .size(48.dp),
        )

        // 左右の「前へ／次へ」ラベル
        Text(
            stringResource(R.string.dl1_prev),
            color = colors.textGray,
            fontSize = 12.sp,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 16.dp),
        )
        Text(
            stringResource(R.string.dl1_next),
            color = colors.textGray,
            fontSize = 12.sp,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 16.dp),
        )
    }
}

// 左右スワイプのベースイラストを描画する
@Composable
private fun HorizontalSwipeBase(colors: AppColors) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(20.dp))
            .background(colors.bg)
            .border(2.dp, colors.divider, RoundedCornerShape(20.dp)),
        contentAlignment = Alignment.Center,
    ) {
        // 日・週・月のタブ風ピル
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TabPill(stringResource(R.string.dl1_tabDay), colors)
            TabPill(stringResource(R.string.dl1_tabWeek), colors)
            TabPill(stringResource(R.string.dl1_tabMonth), colors, selected = true)
        }

        // 左方向の矢印
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
            contentDescription = null,
            tint = colors.primaryAccent,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 8.dp)
                .size(40.dp),
        )

        // 右方向の矢印
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = colors.primaryAccent,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 8.dp)
                .size(40.dp),
        )

        // 下部の説明テキスト
        Text(
            stringResource(R.string.dl1_gestureSwipeHint),
            color = colors.textGray,
            fontSize = 12.sp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 12.dp),
        )
    }
}

// 準備完了ページのベースイラストを描画する
@Composable
private fun ReadyBase(colors: AppColors) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(20.dp))
            .background(colors.bg)
            .border(2.dp, colors.divider, RoundedCornerShape(20.dp)),
        contentAlignment = Alignment.Center,
    ) {
        // 中央に大きなチェックアイコン
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            tint = colors.primaryAccent,
            modifier = Modifier.size(96.dp),
        )
    }
}

// タブ風の小さなピルを描画する
@Composable
private fun TabPill(label: String, colors: AppColors, selected: Boolean = false) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) colors.primaryAccent else colors.surface)
            .border(
                width = 1.dp,
                color = if (selected) colors.primaryAccent else colors.divider,
                shape = RoundedCornerShape(16.dp),
            )
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            text = label,
            color = if (selected) Color.White else colors.text,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        )
    }
}