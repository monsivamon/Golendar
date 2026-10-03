package com.monsivamon.golender.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.monsivamon.golender.ui.theme.AppColors
import kotlinx.coroutines.launch

// AI 解析の初回説明を 3 ページで案内するダイアログを表示する
@Composable
fun AiSetupDialog(
    colors: AppColors,
    onComplete: () -> Unit,
) {
    // 3 ページのページャー状態とスクロール用スコープ
    val pagerState = rememberPagerState(pageCount = { 3 })
    val scope = rememberCoroutineScope()
    val isLastPage = pagerState.currentPage == 2
    AlertDialog(
        onDismissRequest = onComplete,
        containerColor = colors.surface,
        title = null,
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // ページごとに説明内容を切り替える
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxWidth().height(220.dp),
                ) { page ->
                    Column(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        when (page) {
                            // 1 ページ目: AI 解析とは何か
                            0 -> {
                                Text(
                                    "AIに読み取らせるとは",
                                    color = colors.text,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                )
                                Spacer(Modifier.height(16.dp))
                                Text(
                                    "カレンダーの画像やテキストをAIに渡して、\n予定を一括でカレンダーに登録する機能です。\n\n手書きの予定表やスクリーンショットも\n読み取ることができます。",
                                    color = colors.textGray,
                                    fontSize = 14.sp,
                                    textAlign = TextAlign.Center,
                                    lineHeight = 22.sp,
                                )
                            }
                            // 2 ページ目: 使い方の流れ
                            1 -> {
                                Text(
                                    "使い方の流れ",
                                    color = colors.text,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                )
                                Spacer(Modifier.height(16.dp))
                                Text(
                                    "① プロンプトをコピーする\n\n② AIアプリに画像と一緒に渡す\n\n③ 返ってきたJSONを貼り付ける",
                                    color = colors.textGray,
                                    fontSize = 14.sp,
                                    textAlign = TextAlign.Start,
                                    lineHeight = 22.sp,
                                )
                            }
                            // 3 ページ目: プレビューと登録
                            2 -> {
                                Text(
                                    "プレビューと登録",
                                    color = colors.text,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                )
                                Spacer(Modifier.height(16.dp))
                                Text(
                                    "解析結果はチェックボックスで選択できます。\nチェックした予定だけがカレンダーに登録されます。\n\n不要な予定はチェックを外して除外してください。",
                                    color = colors.textGray,
                                    fontSize = 14.sp,
                                    textAlign = TextAlign.Center,
                                    lineHeight = 22.sp,
                                )
                            }
                        }
                    }
                }
                // ページインジケータ
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    repeat(3) { i ->
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
                    text = if (isLastPage) "始める" else "次へ",
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