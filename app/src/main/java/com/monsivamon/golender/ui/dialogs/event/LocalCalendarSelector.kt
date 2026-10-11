package com.monsivamon.golender.ui.dialogs.event

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.monsivamon.golender.data.LocalCalendar
import com.monsivamon.golender.ui.theme.AppColors
import com.monsivamon.golender.ui.theme.resolveCalendarColor

// Golendar モードで複数の可視ローカルカレンダーがあるとき、追加先を選ぶ UI。
// 色が未設定(0) のカレンダーは、リスト内のインデックスに応じて自動色を表示する。
@Composable
internal fun LocalCalendarSelector(
    calendars: List<LocalCalendar>,
    selectedId: Long?,
    colors: AppColors,
    onSelect: (Long) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = calendars.firstOrNull { it.id == selectedId } ?: calendars.firstOrNull()
    val selectedIndex = calendars.indexOfFirst { it.id == selected?.id }.coerceAtLeast(0)
    val selectedColor = selected?.let { colors.resolveCalendarColor(it.colorArgb, selectedIndex) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            "追加先カレンダー",
            color = colors.textGray,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        Box {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .border(1.dp, colors.textGray, RoundedCornerShape(8.dp))
                    .clickable { expanded = true }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                selectedColor?.let {
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(it)
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    selected?.name ?: "カレンダーを選択",
                    color = colors.text,
                    fontSize = 14.sp,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Icon(imageVector = Icons.Default.ArrowDropDown, contentDescription = null, tint = colors.textGray)
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                // Material3 の DropdownMenu は MaterialTheme.colorScheme から
                // 背景色を取得する。本アプリは MaterialTheme を被せていないため
                // 既定でライト固定の白背景になり、ダークモード時に
                // colors.text（ほぼ白）と衝突して文字が見えなくなる。
                // アプリの surface 色を明示して回避する。
                containerColor = colors.surface,
            ) {
                calendars.forEachIndexed { index, cal ->
                    val displayColor = colors.resolveCalendarColor(cal.colorArgb, index)
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clip(CircleShape)
                                        .background(displayColor)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(cal.name, color = colors.text, fontSize = 14.sp)
                            }
                        },
                        onClick = {
                            onSelect(cal.id)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}