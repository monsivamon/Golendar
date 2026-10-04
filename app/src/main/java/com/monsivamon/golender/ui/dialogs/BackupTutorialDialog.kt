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
fun BackupTutorialDialog(colors: AppColors, onComplete: () -> Unit) {
    SetupTutorialScaffold(
        pages = listOf(
            TutorialPage(
                title = "保存形式",
                description = "「写真もバックアップ」OFF なら JSON、\nON なら写真入り ZIP で保存します。\nZIP は Golendar モード専用です。",
                illustration = { c -> FormatIllustration(c) },
            ),
            TutorialPage(
                title = "追記と復元の違い",
                description = "「追記」は既存の予定を残したまま追加、\n「復元」は既存を上書きします。\nGoogleモードでは「復元」は使えません。",
                illustration = { c -> AppendVsRestoreIllustration(c) },
            ),
            TutorialPage(
                title = "復元先の選択",
                description = "Googleモードでの「追記」時は、\n書き込み可能なカレンダーを1つ選んで\n保存先にできます。",
                illustration = { c -> TargetPickerIllustration(c) },
            ),
        ),
        colors = colors,
        onComplete = onComplete,
        dismissLabel = "閉じる",
    )
}

@Composable
private fun FormatIllustration(colors: AppColors) {
    TutorialIllustrationFrame(colors) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FormatBox("JSON", colors)
            Text("or", color = colors.textGray, fontSize = 12.sp)
            FormatBox("ZIP", colors)
        }
    }
}

@Composable
private fun FormatBox(label: String, colors: AppColors) {
    Box(
        modifier = Modifier
            .width(70.dp)
            .height(80.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(colors.surface)
            .border(1.dp, colors.primaryAccent, RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("📄", fontSize = 22.sp)
            Spacer(Modifier.height(4.dp))
            Text(label, color = colors.primaryAccent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun AppendVsRestoreIllustration(colors: AppColors) {
    TutorialIllustrationFrame(colors) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // 追記
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("追記", color = colors.text, fontSize = 11.sp, modifier = Modifier.width(38.dp))
                Box(
                    modifier = Modifier
                        .width(40.dp)
                        .height(12.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(colors.primaryAccent.copy(alpha = 0.5f)),
                )
                Spacer(Modifier.width(4.dp))
                Box(
                    modifier = Modifier
                        .width(40.dp)
                        .height(12.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(colors.primaryAccent),
                )
            }
            // 復元
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("復元", color = colors.text, fontSize = 11.sp, modifier = Modifier.width(38.dp))
                Box(
                    modifier = Modifier
                        .width(40.dp)
                        .height(12.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(colors.bg)
                        .border(1.dp, colors.divider, RoundedCornerShape(3.dp)),
                )
                Spacer(Modifier.width(4.dp))
                Box(
                    modifier = Modifier
                        .width(40.dp)
                        .height(12.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(colors.primaryAccent),
                )
            }
            Text("薄い＝既存 / 濃い＝新規", color = colors.textGray, fontSize = 10.sp)
        }
    }
}

@Composable
private fun TargetPickerIllustration(colors: AppColors) {
    TutorialIllustrationFrame(colors) {
        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            TargetRow(true, colors)
            TargetRow(false, colors)
            TargetRow(false, colors)
        }
    }
}

@Composable
private fun TargetRow(selected: Boolean, colors: AppColors) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .width(14.dp)
                .height(14.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(if (selected) colors.primaryAccent else colors.surface)
                .border(1.dp, colors.divider, RoundedCornerShape(7.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Text("●", color = Color.White, fontSize = 6.sp)
            }
        }
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .width(110.dp)
                .height(12.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(colors.surface),
        )
    }
}