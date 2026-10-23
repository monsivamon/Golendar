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
import com.monsivamon.golender.ui.theme.calendarColorPalette

// 予定ごとの色変更と、カレンダー色への戻し方を案内するチュートリアル
@Composable
fun EventColorTutorialDialog(colors: AppColors, onComplete: () -> Unit) {
    SetupTutorialScaffold(
        pages = listOf(
            TutorialPage(
                title = stringResource(R.string.dl1_colorTitlePerEvent),
                description = stringResource(R.string.dl1_colorDescPerEvent),
                illustration = { c -> ColoredEventsIllustration(c) },
            ),
            TutorialPage(
                title = stringResource(R.string.dl1_colorTitleReset),
                description = stringResource(R.string.dl1_colorDescReset),
                illustration = { c -> PaletteIllustration(c) },
            ),
        ),
        colors = colors,
        onComplete = onComplete,
        dismissLabel = stringResource(R.string.dl1_close),
    )
}

// 色付きの予定カードが並ぶイラスト
@Composable
private fun ColoredEventsIllustration(colors: AppColors) {
    TutorialIllustrationFrame(colors) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.Start,
        ) {
            ColoredEventRow(colors.primaryAccent, colors)
            ColoredEventRow(Color(0xFFFF9800), colors)
            ColoredEventRow(Color(0xFF4CAF50), colors)
            Spacer(Modifier.height(2.dp))
            Text(
                text = stringResource(R.string.dl1_colorBadgePriority),
                color = colors.primaryAccent,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun ColoredEventRow(accent: Color, colors: AppColors) {
    Row(
        modifier = Modifier
            .width(170.dp)
            .height(30.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(colors.surface)
            .border(1.dp, colors.divider, RoundedCornerShape(8.dp)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(5.dp)
                .height(30.dp)
                .background(accent),
        )
        Spacer(Modifier.width(10.dp))
        Box(
            modifier = Modifier
                .width(100.dp)
                .height(10.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(colors.textGray.copy(alpha = 0.35f)),
        )
    }
}

// 既定色とカラーパレットのイラスト
@Composable
private fun PaletteIllustration(colors: AppColors) {
    val palette = colors.calendarColorPalette()
    TutorialIllustrationFrame(colors) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(colors.primaryAccent),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.dl1_colorBadgeDefault),
                    color = colors.text,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.width(6.dp))
                Text("✓", color = colors.primaryAccent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            // パレットの一部を 2 列で表示する
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                palette.take(8).chunked(4).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { color ->
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .border(1.dp, colors.divider, CircleShape),
                            )
                        }
                    }
                }
            }
            Text(
                text = stringResource(R.string.dl1_colorBadgeCustom),
                color = colors.textGray,
                fontSize = 10.sp,
            )
        }
    }
}
