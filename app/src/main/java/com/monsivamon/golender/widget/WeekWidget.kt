package com.monsivamon.golender.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
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

    val customBg = data.bgColor?.let { Color(it) }
    val backgroundColor = customBg?.let { ColorProvider(day = it, night = it) } ?: ColorProvider(day = Color(0xFFF0F2F5), night = Color(0xFF121212))

    // カスタム背景色を予定行にも適用
    val surfaceColor = customBg?.let { ColorProvider(day = it, night = it) } ?: ColorProvider(day = Color(0xFFFFFFFF), night = Color(0xFF1E1E1E))

    val textColor = ColorProvider(day = Color(0xFF1A1A1A), night = Color(0xFFF1F3F4))
    val subTextColor = ColorProvider(day = Color(0xFF888888), night = Color(0xFFAAAAAA))
    val primaryAccent = ColorProvider(day = Color(0xFF6A1B9A), night = Color(0xFFCE93D8))

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(backgroundColor)
            .clickable(onClick = actionStartActivity(Intent(context, MainActivity::class.java)))
            .padding(12.dp)
    ) {
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "${weekStart.monthValue}/${weekStart.dayOfMonth} 〜 ${weekEnd.monthValue}/${weekEnd.dayOfMonth}",
                style = TextStyle(color = textColor, fontSize = 15.sp, fontWeight = FontWeight.Bold),
                modifier = GlanceModifier.defaultWeight()
            )
            Text(
                text = "🔄",
                style = TextStyle(fontSize = 14.sp),
                modifier = GlanceModifier.padding(4.dp).clickable(onClick = actionRunCallback<WidgetUpdateAction>())
            )
        }

        Spacer(modifier = GlanceModifier.height(8.dp))

        val weekDays = (0..6).map { weekStart.plusDays(it.toLong()) }
        val hasEvents = events.values.any { it.isNotEmpty() }

        if (!hasEvents) {
            Text(text = "予定なし", style = TextStyle(color = subTextColor, fontSize = 14.sp))
        } else {
            // 週の全日（7日間）を表示
            weekDays.forEach { date ->
                val dayEvents = events[date] ?: emptyList()
                val dayOfWeek = date.dayOfWeek.getDisplayName(FULL, Locale.JAPANESE).take(1)

                val hasEvent = dayEvents.isNotEmpty()
                val eventTitle = if (hasEvent) dayEvents.first().title else "―"

                Row(
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .padding(bottom = 4.dp)
                        .background(surfaceColor)
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${date.monthValue}/${date.dayOfMonth}($dayOfWeek)",
                        style = TextStyle(
                            color = if (hasEvent) textColor else subTextColor,
                            fontSize = 12.sp,
                            fontWeight = if (hasEvent) FontWeight.Bold else FontWeight.Normal
                        ),
                        modifier = GlanceModifier.width(56.dp)
                    )
                    if (hasEvent) {
                        Spacer(modifier = GlanceModifier.width(6.dp))
                        Box(modifier = GlanceModifier.width(3.dp).height(12.dp).background(primaryAccent)) {}
                    }
                    Spacer(modifier = GlanceModifier.width(6.dp))
                    Text(
                        text = eventTitle,
                        style = TextStyle(
                            color = if (hasEvent) textColor else subTextColor,
                            fontSize = 12.sp
                        ),
                        maxLines = 1,
                        modifier = GlanceModifier.defaultWeight()
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