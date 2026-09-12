package com.monsivamon.golender.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.monsivamon.golender.MainActivity
import com.monsivamon.golender.ui.Routes
import java.time.format.TextStyle.FULL
import java.util.Locale

// 日次表示ウィジェット（今日の予定を表示）
class DayWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = WidgetDataManager.getDayWidgetData(context)
        provideContent { DayWidgetContent(data) }
    }
}

@Composable
fun DayWidgetContent(data: DayWidgetData) {
    val context = LocalContext.current
    val date = data.date
    val events = data.events.take(5) // 最大5件まで表示

    // テーマ・背景色からウィジェット用の色セットを取得
    val wc = computeWidgetColors(data.themeMode, data.bgColor)

    // タップ時に日表示画面を直接開くIntent
    val openAppIntent = Intent(context, MainActivity::class.java).apply {
        putExtra(MainActivity.EXTRA_ROUTE, Routes.DAILY)
    }

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(wc.bg)
            .clickable(onClick = actionStartActivity(openAppIntent))
            .padding(12.dp),
    ) {
        val dayOfWeek = date.dayOfWeek.getDisplayName(FULL, Locale.JAPANESE)
        // 共通ヘッダー（タイトル＋更新＋設定）
        WidgetHeader(title = "${date.monthValue}/${date.dayOfMonth} ($dayOfWeek)", colors = wc)

        Spacer(modifier = GlanceModifier.height(4.dp))

        if (events.isEmpty()) {
            Text(text = "予定なし", style = TextStyle(color = wc.textGray, fontSize = 14.sp))
        } else {
            // 予定を1件ずつカード風に表示
            events.forEach { event ->
                Row(
                    modifier = GlanceModifier.fillMaxWidth().defaultWeight()
                        .padding(bottom = 6.dp)
                        .background(wc.surface).padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(modifier = GlanceModifier.width(4.dp).height(16.dp).background(wc.primaryAccent)) {}
                    Spacer(modifier = GlanceModifier.width(8.dp))
                    Text(
                        text = event.title,
                        style = TextStyle(color = wc.text, fontSize = 14.sp),
                        maxLines = 1,
                        modifier = GlanceModifier.defaultWeight(),
                    )
                }
            }
        }
    }
}

// DayWidget のレシーバー
class DayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = DayWidget()
}