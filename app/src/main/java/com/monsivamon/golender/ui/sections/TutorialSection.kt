package com.monsivamon.golender.ui.sections

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import com.monsivamon.golender.R
import com.monsivamon.golender.ui.LIST_ROW_VERTICAL
import com.monsivamon.golender.ui.SettingsSection
import com.monsivamon.golender.ui.theme.AppColors
import com.monsivamon.golender.viewmodel.CalendarViewModel

// チュートリアル再表示セクション（各機能の案内を再表示）
@Composable
fun TutorialSection(
    viewModel: CalendarViewModel,
    colors: AppColors,
) {
    SettingsSection(stringResource(R.string.sc_tutorialSection), colors) {
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
            Text(stringResource(R.string.sc_tutorialCalendarPermission), fontSize = 16.sp, color = colors.text)
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
            Text(stringResource(R.string.sc_tutorialGesture), fontSize = 16.sp, color = colors.text)
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
            Text(stringResource(R.string.sc_tutorialMap), fontSize = 16.sp, color = colors.text)
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
            Text(stringResource(R.string.sc_tutorialAi), fontSize = 16.sp, color = colors.text)
        }
        TutorialRow(stringResource(R.string.sc_tutorialCalendarDisplay), colors) { viewModel.requestShowCalendarModeTutorial() }
        TutorialRow(stringResource(R.string.sc_tutorialTimezone), colors) { viewModel.requestShowTimezoneTutorial() }
        TutorialRow(stringResource(R.string.sc_tutorialHoliday), colors) { viewModel.requestShowHolidayTutorial() }
        TutorialRow(stringResource(R.string.sc_tutorialMonthList), colors) { viewModel.requestShowMonthViewToggleTutorial() }
        TutorialRow(stringResource(R.string.sc_tutorialScheduleAdd), colors) { viewModel.requestShowScheduleAddTutorial() }
        TutorialRow(stringResource(R.string.sc_tutorialScheduleEdit), colors) { viewModel.requestShowScheduleEditTutorial() }
        TutorialRow(stringResource(R.string.sc_tutorialEventColor), colors) { viewModel.requestShowEventColorTutorial() }
        TutorialRow(stringResource(R.string.sc_tutorialRecurring), colors) { viewModel.requestShowRecurringTutorial() }
        TutorialRow(stringResource(R.string.sc_tutorialPhoto), colors) { viewModel.requestShowPhotoTutorial() }
        TutorialRow(stringResource(R.string.sc_tutorialSearch), colors) { viewModel.requestShowSearchTutorial() }
        TutorialRow(stringResource(R.string.sc_tutorialNotification), colors) { viewModel.requestShowNotificationTutorial() }
        TutorialRow(stringResource(R.string.sc_tutorialBackup), colors) { viewModel.requestShowBackupTutorial() }
        TutorialRow(stringResource(R.string.sc_tutorialWidget), colors) { viewModel.requestShowWidgetTutorial() }
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