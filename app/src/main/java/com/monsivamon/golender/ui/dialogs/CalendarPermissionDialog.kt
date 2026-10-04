package com.monsivamon.golender.ui.dialogs

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DateRange
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

// Google カレンダーへのアクセス許可を案内するダイアログ。
// ジェスチャー画面と同じ土台（SetupTutorialScaffold）で 1 ページ構成として表示する。
@Composable
fun CalendarPermissionDialog(
    colors: AppColors,
    title: String,
    message: String,
    confirmLabel: String = "許可する",
    dismissLabel: String = "あとで",
    onResult: (granted: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    // 読み取り・書き込みの複数権限を要求するランチャー
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val readGranted = permissions[Manifest.permission.READ_CALENDAR] == true
        val writeGranted = permissions[Manifest.permission.WRITE_CALENDAR] == true
        // 読み書き両方が許可された場合のみ成功扱い
        onResult(readGranted && writeGranted)
    }

    SetupTutorialScaffold(
        pages = listOf(
            TutorialPage(
                title = title,
                description = message,
                illustration = { colors -> CalendarIllustration(colors) },
            )
        ),
        colors = colors,
        onComplete = onDismiss,
        onConfirmOverride = { _, _ ->
            launcher.launch(
                arrayOf(
                    Manifest.permission.READ_CALENDAR,
                    Manifest.permission.WRITE_CALENDAR,
                )
            )
        },
        confirmLabelLast = confirmLabel,
        dismissLabel = dismissLabel,
    )
}

// カレンダーアイコン風のイラスト（大きなカレンダーと承認チェック）
@Composable
private fun CalendarIllustration(colors: AppColors) {
    TutorialIllustrationFrame(colors) {
        Box(contentAlignment = Alignment.Center) {
            // カレンダー本体
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .border(3.dp, colors.primaryAccent, RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    // 上部の赤いヘッダー
                    Box(
                        modifier = Modifier
                            .width(88.dp)
                            .height(18.dp)
                            .background(colors.sunRed)
                            .clip(RoundedCornerShape(topStart = 11.dp, topEnd = 11.dp)),
                    )
                    Spacer(Modifier.height(4.dp))
                    Icon(
                        imageVector = Icons.Default.DateRange,
                        contentDescription = null,
                        tint = colors.primaryAccent,
                        modifier = Modifier.size(38.dp),
                    )
                }
            }
            // 右下の承認チェック
            Box(
                modifier = Modifier
                    .padding(start = 62.dp, top = 62.dp)
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(colors.primaryAccent),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}