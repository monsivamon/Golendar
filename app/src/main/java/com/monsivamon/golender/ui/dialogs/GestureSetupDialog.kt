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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.monsivamon.golender.ui.theme.AppColors
import kotlinx.coroutines.launch

// ジェスチャー案内の1ページ分を表すデータクラス。
private data class GesturePage(
    val title: String,
    val description: String,
    val overlayName: String,
    val baseIllustration: @Composable (AppColors) -> Unit,
)

// 初回起動時にジェスチャー操作を3ページで案内するダイアログを表示する。
@Composable
fun GestureSetupDialog(
    colors: AppColors,
    onComplete: () -> Unit,
) {
    val pagerState = rememberPagerState(pageCount = { 3 })
    val scope = rememberCoroutineScope()

    val pages = listOf(
        GesturePage(
            title = "上下スワイプで前後の期間へ",
            description = "月・週・日どの表示でも、同じ方向に素早く2回スワイプすると前後の期間に移動します。",
            overlayName = "gesture_overlay_vertical",
            baseIllustration = { c -> VerticalSwipeBase(c) },
        ),
        GesturePage(
            title = "左右スワイプで表示切替",
            description = "左右にスワイプすると、日・週・月の表示を切り替えられます。",
            overlayName = "gesture_overlay_horizontal",
            baseIllustration = { c -> HorizontalSwipeBase(c) },
        ),
        GesturePage(
            title = "準備完了",
            description = "さっそく予定を追加してみましょう。\nホーム画面のアイコン長押しでショートカットも使えます。",
            overlayName = "gesture_overlay_ready",
            baseIllustration = { c -> ReadyBase(c) },
        ),
    )

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
                    text = if (isLastPage) "始める" else "次へ",
                    color = colors.primaryAccent,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                )
            }
        },
        dismissButton = {
            if (!isLastPage) {
                TextButton(onClick = onComplete) {
                    Text("スキップ", color = colors.textGray)
                }
            } else {
                TextButton(onClick = {
                    scope.launch {
                        pagerState.animateScrollToPage(pagerState.currentPage - 1)
                    }
                }) {
                    Text("戻る", color = colors.textGray)
                }
            }
        },
    )
}

// ベースイラストの上に透過PNGオーバーレイを重ねて描画する。
@Composable
private fun IllustrationWithOverlay(
    colors: AppColors,
    overlayName: String,
    base: @Composable (AppColors) -> Unit,
) {
    val context = LocalContext.current
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

// リソース存在チェック（デバッグ用）。
@Suppress("unused")
private fun Context.hasDrawable(name: String): Boolean =
    resources.getIdentifier(name, "drawable", packageName) != 0

// 上下スワイプのベースイラストを描画する。
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
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(colors.primaryAccent),
            contentAlignment = Alignment.Center,
        ) {
            Text("×2", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }

        Icon(
            imageVector = Icons.Default.KeyboardArrowUp,
            contentDescription = null,
            tint = colors.primaryAccent,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 12.dp)
                .size(48.dp),
        )

        Icon(
            imageVector = Icons.Default.KeyboardArrowDown,
            contentDescription = null,
            tint = colors.primaryAccent,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 12.dp)
                .size(48.dp),
        )

        Text(
            "前へ",
            color = colors.textGray,
            fontSize = 12.sp,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 16.dp),
        )
        Text(
            "次へ",
            color = colors.textGray,
            fontSize = 12.sp,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 16.dp),
        )
    }
}

// 左右スワイプのベースイラストを描画する。
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
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TabPill("日", colors)
            TabPill("週", colors)
            TabPill("月", colors, selected = true)
        }

        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
            contentDescription = null,
            tint = colors.primaryAccent,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 8.dp)
                .size(40.dp),
        )

        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = colors.primaryAccent,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 8.dp)
                .size(40.dp),
        )

        Text(
            "左右にスワイプ",
            color = colors.textGray,
            fontSize = 12.sp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 12.dp),
        )
    }
}

// 準備完了ページのベースイラストを描画する。
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
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            tint = colors.primaryAccent,
            modifier = Modifier.size(96.dp),
        )
    }
}

// タブ風の小さなピルを描画する。
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