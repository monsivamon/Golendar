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

// 週次表示ウィジェット（週間予定を表示）
class WeekWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = WidgetDataManager.getWeekWidgetData(context)
        provideContent { WeekWidgetContent(data) }
    }
}

@Composable
fun WeekWidgetContent(data: WeekWidgetData) {
    val context = LocalContext.current
    val weekStart = data.weekStart
    val weekEnd = data.weekEnd
    val events = data.events

    // テーマ・背景色からウィジェット用の色セットを取得
    val wc = computeWidgetColors(data.themeMode, data.bgColor)

    // タップ時に週表示画面を直接開くIntent
    val openAppIntent = Intent(context, MainActivity::class.java).apply {
        putExtra(MainActivity.EXTRA_ROUTE, Routes.WEEKLY)
    }

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(wc.bg)
            .clickable(onClick = actionStartActivity(openAppIntent))
            .padding(12.dp),
    ) {
        // 共通ヘッダー（タイトル＋更新＋設定）
        WidgetHeader(
            title = "${weekStart.monthValue}/${weekStart.dayOfMonth} 〜 ${weekEnd.monthValue}/${weekEnd.dayOfMonth}",
            colors = wc,
            titleFontSize = 15,
        )

        Spacer(modifier = GlanceModifier.height(4.dp))

        val weekDays = (0..6).map { weekStart.plusDays(it.toLong()) }
        val hasEvents = events.values.any { it.isNotEmpty() }

        if (!hasEvents) {
            Text(text = "予定なし", style = TextStyle(color = wc.textGray, fontSize = 14.sp))
        } else {
            // 週の各日ごとに最初の予定を1件表示
            weekDays.forEach { date ->
                val dayEvents = events[date] ?: emptyList()
                val dayOfWeek = date.dayOfWeek.getDisplayName(FULL, Locale.JAPANESE).take(1)

                val hasEvent = dayEvents.isNotEmpty()
                val eventTitle = if (hasEvent) dayEvents.first().title else "―"

                Row(
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .defaultWeight()
                        .padding(bottom = 4.dp)
                        .background(wc.surface)
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${date.monthValue}/${date.dayOfMonth}($dayOfWeek)",
                        style = TextStyle(
                            color = if (hasEvent) wc.text else wc.textGray,
                            fontSize = 12.sp,
                            fontWeight = if (hasEvent) FontWeight.Bold else FontWeight.Normal,
                        ),
                        modifier = GlanceModifier.width(56.dp),
                    )
                    // 予定がある日はアクセントラインを表示
                    if (hasEvent) {
                        Spacer(modifier = GlanceModifier.width(6.dp))
                        Box(modifier = GlanceModifier.width(3.dp).height(12.dp).background(wc.primaryAccent)) {}
                    }
                    Spacer(modifier = GlanceModifier.width(6.dp))
                    Text(
                        text = eventTitle,
                        style = TextStyle(
                            color = if (hasEvent) wc.text else wc.textGray,
                            fontSize = 12.sp,
                        ),
                        maxLines = 1,
                        modifier = GlanceModifier.defaultWeight(),
                    )
                }
            }
        }
    }
}

// WeekWidget のレシーバー
class WeekWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WeekWidget()
}