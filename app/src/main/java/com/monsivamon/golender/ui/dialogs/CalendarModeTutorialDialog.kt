package com.monsivamon.golender.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
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

// カレンダーモード（Google / Golendar）の違いと、カレンダー一覧の操作を説明するチュートリアル
@Composable
fun CalendarModeTutorialDialog(colors: AppColors, onComplete: () -> Unit) {
    SetupTutorialScaffold(
        pages = listOf(
            TutorialPage(
                title = stringResource(R.string.dl1_calModeTitleDifference),
                description = stringResource(R.string.dl1_calModeDescDifference),
                illustration = { c -> ModeCompareIllustration(c) },
            ),
            TutorialPage(
                title = stringResource(R.string.dl1_calModeTitleGoogleOps),
                description = stringResource(R.string.dl1_calModeDescGoogleOps),
                illustration = { c -> CalendarListIllustration(c, showEditHint = false) },
            ),
            TutorialPage(
                title = stringResource(R.string.dl1_calModeTitleGolendarOps),
                description = stringResource(R.string.dl1_calModeDescGolendarOps),
                illustration = { c -> CalendarListIllustration(c, showEditHint = true) },
            ),
            TutorialPage(
                title = stringResource(R.string.dl1_calModeTitleHidden),
                description = stringResource(R.string.dl1_calModeDescHidden),
                illustration = { c -> EffectIllustration(c) },
            ),
        ),
        colors = colors,
        onComplete = onComplete,
        dismissLabel = stringResource(R.string.dl1_close),
    )
}

// Golendar / Google の比較イラスト（2 枚のカードを並べる）
@Composable
private fun ModeCompareIllustration(colors: AppColors) {
    TutorialIllustrationFrame(colors) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ModeCard(
                label = "Golendar",
                sub = stringResource(R.string.dl1_calModeSubInApp),
                colors = colors,
                accent = colors.primaryAccent,
            )
            ModeCard(
                label = "Google",
                sub = stringResource(R.string.dl1_calModeSubSync),
                colors = colors,
                accent = colors.satBlue,
            )
        }
    }
}

@Composable
private fun ModeCard(
    label: String,
    sub: String,
    colors: AppColors,
    accent: Color,
) {
    Box(
        modifier = Modifier
            .width(96.dp)
            .height(104.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surface)
            .border(2.dp, accent, RoundedCornerShape(14.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (label == "Golendar") "🔒" else "☁",
                    fontSize = 18.sp,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = label,
                color = accent,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = sub,
                color = colors.textGray,
                fontSize = 10.sp,
            )
        }
    }
}

// カレンダー一覧風のイラスト（Checkbox + 色丸 + 名前バー）
@Composable
private fun CalendarListIllustration(colors: AppColors, showEditHint: Boolean) {
    TutorialIllustrationFrame(colors) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.Start,
        ) {
            MiniRow(checked = true, barWidth = 80, colors = colors)
            MiniRow(checked = true, barWidth = 60, colors = colors)
            MiniRow(checked = false, barWidth = 72, colors = colors)
            if (showEditHint) {
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.dl1_calModeEditHint),
                    color = colors.textGray,
                    fontSize = 10.sp,
                )
            }
        }
    }
}

@Composable
private fun MiniRow(checked: Boolean, barWidth: Int, colors: AppColors) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(16.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(if (checked) colors.primaryAccent else colors.bg)
                .border(1.dp, colors.divider, RoundedCornerShape(3.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) {
                Text("✓", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.width(6.dp))
        Box(
            modifier = Modifier
                .size(16.dp)
                .clip(CircleShape)
                .background(colors.primaryAccent.copy(alpha = 0.5f))
        )
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .width(barWidth.dp)
                .height(10.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(colors.surface)
        )
    }
}

// 「非表示にすると…」のイラスト
@Composable
private fun EffectIllustration(colors: AppColors) {
    TutorialIllustrationFrame(colors) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("📅  🧩  🔔", fontSize = 28.sp)
            Spacer(Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .width(150.dp)
                    .height(30.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors.surface)
                    .border(1.dp, colors.divider, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(R.string.dl1_calModeHolidaysAlways), color = colors.primaryAccent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}