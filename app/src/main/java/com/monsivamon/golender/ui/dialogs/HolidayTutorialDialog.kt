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

// 祝日表示の国選択と、Google モードでの優先表示を案内するチュートリアル
@Composable
fun HolidayTutorialDialog(colors: AppColors, onComplete: () -> Unit) {
    SetupTutorialScaffold(
        pages = listOf(
            TutorialPage(
                title = stringResource(R.string.dl1_holidayTitleSelect),
                description = stringResource(R.string.dl1_holidayDescSelect),
                illustration = { c -> HolidayChecklistIllustration(c) },
            ),
            TutorialPage(
                title = stringResource(R.string.dl1_holidayTitlePriority),
                description = stringResource(R.string.dl1_holidayDescPriority),
                illustration = { c -> HolidayPriorityIllustration(c) },
            ),
        ),
        colors = colors,
        onComplete = onComplete,
        dismissLabel = stringResource(R.string.dl1_close),
    )
}

// 国チェックリスト風のイラスト
@Composable
private fun HolidayChecklistIllustration(colors: AppColors) {
    TutorialIllustrationFrame(colors) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.Start,
        ) {
            HolidayRow("🇯🇵", "JP", checked = true, colors)
            HolidayRow("🇺🇸", "US", checked = true, colors)
            HolidayRow("🇩🇪", "DE", checked = true, colors)
            HolidayRow("🇧🇷", "BR", checked = false, colors)
            Spacer(Modifier.height(2.dp))
            Text(
                text = stringResource(R.string.dl1_holidaySelectedBadge),
                color = colors.primaryAccent,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun HolidayRow(flag: String, iso: String, checked: Boolean, colors: AppColors) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(16.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(if (checked) colors.primaryAccent else colors.bg)
                .border(1.dp, colors.divider, RoundedCornerShape(3.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) Text("✓", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(8.dp))
        Text(flag, fontSize = 18.sp)
        Spacer(Modifier.width(6.dp))
        Box(
            modifier = Modifier
                .width(84.dp)
                .height(18.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(colors.surface)
                .border(1.dp, colors.divider, RoundedCornerShape(4.dp)),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(iso, color = colors.textGray, fontSize = 10.sp, modifier = Modifier.padding(start = 6.dp))
        }
    }
}

// 同日の祝日が 1 行にまとまり、Google 側と重複しないことを示すイラスト
@Composable
private fun HolidayPriorityIllustration(colors: AppColors) {
    TutorialIllustrationFrame(colors) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier
                    .width(180.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors.surface)
                    .border(1.dp, colors.primaryAccent, RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("🇯🇵 🇺🇸", fontSize = 14.sp)
                Spacer(Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(10.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(colors.primaryAccent.copy(alpha = 0.45f)),
                )
            }
            Text(
                text = stringResource(R.string.dl1_holidaySameDayBadge),
                color = colors.textGray,
                fontSize = 10.sp,
            )
            Row(
                modifier = Modifier
                    .width(180.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors.bg)
                    .border(1.dp, colors.divider, RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("G", color = colors.textGray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(8.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(colors.textGray.copy(alpha = 0.35f)),
                )
                Spacer(Modifier.width(8.dp))
                Text("×", color = colors.textGray, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            Text(
                text = stringResource(R.string.dl1_holidayGoogleSuppressed),
                color = colors.textGray,
                fontSize = 10.sp,
            )
        }
    }
}
