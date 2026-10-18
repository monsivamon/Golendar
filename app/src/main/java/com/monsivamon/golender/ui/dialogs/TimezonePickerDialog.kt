package com.monsivamon.golender.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.monsivamon.golender.R
import com.monsivamon.golender.data.prefs.AppTimeZone
import com.monsivamon.golender.ui.theme.AppColors

// IANA ゾーン1件分の表示用データ（zoneLabel の計算を1度だけ行うためのキャッシュ）
private data class ZoneItem(val id: String, val label: String)

// 全世界のタイムゾーンから表示基準を選ぶダイアログ。
// 検索欄は IANA ID（Asia/Tokyo 等）とラベル（UTC+09:00 等）の双方にヒットする。
@Composable
fun TimezonePickerDialog(
    colors: AppColors,
    currentZoneId: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    // 全世界のゾーンを「UTC オフセット順→ID 順」で列挙する。
    // zoneLabel の計算は remember 内で1度だけ行い、スクロール時の再計算を防ぐ。
    val zones = remember {
        AppTimeZone.allZoneIdsSorted().map { ZoneItem(it, AppTimeZone.zoneLabel(it)) }
    }
    val filtered = remember(query, zones) {
        if (query.isBlank()) zones
        else zones.filter {
            it.id.contains(query, ignoreCase = true) ||
                it.label.contains(query, ignoreCase = true)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            color = colors.surface,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.82f)
                .padding(vertical = 24.dp),
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.tz_pickerTitle),
                        color = colors.text, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                    )
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.tz_close), color = colors.textGray)
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text(stringResource(R.string.tz_searchHint), color = colors.textGray) },
                    leadingIcon = { Icon(Icons.Default.Search, null, tint = colors.textGray) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(Icons.Default.Close, null, tint = colors.textGray)
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = colors.text, unfocusedTextColor = colors.text,
                        focusedBorderColor = colors.primaryAccent, unfocusedBorderColor = colors.textGray,
                        cursorColor = colors.primaryAccent,
                    ),
                )
                Spacer(Modifier.height(8.dp))
                if (filtered.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.tz_noResults), color = colors.textGray)
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(filtered, key = { it.id }) { zone ->
                            val selected = zone.id == currentZoneId
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (selected) colors.primaryAccent.copy(alpha = 0.15f)
                                        else colors.surface
                                    )
                                    .clickable { onSelect(zone.id) }
                                    .padding(horizontal = 12.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = zone.label,
                                    color = if (selected) colors.primaryAccent else colors.text,
                                    fontSize = 14.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                    modifier = Modifier.weight(1f),
                                )
                                if (selected) {
                                    Text("✓", color = colors.primaryAccent, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}