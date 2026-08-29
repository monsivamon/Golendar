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
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import com.monsivamon.golender.MainActivity
import java.time.DayOfWeek

// 月次表示ウィジェット（月間カレンダーグリッド）
class MonthWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = WidgetDataManager.getMonthWidgetData(context)
        provideContent { MonthWidgetContent(data) }
    }
}

@Composable
fun MonthWidgetContent(data: MonthWidgetData) {
    val context = LocalContext.current
    val yearMonth = data.yearMonth
    val events = data.events
    val weekStartDay = data.weekStartDay

    val firstDayOfMonth = yearMonth.atDay(1)
    val daysInMonth = yearMonth.lengthOfMonth()
    val offset = (firstDayOfMonth.dayOfWeek.value - weekStartDay.value + 7) % 7

    val customBg = data.bgColor?.let { Color(it) }
    val backgroundColor = customBg?.let { ColorProvider(day = it, night = it) } ?: ColorProvider(day = Color(0xFFF0F2F5), night = Color(0xFF121212))

    // カスタム背景色を月間グリッド全体にも適用
    val surfaceColor = customBg?.let { ColorProvider(day = it, night = it) } ?: ColorProvider(day = Color(0xFFFFFFFF), night = Color(0xFF1E1E1E))

    val textColor = ColorProvider(day = Color(0xFF1A1A1A), night = Color(0xFFF1F3F4))
    val subTextColor = ColorProvider(day = Color(0xFF888888), night = Color(0xFF888888))
    val primaryAccent = ColorProvider(day = Color(0xFF6A1B9A), night = Color(0xFFCE93D8))
    val sunColor = ColorProvider(day = Color(0xFFE53935), night = Color(0xFFEF9A9A))
    val satColor = ColorProvider(day = Color(0xFF1A73E8), night = Color(0xFF81D4FA))

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(backgroundColor)
            .clickable(onClick = actionStartActivity(Intent(context, MainActivity::class.java)))
            .padding(8.dp)
    ) {
        Column(modifier = GlanceModifier.fillMaxSize().background(surfaceColor).padding(12.dp)) {
            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(text = "${yearMonth.year}年 ${yearMonth.monthValue}月", style = TextStyle(color = textColor, fontSize = 16.sp, fontWeight = FontWeight.Bold), modifier = GlanceModifier.defaultWeight())
                Text(text = "🔄", style = TextStyle(fontSize = 14.sp), modifier = GlanceModifier.padding(4.dp).clickable(onClick = actionRunCallback<WidgetUpdateAction>()))
            }

            Spacer(modifier = GlanceModifier.height(8.dp))

            // 曜日ヘッダー
            val allDays = listOf("日", "月", "火", "水", "木", "金", "土")
            val startIndex = if (weekStartDay == DayOfWeek.MONDAY) 1 else 0
            val orderedWeekDays = allDays.drop(startIndex) + allDays.take(startIndex)

            Row(modifier = GlanceModifier.fillMaxWidth()) {
                orderedWeekDays.forEachIndexed { _, day ->
                    val color = when (day) { "日" -> sunColor; "土" -> satColor; else -> textColor }
                    Text(text = day, style = TextStyle(color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center), modifier = GlanceModifier.defaultWeight().padding(vertical = 4.dp))
                }
            }

            Spacer(modifier = GlanceModifier.height(4.dp))

            // 日付グリッドを描画
            val totalCells = ((daysInMonth + offset + 6) / 7) * 7
            val rows = totalCells / 7

            repeat(rows) { row ->
                Row(modifier = GlanceModifier.fillMaxWidth()) {
                    repeat(7) { col ->
                        val dayIndex = row * 7 + col - offset
                        val date = when {
                            dayIndex < 0 -> yearMonth.minusMonths(1).atEndOfMonth().plusDays((dayIndex + 1).toLong())
                            dayIndex < daysInMonth -> yearMonth.atDay(dayIndex + 1)
                            else -> yearMonth.plusMonths(1).atDay(dayIndex - daysInMonth + 1)
                        }

                        val isCurrentMonth = date.monthValue == yearMonth.monthValue
                        val hasEvent = events[date]?.isNotEmpty() == true
                        val dayOfWeek = date.dayOfWeek.value

                        val textColorForDate = when {
                            !isCurrentMonth -> subTextColor
                            dayOfWeek == 7 -> sunColor
                            dayOfWeek == 6 -> satColor
                            else -> textColor
                        }

                        Column(modifier = GlanceModifier.defaultWeight().height(36.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalAlignment = Alignment.CenterVertically) {
                            Text(text = date.dayOfMonth.toString(), style = TextStyle(color = textColorForDate, fontSize = 12.sp, fontWeight = if (hasEvent && isCurrentMonth) FontWeight.Bold else FontWeight.Normal, textAlign = TextAlign.Center))
                            if (hasEvent) {
                                Text(text = "•", style = TextStyle(color = if (isCurrentMonth) primaryAccent else subTextColor, fontSize = 16.sp, textAlign = TextAlign.Center))
                            }
                        }
                    }
                }
            }
        }
    }
}

// MonthWidget のレシーバー
class MonthWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MonthWidget()
}