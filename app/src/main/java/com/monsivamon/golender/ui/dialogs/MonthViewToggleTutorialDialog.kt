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
import androidx.compose.material.icons.automirrored.filled.List
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

@Composable
fun MonthViewToggleTutorialDialog(colors: AppColors, onComplete: () -> Unit) {
    SetupTutorialScaffold(
        pages = listOf(
            TutorialPage(
                title = "下部リストの表示切替",
                description = "月表示のヘッダー左のアイコンで、\nカレンダー下の予定リストを ON/OFF できます。\nOFF にすると月グリッドが大きく表示されます。",
                illustration = { c -> ToggleIllustration(c) },
            ),
            TutorialPage(
                title = "日付タップでポップアップ",
                description = "下部リストを OFF にしているとき、\n日付をタップするとその日の予定が\nポップアップで確認できます。",
                illustration = { c -> PopupIllustration(c) },
            ),
        ),
        colors = colors,
        onComplete = onComplete,
        dismissLabel = "閉じる",
    )
}

@Composable
private fun ToggleIllustration(colors: AppColors) {
    TutorialIllustrationFrame(colors) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // ON 状態: グリッド + 下部リスト
            Row(verticalAlignment = Alignment.CenterVertically) {
                // トグルアイコン風
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(colors.primaryAccent.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.List,
                        contentDescription = null,
                        tint = colors.primaryAccent,
                        modifier = Modifier.size(12.dp),
                    )
                }
                Spacer(Modifier.width(6.dp))
                Text("ON", color = colors.primaryAccent, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(26.dp))
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Box(
                        modifier = Modifier
                            .width(110.dp)
                            .height(40.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(colors.primaryAccent.copy(alpha = 0.12f))
                            .border(1.dp, colors.divider, RoundedCornerShape(4.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("月グリッド", color = colors.textGray, fontSize = 9.sp)
                    }
                    Box(
                        modifier = Modifier
                            .width(110.dp)
                            .height(22.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(colors.primaryAccent.copy(alpha = 0.45f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("予定リスト", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            // OFF 状態: グリッドのみ（拡大）
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(colors.surface)
                        .border(1.dp, colors.divider, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.List,
                        contentDescription = null,
                        tint = colors.textGray,
                        modifier = Modifier.size(12.dp),
                    )
                }
                Spacer(Modifier.width(6.dp))
                Text("OFF", color = colors.textGray, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(26.dp))
                Box(
                    modifier = Modifier
                        .width(110.dp)
                        .height(64.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(colors.primaryAccent.copy(alpha = 0.12f))
                        .border(1.dp, colors.divider, RoundedCornerShape(4.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("月グリッド（拡大）", color = colors.textGray, fontSize = 9.sp)
                }
            }
        }
    }
}

@Composable
private fun PopupIllustration(colors: AppColors) {
    TutorialIllustrationFrame(colors) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // 月グリッド風 3列×2行
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                repeat(2) { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        repeat(4) { col ->
                            val isTapped = row == 0 && col == 2
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(if (isTapped) colors.primaryAccent else colors.surface)
                                    .border(1.dp, colors.divider, RoundedCornerShape(3.dp)),
                            )
                        }
                    }
                }
            }
            Text("↓ タップ", color = colors.textGray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            // ポップアップ風の枠
            Box(
                modifier = Modifier
                    .width(150.dp)
                    .height(34.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors.surface)
                    .border(1.5.dp, colors.primaryAccent, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text("その日の予定", color = colors.primaryAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}