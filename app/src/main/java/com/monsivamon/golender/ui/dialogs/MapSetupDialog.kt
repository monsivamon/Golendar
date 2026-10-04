package com.monsivamon.golender.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
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

// 地図の使い方を 3 ページで案内するチュートリアル（設定画面から再表示可能）
@Composable
fun MapSetupDialog(
    colors: AppColors,
    onComplete: () -> Unit,
) {
    SetupTutorialScaffold(
        pages = listOf(
            TutorialPage(
                title = "ピンで場所を選ぶ",
                description = "地図を動かして中央のピンを場所に合わせ、\n「決定」で場所を保存します。",
                illustration = { c -> MapPinIllustration(c) },
            ),
            TutorialPage(
                title = "現在地ボタン",
                description = "右下のボタンをタップするたびに\n「追従 → コンパス追従 → OFF」と切り替わります。",
                illustration = { c -> LocationButtonIllustration(c) },
            ),
            TutorialPage(
                title = "場所の保存形式",
                description = "「POI名, 住所」の形式で保存され、\nGoogleマップなどと相互運用できます。",
                illustration = { c -> SavedLocationIllustration(c) },
            ),
        ),
        colors = colors,
        onComplete = onComplete,
    )
}

// 地図の権限案内を 1 ページで表示する（初回地図表示時・現在地ボタンタップ時）
@Composable
fun MapPermissionIntroDialog(
    colors: AppColors,
    title: String,
    message: String,
    confirmLabel: String,
    dismissLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    SetupTutorialScaffold(
        pages = listOf(
            TutorialPage(
                title = title,
                description = message,
                illustration = { c -> MapPinIllustration(c) },
            ),
        ),
        colors = colors,
        onComplete = onDismiss,
        onConfirmOverride = { _, _ -> onConfirm() },
        confirmLabelLast = confirmLabel,
        dismissLabel = dismissLabel,
    )
}

// 地図＋中央ピンのイラスト
@Composable
private fun MapPinIllustration(colors: AppColors) {
    TutorialIllustrationFrame(colors) {
        Box(
            modifier = Modifier.size(160.dp),
            contentAlignment = Alignment.Center,
        ) {
            // 地図風の背景（薄い surface 色の枠）
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors.surface),
            )
            // 道路風のライン（横）
            Box(
                modifier = Modifier
                    .width(140.dp)
                    .height(2.dp)
                    .background(colors.divider)
                    .align(Alignment.Center),
            )
            // 道路風のライン（縦）
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .height(140.dp)
                    .background(colors.divider)
                    .align(Alignment.Center),
            )
            // 中央のピン
            Icon(
                imageVector = Icons.Default.LocationOn,
                contentDescription = null,
                tint = colors.primaryAccent,
                modifier = Modifier.size(56.dp),
            )
        }
    }
}

// 現在地ボタンのイラスト（3段階トグル＋ボタン）
@Composable
private fun LocationButtonIllustration(colors: AppColors) {
    TutorialIllustrationFrame(colors) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // 3段階のトグルを示す 3 つの円
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StagePill("1", colors, active = true)
                StagePill("2", colors, active = false)
                StagePill("3", colors, active = false)
            }
            // 現在地ボタン風の円（中央ドット付き）
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .border(2.dp, colors.primaryAccent, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .border(2.dp, colors.primaryAccent, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(colors.primaryAccent),
                    )
                }
            }
        }
    }
}

@Composable
private fun StagePill(label: String, colors: AppColors, active: Boolean) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(if (active) colors.primaryAccent else colors.surface)
            .border(1.dp, colors.divider, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (active) Color.White else colors.textGray,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

// 保存形式のイラスト（POI名＋住所）
@Composable
private fun SavedLocationIllustration(colors: AppColors) {
    TutorialIllustrationFrame(colors) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(horizontal = 12.dp),
        ) {
            LocationRow("📍", "東京駅", colors, emphasized = true)
            LocationRow("", "東京都千代田区丸の内1丁目", colors, emphasized = false)
        }
    }
}

@Composable
private fun LocationRow(
    prefix: String,
    text: String,
    colors: AppColors,
    emphasized: Boolean,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (prefix.isNotBlank()) {
            Text(prefix, fontSize = 14.sp)
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text = text,
            color = if (emphasized) colors.text else colors.textGray,
            fontSize = if (emphasized) 14.sp else 12.sp,
            fontWeight = if (emphasized) FontWeight.Bold else FontWeight.Normal,
        )
    }
}