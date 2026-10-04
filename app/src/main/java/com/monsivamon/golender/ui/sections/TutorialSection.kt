package com.monsivamon.golender.ui.sections

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import com.monsivamon.golender.ui.LIST_ROW_VERTICAL
import com.monsivamon.golender.ui.SettingsSection
import com.monsivamon.golender.ui.theme.AppColors
import com.monsivamon.golender.viewmodel.CalendarViewModel

// チュートリアル再表示セクション（カレンダー権限・ジェスチャー・AI解析）
@Composable
fun TutorialSection(
    viewModel: CalendarViewModel,
    colors: AppColors,
) {
    SettingsSection("チュートリアル", colors) {
        // カレンダー権限の案内を再表示
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { viewModel.requestShowCalendarTutorial() },
                )
                .padding(vertical = LIST_ROW_VERTICAL),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("カレンダーのアクセス権限を確認する", fontSize = 16.sp, color = colors.text)
        }
        // ジェスチャー操作の案内を再表示
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { viewModel.requestShowGestureTutorial() },
                )
                .padding(vertical = LIST_ROW_VERTICAL),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("ジェスチャー操作の案内を見る", fontSize = 16.sp, color = colors.text)
        }
        // 地図の使い方を再表示
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { viewModel.requestShowMapTutorial() },
                )
                .padding(vertical = LIST_ROW_VERTICAL),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("地図の使い方を見る", fontSize = 16.sp, color = colors.text)
        }

        // AI 解析の使い方を再表示
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { viewModel.requestShowAiSetup() },
                )
                .padding(vertical = LIST_ROW_VERTICAL),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("AI解析の使い方を見る", fontSize = 16.sp, color = colors.text)
        }
        TutorialRow("月表示の下部リストを見る", colors) { viewModel.requestShowMonthViewToggleTutorial() }
        TutorialRow("予定の追加を見る", colors) { viewModel.requestShowScheduleAddTutorial() }
        TutorialRow("予定の編集と削除を見る", colors) { viewModel.requestShowScheduleEditTutorial() }
        TutorialRow("繰り返し予定の使い方を見る", colors) { viewModel.requestShowRecurringTutorial() }
        TutorialRow("写真の活用を見る", colors) { viewModel.requestShowPhotoTutorial() }
        TutorialRow("検索の使い方を見る", colors) { viewModel.requestShowSearchTutorial() }
        TutorialRow("通知の設定を見る", colors) { viewModel.requestShowNotificationTutorial() }
        TutorialRow("バックアップと復元を見る", colors) { viewModel.requestShowBackupTutorial() }
        TutorialRow("ウィジェットとショートカットを見る", colors) { viewModel.requestShowWidgetTutorial() }
    }
}

// チュートリアル再表示の 1 行
@Composable
private fun TutorialRow(label: String, colors: AppColors, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(vertical = LIST_ROW_VERTICAL),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 16.sp, color = colors.text)
    }
}