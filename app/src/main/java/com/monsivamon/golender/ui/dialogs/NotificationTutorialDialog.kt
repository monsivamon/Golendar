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
fun NotificationTutorialDialog(colors: AppColors, onComplete: () -> Unit) {
    SetupTutorialScaffold(
        pages = listOf(
            TutorialPage(
                title = "通知のタイミング",
                description = "「定刻」と「10分前」の2種類を\n個別に ON/OFF できます。\n祝日・文化イベントは通知対象外です。",
                illustration = { c -> TimingIllustration(c) },
            ),
            TutorialPage(
                title = "正確なアラーム",
                description = "予定時刻ぴったりに通知を届けるため、\n「正確なアラーム」の許可を推奨します。",
                illustration = { c -> ExactAlarmIllustration(c) },
            ),
            TutorialPage(
                title = "バッテリー最適化",
                description = "スリープ中の通知遅延を防ぐため、\nバッテリー最適化の無効化を推奨します。",
                illustration = { c -> BatteryTutIllustration(c) },
            ),
        ),
        colors = colors,
        onComplete = onComplete,
        dismissLabel = "閉じる",
    )
}

@Composable
private fun TimingIllustration(colors: AppColors) {
    TutorialIllustrationFrame(colors) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("🔔", fontSize = 36.sp)
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("10分前", colors)
                Pill("定刻", colors)
            }
        }
    }
}

@Composable
private fun Pill(label: String, colors: AppColors) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(colors.primaryAccent.copy(alpha = 0.15f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(label, color = colors.primaryAccent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ExactAlarmIllustration(colors: AppColors) {
    TutorialIllustrationFrame(colors) {
        Box(contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(colors.surface)
                    .border(3.dp, colors.primaryAccent, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text("10:00", color = colors.primaryAccent, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
            Box(
                modifier = Modifier
                    .padding(bottom = 90.dp)
                    .width(28.dp)
                    .height(10.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(colors.primaryAccent),
            )
        }
    }
}

@Composable
private fun BatteryTutIllustration(colors: AppColors) {
    TutorialIllustrationFrame(colors) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .width(120.dp)
                    .height(56.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.surface)
                    .border(3.dp, colors.primaryAccent, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text("⚡", fontSize = 24.sp, color = Color(0xFFFFB300))
            }
            Box(
                modifier = Modifier
                    .padding(start = 4.dp)
                    .width(8.dp)
                    .height(22.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(colors.primaryAccent),
            )
        }
    }
}