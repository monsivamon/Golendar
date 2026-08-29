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
    val events = data.events.take(5)

    val customBg = data.bgColor?.let { Color(it) }
    val backgroundColor = customBg?.let { ColorProvider(day = it, night = it) } ?: ColorProvider(day = Color(0xFFF0F2F5), night = Color(0xFF121212))

    // カスタム背景色を予定カードにも反映
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
        val dayOfWeek = date.dayOfWeek.getDisplayName(FULL, Locale.JAPANESE)

        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(text = "${date.monthValue}/${date.dayOfMonth} ($dayOfWeek)", style = TextStyle(color = textColor, fontSize = 16.sp, fontWeight = FontWeight.Bold), modifier = GlanceModifier.defaultWeight())
            Text(text = "🔄", style = TextStyle(fontSize = 14.sp), modifier = GlanceModifier.padding(4.dp).clickable(onClick = actionRunCallback<WidgetUpdateAction>()))
        }

        Spacer(modifier = GlanceModifier.height(8.dp))

        if (events.isEmpty()) {
            Text(text = "予定なし", style = TextStyle(color = subTextColor, fontSize = 14.sp))
        } else {
            events.forEach { event ->
                Row(
                    modifier = GlanceModifier.fillMaxWidth().padding(bottom = 6.dp).background(surfaceColor).padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = GlanceModifier.width(4.dp).height(16.dp).background(primaryAccent)) {}
                    Spacer(modifier = GlanceModifier.width(8.dp))
                    Text(text = event.title, style = TextStyle(color = textColor, fontSize = 14.sp), maxLines = 1, modifier = GlanceModifier.defaultWeight())
                }
            }
        }
    }
}

// DayWidget のレシーバー
class DayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = DayWidget()
}