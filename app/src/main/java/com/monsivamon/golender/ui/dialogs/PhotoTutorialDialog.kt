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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.monsivamon.golender.R
import com.monsivamon.golender.ui.common.SetupTutorialScaffold
import com.monsivamon.golender.ui.common.TutorialIllustrationFrame
import com.monsivamon.golender.ui.common.TutorialPage
import com.monsivamon.golender.ui.theme.AppColors

@Composable
// 写真の添付・拡大・保存・共有の使い方を案内するチュートリアル
fun PhotoTutorialDialog(colors: AppColors, onComplete: () -> Unit) {
    SetupTutorialScaffold(
        pages = listOf(
            TutorialPage(
                title = stringResource(R.string.dl1_photoTutTitleAttach),
                description = stringResource(R.string.dl1_photoTutDescAttach),
                illustration = { c -> AttachIllustration(c) },
            ),
            TutorialPage(
                title = stringResource(R.string.dl1_photoTutTitleViewer),
                description = stringResource(R.string.dl1_photoTutDescViewer),
                illustration = { c -> ViewerIllustration(c) },
            ),
            TutorialPage(
                title = stringResource(R.string.dl1_photoTutTitleAlbum),
                description = stringResource(R.string.dl1_photoTutDescAlbum),
                illustration = { c -> AlbumIllustration(c) },
            ),
        ),
        colors = colors,
        onComplete = onComplete,
        dismissLabel = stringResource(R.string.dl1_close),
    )
}

@Composable
private fun AttachIllustration(colors: AppColors) {
    TutorialIllustrationFrame(colors) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            // 写真枠 2 枚
            repeat(2) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(colors.primaryAccent.copy(alpha = 0.25f)),
                )
            }
            // + ボタン
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(colors.surface)
                    .border(1.dp, colors.primaryAccent, RoundedCornerShape(6.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text("+", color = colors.primaryAccent, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ViewerIllustration(colors: AppColors) {
    TutorialIllustrationFrame(colors) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // 全画面ビューア風の枠
            Box(
                modifier = Modifier
                    .width(140.dp)
                    .height(90.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors.primaryAccent.copy(alpha = 0.15f))
                    .border(1.dp, colors.divider, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(R.string.dl1_photoTutLabelPhoto), color = colors.textGray, fontSize = 12.sp)
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(colors.primaryAccent)
                        .padding(horizontal = 14.dp, vertical = 5.dp),
                ) {
                    Text(stringResource(R.string.dl1_save), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(colors.primaryAccent)
                        .padding(horizontal = 14.dp, vertical = 5.dp),
                ) {
                    Text(stringResource(R.string.dl1_share), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// アルバムタブと日付ごとの写真一覧・「その日へ」ボタンのイメージ
@Composable
private fun AlbumIllustration(colors: AppColors) {
    TutorialIllustrationFrame(colors) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // 日・週・月・アルバムのタブ（アルバムを選択中として強調）
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                listOf(
                    stringResource(R.string.co_tab_daily) to false,
                    stringResource(R.string.co_tab_weekly) to false,
                    stringResource(R.string.co_tab_monthly) to false,
                    stringResource(R.string.al_albumTitle) to true,
                ).forEach { (label, selected) ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (selected) colors.primaryAccent else colors.surface)
                            .border(
                                1.dp,
                                if (selected) colors.primaryAccent else colors.divider,
                                RoundedCornerShape(10.dp),
                            )
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    ) {
                        Text(
                            label,
                            color = if (selected) Color.White else colors.text,
                            fontSize = 9.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            // 同じ日の写真を横並びにしたイメージ
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(3) { index ->
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(colors.primaryAccent.copy(alpha = 0.2f + index * 0.1f)),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            // ビューア内の「その日へ」ボタン
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.primaryAccent)
                    .padding(horizontal = 12.dp, vertical = 3.dp),
            ) {
                Text(
                    stringResource(R.string.al_goToDate),
                    color = Color.White,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}