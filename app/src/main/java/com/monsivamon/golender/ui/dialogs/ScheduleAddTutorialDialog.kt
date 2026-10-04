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
fun ScheduleAddTutorialDialog(colors: AppColors, onComplete: () -> Unit) {
    SetupTutorialScaffold(
        pages = listOf(
            TutorialPage(
                title = "FAB から追加",
                description = "月表示の右下にある「+」ボタンをタップすると、\n日付を選んで予定を追加できます。",
                illustration = { c -> FabIllustration(c) },
            ),
            TutorialPage(
                title = "各日から追加",
                description = "週表示では、各日付の下の「+ 予定を追加」\nボタンからその日の予定を作成できます。",
                illustration = { c -> WeekAddIllustration(c) },
            ),
            TutorialPage(
                title = "ショートカット",
                description = "ホーム画面のアイコンを長押しすると、\n「予定追加」を直接起動できます。",
                illustration = { c -> ShortcutIllustration(c) },
            ),
        ),
        colors = colors,
        onComplete = onComplete,
        dismissLabel = "閉じる",
    )
}

@Composable
private fun FabIllustration(colors: AppColors) {
    TutorialIllustrationFrame(colors) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(colors.primaryAccent),
                contentAlignment = Alignment.Center,
            ) {
                Text("+", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(10.dp))
            Text("右下の + ボタン", color = colors.textGray, fontSize = 12.sp)
        }
    }
}

@Composable
private fun WeekAddIllustration(colors: AppColors) {
    TutorialIllustrationFrame(colors) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            repeat(2) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .width(50.dp)
                            .height(18.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(colors.surface),
                    )
                    Box(
                        modifier = Modifier
                            .width(90.dp)
                            .height(24.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.primaryAccent.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("+ 予定を追加", color = colors.primaryAccent, fontSize = 10.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}

@Composable
private fun ShortcutIllustration(colors: AppColors) {
    TutorialIllustrationFrame(colors) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // アプリアイコン風
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(colors.primaryAccent.copy(alpha = 0.2f))
                    .border(2.dp, colors.primaryAccent, RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text("📅", fontSize = 26.sp)
            }
            Spacer(Modifier.height(8.dp))
            Text("長押し", color = colors.primaryAccent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}