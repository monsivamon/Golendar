package com.monsivamon.golender.ui.sections

import android.widget.Toast
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

// カレンダーモード切替と、モード別のカレンダー管理 UI を表示するセクション。
// Google モード時は Google カレンダー一覧、Golendar モード時はローカルカレンダー管理を表示する。
@Composable
fun CalendarModeSection(
    viewModel: CalendarViewModel,
    colors: AppColors,
    onPermissionNeeded: () -> Unit,
    onNoAccounts: () -> Unit,
    onColorPickerRequested: (SelectedCalendar) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val calendarMode by viewModel.calendarMode.collectAsState()
    val selectedCalendars by viewModel.selectedCalendars.collectAsState()
    // 「最低1つは表示」ルール用: 現在の可視カレンダー数
    val visibleGoogleCalendarCount = selectedCalendars.count { it.isVisible }

    SettingsSection("カレンダーモード", colors, onExpand = { viewModel.onCalendarModeSectionExpanded() }) {
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
                            // 1) まずカレンダー読み書き権限の有無を確認する。
                            //    権限が無いと fetchAccountNames() が SecurityException を
                            //    握りつぶして空リストを返し、「アカウントなし」と誤判定するため、
                            //    必ず権限チェックを先に行う。
                            val hasRead = ContextCompat.checkSelfPermission(
                                context, android.Manifest.permission.READ_CALENDAR,
                            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                            val hasWrite = ContextCompat.checkSelfPermission(
                                context, android.Manifest.permission.WRITE_CALENDAR,
                            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                            if (!hasRead || !hasWrite) {
                                // 2) 権限が無ければ許可を求めるダイアログを表示する
                                onPermissionNeeded()
                                return@launch
                            }
                            // 3) 権限が揃ったのでアカウントの有無を確認する
                            val accounts = viewModel.fetchAccountNames()
                            if (accounts.isEmpty()) {
                                onNoAccounts()
                                return@launch
                            }
                            // 4) 再起動なしで即座に切り替える
                            if (calendarMode != CalendarMode.GOOGLE) {
                                viewModel.setCalendarMode(CalendarMode.GOOGLE)
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
        // モードの説明文（短く）
        Text(
            if (calendarMode == CalendarMode.GOLENDAR) "アプリ内のみで完結します。"
            else "Googleカレンダーと同期します。",
            fontSize = 13.sp, color = colors.textGray,
            modifier = Modifier.padding(top = 8.dp),
        )


        HorizontalDivider(color = colors.divider, modifier = Modifier.padding(vertical = 8.dp))

        // モード別のカレンダー管理 UI
        if (calendarMode == CalendarMode.GOOGLE) {
            // ---------- Google モード: Google カレンダー一覧 ----------
            val grouped = selectedCalendars.groupBy { it.accountName }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { viewModel.setAllCalendarsVisible(true) }) {
                    Text("全て表示", color = colors.primaryAccent)
                }
            }
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
                            onCheckedChange = { checked ->
                                if (!checked && cal.isVisible && visibleGoogleCalendarCount <= 1) {
                                    Toast.makeText(context, "最低1つのカレンダーを表示してください", Toast.LENGTH_SHORT).show()
                                } else {
                                    viewModel.setCalendarVisibility(cal.calendarId, checked)
                                }
                            },
                            colors = CheckboxDefaults.colors(checkedColor = colors.primaryAccent, uncheckedColor = colors.text),
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
        } else {
            // ---------- Golendar モード: ローカルカレンダー管理 ----------
            LocalCalendarSectionContent(
                viewModel = viewModel,
                colors = colors,
            )
        }
    }
}