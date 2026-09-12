package com.monsivamon.golender.widget

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.monsivamon.golender.MainActivity
import com.monsivamon.golender.R
import com.monsivamon.golender.ui.Routes

// 全ウィジェット共通のヘッダー行（タイトル＋更新＋設定ボタン）
@Composable
fun WidgetHeader(
    title: String,
    colors: WidgetColorSet,
    titleFontSize: Int = 16,
) {
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = TextStyle(
                color = colors.text,
                fontSize = titleFontSize.sp,
                fontWeight = FontWeight.Bold,
            ),
            modifier = GlanceModifier.defaultWeight(),
        )
        WidgetRefreshButton(colors = colors)
        WidgetSettingsButton(colors = colors)
    }
}

// ウィジェット更新ボタン（タップで全ウィジェットを再描画）
@Composable
fun WidgetRefreshButton(
    colors: WidgetColorSet,
    touchSize: Int = 32,
    iconSize: Int = 18,
) {
    Box(
        modifier = GlanceModifier
            .size(touchSize.dp)
            .clickable(onClick = actionRunCallback<WidgetUpdateAction>()),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            provider = ImageProvider(R.drawable.ic_widget_refresh),
            contentDescription = "更新",
            colorFilter = ColorFilter.tint(colors.text),
            modifier = GlanceModifier.size(iconSize.dp),
        )
    }
}

// ウィジェット設定ボタン（タップでアプリの設定画面を開く）
@Composable
fun WidgetSettingsButton(
    colors: WidgetColorSet,
    touchSize: Int = 32,
    iconSize: Int = 18,
) {
    val context = LocalContext.current
    val openSettingsIntent = Intent(context, MainActivity::class.java).apply {
        putExtra(MainActivity.EXTRA_ROUTE, Routes.SETTINGS)
    }

    Box(
        modifier = GlanceModifier
            .size(touchSize.dp)
            .clickable(onClick = actionStartActivity(openSettingsIntent)),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            provider = ImageProvider(R.drawable.ic_widget_settings),
            contentDescription = "設定",
            colorFilter = ColorFilter.tint(colors.text),
            modifier = GlanceModifier.size(iconSize.dp),
        )
    }
}