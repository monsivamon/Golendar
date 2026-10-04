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
fun RecurringTutorialDialog(colors: AppColors, onComplete: () -> Unit) {
    SetupTutorialScaffold(
        pages = listOf(
            TutorialPage(
                title = "繰り返しの種類",
                description = "毎日 / 平日 / 毎週 / 毎月 / 毎年 から選べます。\n「平日」は月〜金のみ繰り返します。",
                illustration = { c -> RecurringChipsIllustration(c) },
            ),
            TutorialPage(
                title = "「終了」と「繰り返し終了日」",
                description = "「終了」は1回分の所要時間、\n「繰り返しの終了日」は繰り返し全体の最終日です。",
                illustration = { c -> DurationVsUntilIllustration(c) },
            ),
            TutorialPage(
                title = "繰り返しの削除",
                description = "繰り返し予定は「この日だけ削除」できません。\n1件だけ変更したい場合は、繰り返しを解除してから\n個別の予定に分けてください。",
                illustration = { c -> NoSplitIllustration(c) },
            ),
        ),
        colors = colors,
        onComplete = onComplete,
        dismissLabel = "閉じる",
    )
}

@Composable
private fun RecurringChipsIllustration(colors: AppColors) {
    TutorialIllustrationFrame(colors) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Chip("毎日", colors, true)
                Chip("平日", colors, false)
                Chip("毎週", colors, false)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Chip("毎月", colors, false)
                Chip("毎年", colors, false)
            }
        }
    }
}

@Composable
private fun Chip(label: String, colors: AppColors, selected: Boolean) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) colors.primaryAccent else colors.surface)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            label,
            color = if (selected) Color.White else colors.text,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

@Composable
private fun DurationVsUntilIllustration(colors: AppColors) {
    TutorialIllustrationFrame(colors) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // 上段: 1回分の所要時間
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("開始", color = colors.textGray, fontSize = 11.sp, modifier = Modifier.width(40.dp))
                Box(
                    modifier = Modifier
                        .width(80.dp)
                        .height(14.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(colors.primaryAccent.copy(alpha = 0.5f)),
                )
                Spacer(Modifier.width(6.dp))
                Text("←「終了」", color = colors.primaryAccent, fontSize = 11.sp, fontWeight = FontWeight.Medium)
            }
            // 下段: 繰り返し全体の期間
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("初回", color = colors.textGray, fontSize = 11.sp, modifier = Modifier.width(40.dp))
                Box(
                    modifier = Modifier
                        .width(120.dp)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(colors.primaryAccent.copy(alpha = 0.3f)),
                )
                Spacer(Modifier.width(6.dp))
                Text("←終了日", color = colors.textGray, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun NoSplitIllustration(colors: AppColors) {
    TutorialIllustrationFrame(colors) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .width(140.dp)
                    .height(20.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(colors.primaryAccent.copy(alpha = 0.35f)),
                contentAlignment = Alignment.Center,
            ) {
                Text("繰り返し予定", color = colors.text, fontSize = 11.sp)
            }
            Text("× この日だけ削除できません", color = colors.sunRed, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}