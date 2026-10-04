package com.monsivamon.golender.ui.sections

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.monsivamon.golender.data.util.getJpDayOfWeek
import com.monsivamon.golender.ui.LIST_ROW_VERTICAL
import com.monsivamon.golender.ui.RadioOptionRow
import com.monsivamon.golender.ui.SECTION_DIVIDER_VERTICAL
import com.monsivamon.golender.ui.SECTION_LABEL_BOTTOM
import com.monsivamon.golender.ui.SettingsSection
import com.monsivamon.golender.ui.theme.AppColors
import com.monsivamon.golender.viewmodel.CalendarViewModel
import com.monsivamon.golender.viewmodel.ThemeMode
import java.time.DayOfWeek

// カスタム設定（祝日表示・背景色・曜日色・テーマ・週の始まり）
@Composable
fun CustomSettingsSection(
    viewModel: CalendarViewModel,
    colors: AppColors,
    isSystemDark: Boolean,
    onOpenBgColorPicker: () -> Unit,
    onOpenDayColorPicker: (DayOfWeek) -> Unit,
) {
    val themeMode by viewModel.themeMode.collectAsState()
    val weekStartDay by viewModel.weekStartDay.collectAsState()
    val showHolidays by viewModel.showHolidays.collectAsState()
    val calendarBgColor by viewModel.calendarBgColor.collectAsState()
    val dayColors by viewModel.dayColors.collectAsState()

    SettingsSection("カスタム設定", colors) {
        // 祝日表示 ON/OFF
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
            Switch(
                checked = showHolidays,
                onCheckedChange = { viewModel.setShowHolidays(it) },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = colors.primaryAccent,
                    checkedTrackColor = colors.primaryAccent.copy(alpha = 0.5f),
                ),
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text("祝日を表示", fontSize = 16.sp, color = colors.text)
        }
        HorizontalDivider(color = colors.divider, modifier = Modifier.padding(vertical = SECTION_DIVIDER_VERTICAL))
        Text(
            "アプリ背景色",
            fontSize = 14.sp, fontWeight = FontWeight.Bold, color = colors.textGray,
            modifier = Modifier.padding(bottom = SECTION_LABEL_BOTTOM),
        )
        // アプリ背景色の選択行
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onOpenBgColorPicker,
                )
                .padding(vertical = LIST_ROW_VERTICAL),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("アプリ背景色を選択", fontSize = 16.sp, color = colors.text)
            Box(
                modifier = Modifier.size(24.dp).clip(CircleShape)
                    .background(if (calendarBgColor == Color.Unspecified) Color.Transparent else calendarBgColor)
                    .border(1.dp, colors.textGray, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (calendarBgColor == Color.Unspecified) Text("/", color = colors.textGray, fontSize = 14.sp)
            }
        }
        HorizontalDivider(color = colors.divider, modifier = Modifier.padding(vertical = SECTION_DIVIDER_VERTICAL))
        Text(
            "曜日の色",
            fontSize = 14.sp, fontWeight = FontWeight.Bold, color = colors.textGray,
            modifier = Modifier.padding(bottom = SECTION_LABEL_BOTTOM),
        )
        val days = listOf(
            DayOfWeek.SUNDAY, DayOfWeek.MONDAY, DayOfWeek.TUESDAY,
            DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY,
        )
        days.forEach { day ->
            val color = dayColors[day] ?: Color.Unspecified
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { onOpenDayColorPicker(day) },
                    )
                    .padding(vertical = LIST_ROW_VERTICAL),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(getJpDayOfWeek(day) + "曜日", fontSize = 16.sp, color = colors.text)
                Box(
                    modifier = Modifier.size(24.dp).clip(CircleShape)
                        .background(if (color == Color.Unspecified) Color.Transparent else color)
                        .border(1.dp, colors.textGray, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    if (color == Color.Unspecified) Text("/", color = colors.textGray, fontSize = 14.sp)
                }
            }
        }
        HorizontalDivider(color = colors.divider, modifier = Modifier.padding(vertical = SECTION_DIVIDER_VERTICAL))
        Text(
            "表示テーマ",
            fontSize = 14.sp, fontWeight = FontWeight.Bold, color = colors.textGray,
            modifier = Modifier.padding(bottom = SECTION_LABEL_BOTTOM),
        )
        ThemeMode.entries.forEach { mode ->
            RadioOptionRow(
                selected = themeMode == mode,
                label = when (mode) {
                    ThemeMode.SYSTEM -> "端末の設定に合わせる"
                    ThemeMode.LIGHT -> "ライトモード"
                    ThemeMode.DARK -> "ダークモード"
                },
                colors = colors,
                onClick = { viewModel.setThemeMode(mode) },
            )
        }
        HorizontalDivider(color = colors.divider, modifier = Modifier.padding(vertical = SECTION_DIVIDER_VERTICAL))
        Text(
            "週の始まり",
            fontSize = 14.sp, fontWeight = FontWeight.Bold, color = colors.textGray,
            modifier = Modifier.padding(bottom = SECTION_LABEL_BOTTOM),
        )
        listOf(
            DayOfWeek.SUNDAY to "日曜日から始める",
            DayOfWeek.MONDAY to "月曜日から始める",
        ).forEach { (day, label) ->
            RadioOptionRow(
                selected = weekStartDay == day,
                label = label,
                colors = colors,
                onClick = { viewModel.setWeekStartDay(day) },
            )
        }
    }
}