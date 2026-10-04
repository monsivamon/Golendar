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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.monsivamon.golender.ui.common.SetupTutorialScaffold
import com.monsivamon.golender.ui.common.TutorialIllustrationFrame
import com.monsivamon.golender.ui.common.TutorialPage
import com.monsivamon.golender.ui.theme.AppColors

@Composable
fun ScheduleEditTutorialDialog(colors: AppColors, onComplete: () -> Unit) {
    SetupTutorialScaffold(
        pages = listOf(
            TutorialPage(
                title = "予定を編集",
                description = "予定カードをタップすると詳細が開き、\n「編集」ボタンから内容を変更できます。",
                illustration = { c -> EditIllustration(c) },
            ),
            TutorialPage(
                title = "予定を削除",
                description = "編集画面の「削除」ボタンで、\n予定を完全に削除します。",
                illustration = { c -> DeleteIllustration(c) },
            ),
            TutorialPage(
                title = "この日だけ削除",
                description = "複数日にまたがる予定では、\n「この日だけ削除」で他の日を残せます。\n※予定は2つに分割されます。",
                illustration = { c -> SplitDeleteIllustration(c) },
            ),
        ),
        colors = colors,
        onComplete = onComplete,
        dismissLabel = "閉じる",
    )
}

@Composable
private fun EditIllustration(colors: AppColors) {
    TutorialIllustrationFrame(colors) {
        Column(
            modifier = Modifier
                .width(180.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(colors.surface)
                .border(1.dp, colors.divider, RoundedCornerShape(10.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Text("会議", color = colors.text, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .width(60.dp)
                        .height(22.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(colors.primaryAccent.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("編集", color = colors.primaryAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun DeleteIllustration(colors: AppColors) {
    TutorialIllustrationFrame(colors) {
        Box(
            modifier = Modifier
                .width(120.dp)
                .height(40.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(colors.sunRed.copy(alpha = 0.15f))
                .border(1.dp, colors.sunRed, RoundedCornerShape(20.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text("削除", color = colors.sunRed, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SplitDeleteIllustration(colors: AppColors) {
    TutorialIllustrationFrame(colors) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Before: 1本の長いバー
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("前", color = colors.textGray, fontSize = 11.sp)
                Box(
                    modifier = Modifier
                        .width(140.dp)
                        .height(14.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(colors.primaryAccent.copy(alpha = 0.5f)),
                )
            }
            Text("↓", color = colors.textGray, fontSize = 14.sp)
            // After: 2本の短いバー（間が空く）
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("後", color = colors.textGray, fontSize = 11.sp)
                Box(
                    modifier = Modifier
                        .width(60.dp)
                        .height(14.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(colors.primaryAccent.copy(alpha = 0.5f)),
                )
                Box(
                    modifier = Modifier
                        .width(20.dp)
                        .height(14.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(colors.bg)
                        .border(1.dp, colors.divider, RoundedCornerShape(4.dp)),
                )
                Box(
                    modifier = Modifier
                        .width(60.dp)
                        .height(14.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(colors.primaryAccent.copy(alpha = 0.5f)),
                )
            }
        }
    }
}