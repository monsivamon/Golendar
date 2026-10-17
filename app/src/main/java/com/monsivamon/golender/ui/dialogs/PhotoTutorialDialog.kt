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