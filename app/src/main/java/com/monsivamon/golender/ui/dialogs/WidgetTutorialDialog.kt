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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.monsivamon.golender.ui.common.SetupTutorialScaffold
import com.monsivamon.golender.ui.common.TutorialIllustrationFrame
import com.monsivamon.golender.ui.common.TutorialPage
import com.monsivamon.golender.ui.theme.AppColors

@Composable
fun WidgetTutorialDialog(colors: AppColors, onComplete: () -> Unit) {
    SetupTutorialScaffold(
        pages = listOf(
            TutorialPage(
                title = "3種類のウィジェット",
                description = "日間・週間・月間の3種類を、\nホーム画面に自由に配置できます。\nテーマや背景色と連動します。",
                illustration = { c -> WidgetsIllustration(c) },
            ),
            TutorialPage(
                title = "アプリショートカット",
                description = "ホーム画面のアイコンを長押しすると、\n「予定追加」「今日」「検索」を\n直接起動できます。",
                illustration = { c -> ShortcutWidgetIllustration(c) },
            ),
        ),
        colors = colors,
        onComplete = onComplete,
        dismissLabel = "閉じる",
    )
}

@Composable
private fun WidgetsIllustration(colors: AppColors) {
    TutorialIllustrationFrame(colors) {
        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            WidgetMini("日", colors, 140, 20)
            WidgetMini("週", colors, 140, 24)
            WidgetMini("月", colors, 140, 30)
        }
    }
}

@Composable
private fun WidgetMini(label: String, colors: AppColors, width: Int, height: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .width(28.dp)
                .height(18.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(colors.primaryAccent.copy(alpha = 0.25f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(label, color = colors.primaryAccent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(6.dp))
        Box(
            modifier = Modifier
                .width(width.dp)
                .height(height.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(colors.surface)
                .border(1.dp, colors.divider, RoundedCornerShape(4.dp)),
        )
    }
}

@Composable
private fun ShortcutWidgetIllustration(colors: AppColors) {
    TutorialIllustrationFrame(colors) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
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
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MiniPill("予定追加", colors)
                MiniPill("今日", colors)
                MiniPill("検索", colors)
            }
        }
    }
}

@Composable
private fun MiniPill(label: String, colors: AppColors) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(colors.surface)
            .border(1.dp, colors.divider, RoundedCornerShape(8.dp))
            .padding(horizontal = 6.dp, vertical = 3.dp),
    ) {
        Text(label, color = colors.textGray, fontSize = 9.sp)
    }
}