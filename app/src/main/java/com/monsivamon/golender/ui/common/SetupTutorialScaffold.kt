package com.monsivamon.golender.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.monsivamon.golender.ui.theme.AppColors
import kotlinx.coroutines.launch

// セットアップチュートリアル 1 ページ分の定義
data class TutorialPage(
    val title: String,
    val description: String,
    val illustration: @Composable (AppColors) -> Unit,
)

// ジェスチャー画面と同じ「角丸20dp・2dpボーダー・bg背景」のイラスト枠
@Composable
fun TutorialIllustrationFrame(
    colors: AppColors,
    width: Dp = 240.dp,
    height: Dp = 180.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = Modifier
            .width(width)
            .height(height)
            .clip(RoundedCornerShape(20.dp))
            .background(colors.bg)
            .border(2.dp, colors.divider, RoundedCornerShape(20.dp)),
        contentAlignment = Alignment.Center,
        content = content,
    )
}

// 全チュートリアル共通のダイアログ土台。
// GestureSetupDialog と同じ「イラスト枠 + タイトル + 説明 + ドット + 3ボタン」の見た目を提供する。
@Composable
fun SetupTutorialScaffold(
    pages: List<TutorialPage>,
    colors: AppColors,
    onComplete: () -> Unit,
    // ページごとの confirm 動作を上書き。null なら「次へ / 最終ページで onComplete」
    onConfirmOverride: ((pageIndex: Int, advance: () -> Unit) -> Unit)? = null,
    // ページごとの confirm ラベルを返す。null なら confirmLabel / confirmLabelLast を使用
    confirmLabelProvider: ((pageIndex: Int, isLast: Boolean) -> String)? = null,
    pageHeight: Dp = 340.dp,
    illustrationWidth: Dp = 240.dp,
    illustrationHeight: Dp = 180.dp,
    swipeEnabled: Boolean = true,
    confirmLabel: String = "次へ",
    confirmLabelLast: String = "始める",
    dismissLabel: String = "スキップ",
    backLabel: String = "戻る",
) {
    if (pages.isEmpty()) return
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()
    val isLastPage = pagerState.currentPage == pages.lastIndex
    val isMultiPage = pages.size > 1

    // 次ページへ進む（最終ページなら完了）
    fun advance() {
        scope.launch {
            if (isLastPage) onComplete()
            else pagerState.animateScrollToPage(pagerState.currentPage + 1)
        }
    }

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
                    userScrollEnabled = swipeEnabled,
                    modifier = Modifier.fillMaxWidth().height(pageHeight),
                ) { page ->
                    val p = pages[page]
                    Column(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Box(
                            modifier = Modifier.width(illustrationWidth).height(illustrationHeight),
                            contentAlignment = Alignment.Center,
                        ) {
                            p.illustration(colors)
                        }
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
                if (isMultiPage) {
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
            }
        },
        confirmButton = {
            val currentIndex = pagerState.currentPage
            val defaultLabel = if (isLastPage || !isMultiPage) confirmLabelLast else confirmLabel
            val label = confirmLabelProvider?.invoke(currentIndex, isLastPage) ?: defaultLabel
            TextButton(onClick = {
                if (onConfirmOverride != null) {
                    onConfirmOverride(currentIndex, ::advance)
                } else {
                    advance()
                }
            }) {
                Text(
                    text = label,
                    color = colors.primaryAccent,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                )
            }
        },
        dismissButton = {
            if (isMultiPage && isLastPage) {
                TextButton(onClick = {
                    scope.launch {
                        pagerState.animateScrollToPage(pagerState.currentPage - 1)
                    }
                }) {
                    Text(backLabel, color = colors.textGray)
                }
            } else {
                TextButton(onClick = onComplete) {
                    Text(dismissLabel, color = colors.textGray)
                }
            }
        },
    )
}