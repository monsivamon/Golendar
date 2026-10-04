package com.monsivamon.golender.ui.sections

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.monsivamon.golender.data.SelectedCalendar
import com.monsivamon.golender.ui.SettingsSection
import com.monsivamon.golender.ui.theme.AppColors
import com.monsivamon.golender.viewmodel.CalendarMode
import com.monsivamon.golender.viewmodel.CalendarViewModel
import kotlinx.coroutines.launch

// カレンダーモード切替と Google カレンダー一覧を表示するセクション
@Composable
fun CalendarModeSection(
    viewModel: CalendarViewModel,
    colors: AppColors,
    onPermissionNeeded: () -> Unit,
    onRestartNeeded: () -> Unit,
    onNoAccounts: () -> Unit,
    onColorPickerRequested: (SelectedCalendar) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val calendarMode by viewModel.calendarMode.collectAsState()
    val selectedCalendars by viewModel.selectedCalendars.collectAsState()

    SettingsSection("カレンダーモード", colors) {
        // Golendar / Google の切替ボタン（セグメント風）
        Row(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                .background(colors.surface).padding(4.dp),
        ) {
            // Golendar 側
            Box(
                modifier = Modifier.weight(1f).clip(RoundedCornerShape(6.dp))
                    .background(if (calendarMode == CalendarMode.GOLENDAR) colors.primaryAccent else Color.Transparent)
                    .clickable { viewModel.setCalendarMode(CalendarMode.GOLENDAR) }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "Golendar",
                    color = if (calendarMode == CalendarMode.GOLENDAR) Color.White else colors.text,
                    fontWeight = FontWeight.Bold,
                )
            }
            // Google 側（アカウント有無と権限を確認してから切替）
            Box(
                modifier = Modifier.weight(1f).clip(RoundedCornerShape(6.dp))
                    .background(if (calendarMode == CalendarMode.GOOGLE) colors.primaryAccent else Color.Transparent)
                    .clickable {
                        scope.launch {
                            val accounts = viewModel.fetchAccountNames()
                            if (accounts.isEmpty()) {
                                onNoAccounts()
                                return@launch
                            }
                            val hasRead = ContextCompat.checkSelfPermission(
                                context, android.Manifest.permission.READ_CALENDAR,
                            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                            val hasWrite = ContextCompat.checkSelfPermission(
                                context, android.Manifest.permission.WRITE_CALENDAR,
                            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                            when {
                                hasRead && hasWrite -> {
                                    if (calendarMode != CalendarMode.GOOGLE) onRestartNeeded()
                                }
                                else -> onPermissionNeeded()
                            }
                        }
                    }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "Google",
                    color = if (calendarMode == CalendarMode.GOOGLE) Color.White else colors.text,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        // モードの説明文
        Text(
            if (calendarMode == CalendarMode.GOLENDAR) "Googleアカウントと一切同期せず、アプリ内のみで完結します。"
            else "Googleカレンダーのシステムと同期して予定を読み書きします。",
            fontSize = 13.sp, color = colors.textGray,
            modifier = Modifier.padding(top = 8.dp, bottom = 12.dp),
        )
        // Google モード時はカレンダー一覧を表示する
        if (calendarMode == CalendarMode.GOOGLE) {
            val grouped = selectedCalendars.groupBy { it.accountName }
            // 一括表示／非表示ボタン
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { viewModel.setAllCalendarsVisible(true) }) {
                    Text("全て表示", color = colors.primaryAccent)
                }
                TextButton(onClick = { viewModel.setAllCalendarsVisible(false) }) {
                    Text("全て非表示", color = colors.primaryAccent)
                }
            }
            // アカウント単位でカレンダーを一覧表示する
            grouped.forEach { (account, cals) ->
                Text(
                    account, fontWeight = FontWeight.Bold, color = colors.textGray,
                    fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                )
                cals.forEach { cal ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    ) {
                        Checkbox(
                            checked = cal.isVisible,
                            onCheckedChange = { viewModel.setCalendarVisibility(cal.calendarId, it) },
                            colors = CheckboxDefaults.colors(checkedColor = colors.primaryAccent),
                        )
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(if (cal.colorArgb == 0) Color.Gray else Color(cal.colorArgb))
                                .clickable { onColorPickerRequested(cal) },
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(cal.displayName.ifBlank { "名称未設定" }, color = colors.text, fontSize = 15.sp)
                    }
                }
            }
            // 端末のカレンダー追加・削除に追従するための再取得ボタン
            Button(
                onClick = { viewModel.refreshAvailableCalendars() },
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.surface,
                    contentColor = colors.primaryAccent,
                ),
            ) {
                Text("カレンダー一覧を再取得")
            }
        }
    }
}