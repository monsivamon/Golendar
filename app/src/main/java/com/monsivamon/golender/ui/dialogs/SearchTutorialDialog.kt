package com.monsivamon.golender.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.monsivamon.golender.ui.common.SetupTutorialScaffold
import com.monsivamon.golender.ui.common.TutorialIllustrationFrame
import com.monsivamon.golender.ui.common.TutorialPage
import com.monsivamon.golender.ui.theme.AppColors

@Composable
fun SearchTutorialDialog(colors: AppColors, onComplete: () -> Unit) {
    SetupTutorialScaffold(
        pages = listOf(
            TutorialPage(
                title = "予定を検索",
                description = "ヘッダーの検索アイコンから、\n予定のタイトルを検索できます。",
                illustration = { c -> SearchBoxIllustration(c) },
            ),
            TutorialPage(
                title = "範囲と除外",
                description = "過去2年〜未来2年が対象です。\n祝日・文化・誕生日は除外されます。\n結果をタップするとその日付へ移動します。",
                illustration = { c -> SearchRangeIllustration(c) },
            ),
        ),
        colors = colors,
        onComplete = onComplete,
        dismissLabel = "閉じる",
    )
}

@Composable
private fun SearchBoxIllustration(colors: AppColors) {
    TutorialIllustrationFrame(colors) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // 検索ボックス風
            Box(
                modifier = Modifier
                    .width(160.dp)
                    .height(34.dp)
                    .clip(RoundedCornerShape(17.dp))
                    .background(colors.surface)
                    .border(1.dp, colors.divider, RoundedCornerShape(17.dp))
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🔍", fontSize = 14.sp)
                    Spacer(Modifier.width(6.dp))
                    Text("予定を検索...", color = colors.textGray, fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(10.dp))
            // 結果リスト風
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(2) {
                    Box(
                        modifier = Modifier
                            .width(160.dp)
                            .height(14.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(colors.primaryAccent.copy(alpha = 0.2f)),
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchRangeIllustration(colors: AppColors) {
    TutorialIllustrationFrame(colors) {
        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // 中央に今日、両側に矢印
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("←", color = colors.textGray, fontSize = 16.sp)
                Box(
                    modifier = Modifier
                        .width(60.dp)
                        .height(20.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.primaryAccent.copy(alpha = 0.25f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("2年前", color = colors.primaryAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .width(40.dp)
                        .height(20.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.primaryAccent),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("今日", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .width(60.dp)
                        .height(20.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.primaryAccent.copy(alpha = 0.25f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("2年後", color = colors.primaryAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                Text("→", color = colors.textGray, fontSize = 16.sp)
            }
            Text("祝日・文化・誕生日は除外", color = colors.textGray, fontSize = 11.sp)
        }
    }
}