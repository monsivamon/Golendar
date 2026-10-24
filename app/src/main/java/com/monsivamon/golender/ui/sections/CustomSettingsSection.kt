package com.monsivamon.golender.ui.sections

import android.app.Activity
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.monsivamon.golender.R
import com.monsivamon.golender.MainActivity
import com.monsivamon.golender.data.dataStore
import com.monsivamon.golender.data.prefs.LanguageSetting
import com.monsivamon.golender.data.util.dayShort
import com.monsivamon.golender.ui.LIST_ROW_VERTICAL
import com.monsivamon.golender.ui.RadioOptionRow
import com.monsivamon.golender.ui.Routes
import com.monsivamon.golender.ui.SECTION_DIVIDER_VERTICAL
import com.monsivamon.golender.ui.SECTION_LABEL_BOTTOM
import com.monsivamon.golender.ui.SettingsSection
import com.monsivamon.golender.ui.theme.AppColors
import com.monsivamon.golender.viewmodel.CalendarMode
import com.monsivamon.golender.viewmodel.CalendarViewModel
import com.monsivamon.golender.viewmodel.ThemeMode
import kotlinx.coroutines.launch
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
    val calendarBgColor by viewModel.calendarBgColor.collectAsState()
    val dayColors by viewModel.dayColors.collectAsState()
    val calendarMode by viewModel.calendarMode.collectAsState()
    val showAlbumTab by viewModel.showAlbumTab.collectAsState()
    val context = LocalContext.current

    SettingsSection(stringResource(R.string.sc_customSettingsSection), colors) {
        Text(
            stringResource(R.string.sc_appBgColor),
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
            Text(stringResource(R.string.sc_appBgColorSelect), fontSize = 16.sp, color = colors.text)
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
        // アルバムタブは Golendar モードでのみ利用できる
        if (calendarMode == CalendarMode.GOLENDAR) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.setShowAlbumTab(!showAlbumTab) }
                    .padding(vertical = LIST_ROW_VERTICAL),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.sc_albumTab), fontSize = 16.sp, color = colors.text)
                    Text(stringResource(R.string.sc_albumTabDesc), fontSize = 12.sp, color = colors.textGray)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Switch(
                    checked = showAlbumTab,
                    onCheckedChange = { viewModel.setShowAlbumTab(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = colors.primaryAccent,
                        checkedTrackColor = colors.primaryAccent.copy(alpha = 0.7f),
                    ),
                )
            }
            HorizontalDivider(color = colors.divider, modifier = Modifier.padding(vertical = SECTION_DIVIDER_VERTICAL))
        }
        Text(
            stringResource(R.string.sc_dayColors),
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
                Text(stringResource(R.string.sc_dayOfWeekRow, day.dayShort(context)), fontSize = 16.sp, color = colors.text)
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
            stringResource(R.string.sc_displayTheme),
            fontSize = 14.sp, fontWeight = FontWeight.Bold, color = colors.textGray,
            modifier = Modifier.padding(bottom = SECTION_LABEL_BOTTOM),
        )
        ThemeMode.entries.forEach { mode ->
            RadioOptionRow(
                selected = themeMode == mode,
                label = when (mode) {
                    ThemeMode.SYSTEM -> stringResource(R.string.sc_themeSystem)
                    ThemeMode.LIGHT -> stringResource(R.string.sc_themeLight)
                    ThemeMode.DARK -> stringResource(R.string.sc_themeDark)
                },
                colors = colors,
                onClick = { viewModel.setThemeMode(mode) },
            )
        }
        HorizontalDivider(color = colors.divider, modifier = Modifier.padding(vertical = SECTION_DIVIDER_VERTICAL))
        Text(
            stringResource(R.string.sc_weekStart),
            fontSize = 14.sp, fontWeight = FontWeight.Bold, color = colors.textGray,
            modifier = Modifier.padding(bottom = SECTION_LABEL_BOTTOM),
        )
        listOf(
            DayOfWeek.SUNDAY to stringResource(R.string.sc_weekStartSunday),
            DayOfWeek.MONDAY to stringResource(R.string.sc_weekStartMonday),
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

// アプリ内言語の選択セクション（単体の設定項目として独立・4言語のみ）
// 選択値は DataStore に保存し、MainActivity.attachBaseContext で locale として差し込む。
@Composable
fun LanguageSettingsSection(colors: AppColors) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // 現在の選択値（初回はブロッキング読み出しで確定させる。未設定は端末言語から判定）
    val selectedTag = remember {
        runCatching { LanguageSetting.sync(context) }.getOrDefault(LanguageSetting.defaultTag(context))
    }

    SettingsSection(stringResource(R.string.sc_language), colors) {
        // 言語ラベルは各言語のネイティブ表記（翻訳しない）
        val languageLabels = listOf(
            "ja" to "日本語",
            "en" to "English",
            "zh-CN" to "简体中文",
            "ko" to "한국어",
        )
        languageLabels.forEach { (tag, label) ->
            RadioOptionRow(
                selected = selectedTag == tag,
                label = label,
                colors = colors,
                onClick = {
                    if (tag != selectedTag) {
                        scope.launch {
                            LanguageSetting.set(context, tag)
                            // 再生成後も設定画面にとどまることを MainActivity へ伝えておく
                            MainActivity.pendingRouteAfterRecreate = Routes.SETTINGS
                            // locale 差し替えを Activity 再生成（attachBaseContext での再読込）で反映する
                            (context as? Activity)?.recreate()
                        }
                    }
                },
            )
        }
    }
}