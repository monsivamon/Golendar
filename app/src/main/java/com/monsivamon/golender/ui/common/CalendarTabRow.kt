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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.monsivamon.golender.ui.Routes
import com.monsivamon.golender.ui.navigateTab
import com.monsivamon.golender.ui.navigateToTab
import com.monsivamon.golender.ui.theme.AppColors

// 日・週・月の表示切り替えタブと前後移動ボタン
@Composable
fun CalendarTabRow(currentRoute: String, colors: AppColors, navController: NavController) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompactIconButton(onClick = { navigateTab(navController, currentRoute, -1) }) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "前へ", tint = colors.text)
        }
        TabLabel("日", Routes.DAILY, currentRoute, colors, navController)
        TabLabel("週", Routes.WEEKLY, currentRoute, colors, navController)
        TabLabel("月", Routes.MONTHLY, currentRoute, colors, navController)
        CompactIconButton(onClick = { navigateTab(navController, currentRoute, 1) }) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "次へ", tint = colors.text)
        }
    }
}

// タブのラベル（選択中はアクセントカラーの背景で強調）
@Composable
private fun TabLabel(
    label: String, route: String, currentRoute: String,
    colors: AppColors, navController: NavController,
) {
    if (route == currentRoute) {
        Box(
            Modifier.clip(RoundedCornerShape(20.dp)).background(colors.primaryAccent)
                .padding(horizontal = 24.dp, vertical = 6.dp)
        ) {
            Text(label, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
    } else {
        Text(
            text = label, fontSize = 16.sp, color = colors.text,
            modifier = Modifier.clickable { navigateToTab(navController, route) }.padding(8.dp),
        )
    }
}