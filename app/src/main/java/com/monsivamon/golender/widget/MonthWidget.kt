package com.monsivamon.golender.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.unit.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import com.monsivamon.golender.MainActivity
import com.monsivamon.golender.R
import com.monsivamon.golender.data.prefs.LanguageSetting
import com.monsivamon.golender.ui.Routes
import java.time.DayOfWeek

// 月次表示ウィジェット（月間カレンダーグリッド）
class MonthWidget : GlanceAppWidget() {
    // ウィジェットの表示内容を構築する
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // Glance の描画は WorkManager 内部 Context（システムロケール）で行われるため、
        // アプリ内言語設定を反映した Context をローカルで差し込む
        val localized = LanguageSetting.wrap(context)
        val data = WidgetDataManager.getMonthWidgetData(localized)
        provideContent {
            CompositionLocalProvider(LocalWidgetLocalizedContext provides localized) {
                MonthWidgetContent(data)
            }
        }
    }
}

// 月次ウィジェットの内容を描画する
@Composable
fun MonthWidgetContent(data: MonthWidgetData) {
    // 表示に必要なパラメータを取得する
    val context = LocalWidgetLocalizedContext.current
    val yearMonth = data.yearMonth
    val events = data.events
    val weekStartDay = data.weekStartDay

    // 月初の曜日オフセットを計算する
    val firstDayOfMonth = yearMonth.atDay(1)
    val daysInMonth = yearMonth.lengthOfMonth()
    val offset = (firstDayOfMonth.dayOfWeek.value - weekStartDay.value + 7) % 7

    // テーマ・背景色からウィジェット用の色セットを取得する
    val wc = computeWidgetColors(data.themeMode, data.bgColor)

    // タップ時に月表示画面を直接開く Intent
    val openAppIntent = Intent(context, MainActivity::class.java).apply {
        putExtra(MainActivity.EXTRA_ROUTE, Routes.MONTHLY)
    }

    // 外枠（背景色＋タップ領域）を描画する
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(wc.bg)
            .clickable(onClick = actionStartActivity(openAppIntent))
            .padding(8.dp),
    ) {
        // 内側パネル（surface 色の枠）
        Column(modifier = GlanceModifier.fillMaxSize().background(wc.surface).padding(12.dp)) {
            // 共通ヘッダー（タイトル＋更新＋設定）
            WidgetHeader(title = context.getString(R.string.wd_widgetMonthTitle, yearMonth.year, yearMonth.monthValue), colors = wc)

            Spacer(modifier = GlanceModifier.height(4.dp))

            // 曜日ヘッダー（週の始まり設定に応じて並び替え）
            val weekdayPairs = listOf(
                DayOfWeek.SUNDAY to context.getString(R.string.wd_weekDaySun),
                DayOfWeek.MONDAY to context.getString(R.string.wd_weekDayMon),
                DayOfWeek.TUESDAY to context.getString(R.string.wd_weekDayTue),
                DayOfWeek.WEDNESDAY to context.getString(R.string.wd_weekDayWed),
                DayOfWeek.THURSDAY to context.getString(R.string.wd_weekDayThu),
                DayOfWeek.FRIDAY to context.getString(R.string.wd_weekDayFri),
                DayOfWeek.SATURDAY to context.getString(R.string.wd_weekDaySat),
            )
            val startIndex = if (weekStartDay == DayOfWeek.MONDAY) 1 else 0
            val orderedWeekDays = weekdayPairs.drop(startIndex) + weekdayPairs.take(startIndex)

            // 曜日ラベルを 1 行で描画する（アプリ内の曜日別文字色設定を反映。
            // 未設定時は日=赤・土=青、「標準」明示選択時は通常文字色）
            Row(modifier = GlanceModifier.fillMaxWidth()) {
                orderedWeekDays.forEach { (dayEnum, day) ->
                    val color = dayColorProvider(data.dayColors[dayEnum])
                        ?: when (dayEnum) {
                            DayOfWeek.SUNDAY -> wc.sunRed
                            DayOfWeek.SATURDAY -> wc.satBlue
                            else -> wc.text
                        }
                    Text(
                        text = day,
                        style = TextStyle(
                            color = color, fontSize = 11.sp,
                            fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                        ),
                        modifier = GlanceModifier.defaultWeight().padding(vertical = 4.dp),
                    )
                }
            }

            Spacer(modifier = GlanceModifier.height(4.dp))

            // 日付グリッドを描画する（前後月セルを含む週数分）
            val totalCells = ((daysInMonth + offset + 6) / 7) * 7
            val rows = totalCells / 7

            repeat(rows) { row ->
                Row(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
                    repeat(7) { col ->
                        // セル位置から実際の日付を算出する
                        val dayIndex = row * 7 + col - offset
                        val date = when {
                            dayIndex < 0 -> yearMonth.minusMonths(1).atEndOfMonth().plusDays((dayIndex + 1).toLong())
                            dayIndex < daysInMonth -> yearMonth.atDay(dayIndex + 1)
                            else -> yearMonth.plusMonths(1).atDay(dayIndex - daysInMonth + 1)
                        }

                        // 当月判定・予定有無・曜日を取得する
                        val isCurrentMonth = date.monthValue == yearMonth.monthValue
                        val hasEvent = events[date]?.isNotEmpty() == true
                        val dayOfWeek = date.dayOfWeek.value

                        // 当月外はグレー。当月は曜日別文字色設定を反映する
                        //（未設定時は日=赤・土=青・平日=通常、「標準」明示選択時は通常文字色）
                        val textColorForDate = if (!isCurrentMonth) {
                            wc.textGray
                        } else {
                            dayColorProvider(data.dayColors[date.dayOfWeek])
                                ?: when (dayOfWeek) {
                                    7 -> wc.sunRed
                                    6 -> wc.satBlue
                                    else -> wc.text
                                }
                        }

                        // セル 1 つ分（日付＋ドット）を描画する
                        Column(
                            modifier = GlanceModifier.defaultWeight().fillMaxHeight(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // 日付数字（予定がある当月日は太字）
                            Text(
                                text = date.dayOfMonth.toString(),
                                style = TextStyle(
                                    color = textColorForDate,
                                    fontSize = 12.sp,
                                    fontWeight = if (hasEvent && isCurrentMonth) FontWeight.Bold else FontWeight.Normal,
                                    textAlign = TextAlign.Center,
                                ),
                            )
                            // 予定がある日はドットを表示する
                            if (hasEvent) {
                                Text(
                                    text = "•",
                                    style = TextStyle(
                                        color = if (isCurrentMonth) wc.primaryAccent else wc.textGray,
                                        fontSize = 16.sp,
                                        textAlign = TextAlign.Center,
                                    ),
                                )
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