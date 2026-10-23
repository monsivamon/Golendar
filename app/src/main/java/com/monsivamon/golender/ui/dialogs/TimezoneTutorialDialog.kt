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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.monsivamon.golender.R
import com.monsivamon.golender.ui.common.SetupTutorialScaffold
import com.monsivamon.golender.ui.common.TutorialIllustrationFrame
import com.monsivamon.golender.ui.common.TutorialPage
import com.monsivamon.golender.ui.theme.AppColors

// 表示タイムゾーンと通知時刻の固定を案内するチュートリアル
@Composable
fun TimezoneTutorialDialog(colors: AppColors, onComplete: () -> Unit) {
    SetupTutorialScaffold(
        pages = listOf(
            TutorialPage(
                title = stringResource(R.string.dl1_tzTitlePin),
                description = stringResource(R.string.dl1_tzDescPin),
                illustration = { c -> TimezoneCompareIllustration(c) },
            ),
            TutorialPage(
                title = stringResource(R.string.dl1_tzTitleNotify),
                description = stringResource(R.string.dl1_tzDescNotify),
                illustration = { c -> TimezoneNotifyIllustration(c) },
            ),
        ),
        colors = colors,
        onComplete = onComplete,
        dismissLabel = stringResource(R.string.dl1_close),
    )
}

// 端末時刻と固定タイムゾーンの比較イラスト
@Composable
private fun TimezoneCompareIllustration(colors: AppColors) {
    TutorialIllustrationFrame(colors) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ClockCard("09:00", "UTC+9", colors.primaryAccent, colors)
                Text("→", color = colors.textGray, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                ClockCard("01:00", "UTC+1", colors.satBlue, colors)
            }
            Text(
                text = stringResource(R.string.dl1_tzPinHint),
                color = colors.textGray,
                fontSize = 10.sp,
            )
        }
    }
}

@Composable
private fun ClockCard(time: String, zone: String, accent: androidx.compose.ui.graphics.Color, colors: AppColors) {
    Box(
        modifier = Modifier
            .width(84.dp)
            .height(64.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surface)
            .border(2.dp, accent, RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(time, color = accent, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(2.dp))
            Text(zone, color = colors.textGray, fontSize = 10.sp)
        }
    }
}

// 通知が現地の時計に合わせて鳴ることを示すイラスト
@Composable
private fun TimezoneNotifyIllustration(colors: AppColors) {
    TutorialIllustrationFrame(colors) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("🔔", fontSize = 34.sp)
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.surface)
                    .border(1.dp, colors.divider, RoundedCornerShape(10.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Text("09:00", color = colors.primaryAccent, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
            Text(
                text = stringResource(R.string.dl1_tzNotifyHint),
                color = colors.textGray,
                fontSize = 10.sp,
            )
        }
    }
}
