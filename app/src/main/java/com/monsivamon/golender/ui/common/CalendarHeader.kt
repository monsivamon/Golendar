package com.monsivamon.golender.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.monsivamon.golender.ui.theme.AppColors
import java.time.LocalDate

// カレンダー共通ヘッダー（タイトル・検索・今日・同期・設定ボタンを表示）を描画する。
@Composable
fun CalendarHeader(
    title: String,
    colors: AppColors,
    isSearchMode: Boolean,
    searchQuery: String,
    showBottomListToggle: Boolean,
    showBottomList: Boolean,
    onTitleClick: () -> Unit,
    onTodayClick: () -> Unit,
    onToggleBottomList: () -> Unit,
    onSearchStart: () -> Unit,
    onSearchClose: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSyncClick: () -> Unit,
    onSettingsClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 0.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isSearchMode) {
            SearchBox(searchQuery, colors, onSearchQueryChange, onSearchClose, Modifier.weight(1f))
        } else {
            DateTitleWithPicker(title, colors, onTitleClick)
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (showBottomListToggle) {
                    BottomListToggleButton(
                        isOn = showBottomList,
                        colors = colors,
                        onClick = onToggleBottomList,
                    )
                    Spacer(Modifier.width(2.dp))
                }
                TodayButton(onTodayClick, colors)
                Spacer(Modifier.width(4.dp))
                CompactIconButton(onClick = onSearchStart) {
                    Icon(Icons.Default.Search, "検索", tint = colors.text)
                }
                CompactIconButton(onClick = onSyncClick) {
                    Icon(Icons.Default.Refresh, "同期", tint = colors.text)
                }
                CompactIconButton(onClick = onSettingsClick) {
                    Icon(Icons.Default.Settings, "設定", tint = colors.text)
                }
            }
        }
    }
}

// 検索テキスト入力欄と閉じるボタンを表示する。
@Composable
private fun SearchBox(
    query: String, colors: AppColors,
    onQueryChange: (String) -> Unit, onClose: () -> Unit, modifier: Modifier,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier.weight(1f).height(40.dp)
                .border(1.dp, colors.textGray, RoundedCornerShape(8.dp))
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (query.isEmpty()) Text("予定を検索...", color = colors.textGray, fontSize = 14.sp)
            BasicTextField(
                value = query, onValueChange = onQueryChange, singleLine = true,
                textStyle = TextStyle(color = colors.text, fontSize = 14.sp),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.width(4.dp))
        CompactIconButton(onClick = onClose) {
            Icon(Icons.Default.Close, "閉じる", tint = colors.textGray)
        }
    }
}

// ドロップダウンアイコン付きの日付タイトル（タップで年月選択を開く）を描画する。
@Composable
fun DateTitleWithPicker(title: String, colors: AppColors, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(4.dp),
    ) {
        Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = colors.text)
        Icon(Icons.Default.ArrowDropDown, "選択", tint = colors.primaryAccent)
    }
}

// 「今日」ボタンを描画する。
@Composable
private fun TodayButton(onClick: () -> Unit, colors: AppColors) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(bounded = false, radius = 22.dp),
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(RoundedCornerShape(4.dp))
                .border(1.5.dp, colors.text, RoundedCornerShape(4.dp)),
        ) {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxWidth().height(6.dp).background(colors.sunRed))
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        LocalDate.now().dayOfMonth.toString(), fontSize = 11.sp,
                        fontWeight = FontWeight.Bold, color = colors.text,
                        modifier = Modifier.offset(y = (-1).dp),
                    )
                }
            }
        }
    }
}

// 月表示の下部予定リストの表示切替トグルを描画する。
@Composable
private fun BottomListToggleButton(
    isOn: Boolean,
    colors: AppColors,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(bounded = false, radius = 22.dp),
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.List,
            contentDescription = if (isOn) "下部リストを非表示" else "下部リストを表示",
            tint = if (isOn) colors.primaryAccent else colors.textGray,
            modifier = Modifier.size(24.dp),
        )
    }
}