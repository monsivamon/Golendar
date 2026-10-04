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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Icon
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

// AI 解析の初回説明を 3 ページで案内する（ジェスチャー画面と同じ土台）
@Composable
fun AiSetupDialog(
    colors: AppColors,
    onComplete: () -> Unit,
) {
    SetupTutorialScaffold(
        pages = listOf(
            TutorialPage(
                title = "AIに読み取らせるとは",
                description = "カレンダーの画像やテキストをAIに渡して、\n予定を一括でカレンダーに登録する機能です。",
                illustration = { colors -> AiIntroIllustration(colors) },
            ),
            TutorialPage(
                title = "使い方の流れ",
                description = "① プロンプトをコピー\n② AIアプリに画像と一緒に渡す\n③ 返ってきたJSONを貼り付ける",
                illustration = { colors -> AiFlowIllustration(colors) },
            ),
            TutorialPage(
                title = "プレビューと登録",
                description = "解析結果はチェックボックスで選択できます。\nチェックした予定だけが登録されます。",
                illustration = { colors -> AiPreviewIllustration(colors) },
            ),
        ),
        colors = colors,
        onComplete = onComplete,
    )
}

// 1ページ目: 📄 → 🤖 → 📋 のフロー
@Composable
private fun AiIntroIllustration(colors: AppColors) {
    TutorialIllustrationFrame(colors) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            EmojiTile("📄", colors)
            ArrowIcon(colors)
            EmojiTile("🤖", colors)
            ArrowIcon(colors)
            EmojiTile("📋", colors)
        }
    }
}

// 2ページ目: 番号付きステップ
@Composable
private fun AiFlowIllustration(colors: AppColors) {
    TutorialIllustrationFrame(colors) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.Start,
        ) {
            StepRow("1", "プロンプトをコピー", colors)
            StepRow("2", "AIアプリに渡す", colors)
            StepRow("3", "JSONを貼り付ける", colors)
        }
    }
}

// 3ページ目: チェックボックス付きプレビュー
@Composable
private fun AiPreviewIllustration(colors: AppColors) {
    TutorialIllustrationFrame(colors) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(horizontal = 16.dp),
        ) {
            PreviewRow(true, colors)
            PreviewRow(false, colors)
            PreviewRow(true, colors)
        }
    }
}

@Composable
private fun EmojiTile(emoji: String, colors: AppColors) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surface),
        contentAlignment = Alignment.Center,
    ) {
        Text(emoji, fontSize = 28.sp)
    }
}

@Composable
private fun ArrowIcon(colors: AppColors) {
    Icon(
        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
        contentDescription = null,
        tint = colors.textGray,
        modifier = Modifier.size(20.dp),
    )
}

@Composable
private fun StepRow(number: String, label: String, colors: AppColors) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(colors.primaryAccent),
            contentAlignment = Alignment.Center,
        ) {
            Text(number, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(10.dp))
        Text(label, color = colors.text, fontSize = 14.sp)
    }
}

@Composable
private fun PreviewRow(checked: Boolean, colors: AppColors) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(if (checked) colors.primaryAccent else colors.bg)
                .border(1.dp, colors.divider, RoundedCornerShape(4.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) {
                Text("✓", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .height(10.dp)
                .width(120.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(colors.surface),
        )
    }
}