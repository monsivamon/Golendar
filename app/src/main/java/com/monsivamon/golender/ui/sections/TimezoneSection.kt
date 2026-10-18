package com.monsivamon.golender.ui.sections

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.monsivamon.golender.R
import com.monsivamon.golender.data.prefs.AppTimeZone
import com.monsivamon.golender.ui.SettingsSection
import com.monsivamon.golender.ui.dialogs.TimezonePickerDialog
import com.monsivamon.golender.ui.theme.AppColors
import com.monsivamon.golender.viewmodel.CalendarViewModel

// タイムゾーン設定セクション。
// 表示タイムゾーンの固定／固定するゾーン選択（全世界対応）／通知時刻の現地時計合わせを提供する。
@Composable
fun TimezoneSection(
    viewModel: CalendarViewModel,
    colors: AppColors,
) {
    val tz by viewModel.timezoneSetting.collectAsState()
    var showPicker by remember { mutableStateOf(false) }
    // 現在の「表示基準」ラベル。固定 OFF 時は端末ゾーンが効いている。
    val effectiveZoneId = if (tz.pinned) tz.zoneId else java.time.ZoneId.systemDefault().id
    val effectiveLabel = AppTimeZone.zoneLabel(effectiveZoneId)

    SettingsSection(stringResource(R.string.tz_sectionTitle), colors) {
        // 表示タイムゾーンを固定 ON/OFF
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        ) {
            Switch(
                checked = tz.pinned,
                onCheckedChange = { checked ->
                    viewModel.updateTimezoneSetting(checked, tz.zoneId, tz.notifyPinned)
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = colors.primaryAccent,
                    checkedTrackColor = colors.primaryAccent.copy(alpha = 0.7f),
                ),
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.tz_pinTitle), fontSize = 16.sp, color = colors.text)
                Text(stringResource(R.string.tz_pinDesc), fontSize = 12.sp, color = colors.textGray)
            }
        }

        // 固定するタイムゾーン選択行
        Text(
            stringResource(R.string.tz_pinnedZoneLabel),
            fontSize = 14.sp, fontWeight = FontWeight.Bold, color = colors.textGray,
            modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(colors.bg)
                .border(1.dp, colors.divider, RoundedCornerShape(8.dp))
                .clickable(enabled = tz.pinned) { showPicker = true }
                .padding(horizontal = 12.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                AppTimeZone.zoneLabel(tz.zoneId),
                color = if (tz.pinned) colors.text else colors.textGray,
                fontSize = 15.sp,
                modifier = Modifier.weight(1f),
            )
            Text("▼", color = if (tz.pinned) colors.primaryAccent else colors.textGray)
        }

        // 現在の表示基準（固定 OFF 時は端末ゾーン）
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.tz_currentBase, effectiveLabel),
            fontSize = 12.sp, color = colors.textGray,
        )

        HorizontalDivider(color = colors.divider, modifier = Modifier.padding(vertical = 12.dp))

        // 通知時刻も固定（現地の時計に合わせる）
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        ) {
            Switch(
                checked = tz.notifyPinned,
                enabled = tz.pinned,
                onCheckedChange = { checked ->
                    viewModel.updateTimezoneSetting(tz.pinned, tz.zoneId, checked)
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = colors.primaryAccent,
                    checkedTrackColor = colors.primaryAccent.copy(alpha = 0.7f),
                ),
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.tz_notifyPinTitle),
                    fontSize = 16.sp,
                    color = if (tz.pinned) colors.text else colors.textGray,
                )
                Text(stringResource(R.string.tz_notifyPinDesc), fontSize = 12.sp, color = colors.textGray)
            }
        }
    }

    if (showPicker) {
        TimezonePickerDialog(
            colors = colors,
            currentZoneId = tz.zoneId,
            onDismiss = { showPicker = false },
            onSelect = { zoneId ->
                viewModel.updateTimezoneSetting(tz.pinned, zoneId, tz.notifyPinned)
                showPicker = false
            },
        )
    }
}