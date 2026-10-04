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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.monsivamon.golender.data.SelectedCalendar
import com.monsivamon.golender.ui.theme.AppColors

// Google モードで複数カレンダーが可視のとき、追加先カレンダーを選択する UI
@Composable
internal fun CalendarSelector(
    calendars: List<SelectedCalendar>,
    selectedId: Long?,
    colors: AppColors,
    onSelect: (Long) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = calendars.firstOrNull { it.calendarId == selectedId } ?: calendars.firstOrNull()

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
                selected?.let {
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(if (it.colorArgb == 0) Color.Gray else Color(it.colorArgb))
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    selected?.displayName ?: "カレンダーを選択",
                    color = colors.text,
                    fontSize = 14.sp,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    tint = colors.textGray,
                )
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                calendars.forEach { cal ->
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clip(CircleShape)
                                        .background(if (cal.colorArgb == 0) Color.Gray else Color(cal.colorArgb))
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    cal.displayName.ifBlank { "名称未設定" },
                                    color = colors.text,
                                    fontSize = 14.sp,
                                )
                            }
                        },
                        onClick = {
                            onSelect(cal.calendarId)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}