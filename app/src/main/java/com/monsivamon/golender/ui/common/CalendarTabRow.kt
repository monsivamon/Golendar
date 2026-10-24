package com.monsivamon.golender.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.monsivamon.golender.R
import com.monsivamon.golender.ui.Routes
import com.monsivamon.golender.ui.navigateTab
import com.monsivamon.golender.ui.navigateToTab
import com.monsivamon.golender.ui.tabOrder
import com.monsivamon.golender.ui.theme.AppColors

// 日・週・月（＋アルバム）の表示切り替えタブと前後移動ボタンを描画する
@Composable
fun CalendarTabRow(
    currentRoute: String,
    colors: AppColors,
    navController: NavController,
    tabs: List<String> = tabOrder,
) {
    // タブが 4 つ以上になると幅が厳しいのでコンパクト表示に切り替える
    val compact = tabs.size > 3
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 前のタブへ循環移動
        CompactIconButton(onClick = { navigateTab(navController, currentRoute, -1, tabs) }) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, stringResource(R.string.co_prev), tint = colors.text)
        }
        tabs.forEach { route ->
            TabLabel(tabLabel(route), route, currentRoute, colors, navController, compact)
        }
        // 次のタブへ循環移動
        CompactIconButton(onClick = { navigateTab(navController, currentRoute, 1, tabs) }) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(R.string.co_next), tint = colors.text)
        }
    }
}

// ルートに対応するタブ文言を返す
@Composable
private fun tabLabel(route: String): String = when (route) {
    Routes.DAILY -> stringResource(R.string.co_tab_daily)
    Routes.WEEKLY -> stringResource(R.string.co_tab_weekly)
    Routes.MONTHLY -> stringResource(R.string.co_tab_monthly)
    Routes.ALBUM -> stringResource(R.string.al_albumTitle)
    else -> route
}

// タブのラベル（選択中はアクセントカラーの背景で強調）を描画する
@Composable
private fun TabLabel(
    label: String, route: String, currentRoute: String,
    colors: AppColors, navController: NavController, compact: Boolean,
) {
    val fontSize = if (compact) 14.sp else 16.sp
    if (route == currentRoute) {
        // 選択中のタブは強調表示
        Box(
            Modifier.clip(RoundedCornerShape(20.dp)).background(colors.primaryAccent)
                .padding(horizontal = if (compact) 12.dp else 24.dp, vertical = 6.dp)
        ) {
            Text(label, fontSize = fontSize, fontWeight = FontWeight.Bold, color = Color.White)
        }
    } else {
        // 非選択タブはタップで遷移
        Text(
            text = label, fontSize = fontSize, color = colors.text,
            modifier = Modifier
                .clickable { navigateToTab(navController, route) }
                .padding(if (compact) 6.dp else 8.dp),
        )
    }
}
