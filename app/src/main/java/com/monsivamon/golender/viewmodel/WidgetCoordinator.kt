package com.monsivamon.golender.viewmodel

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import com.monsivamon.golender.widget.DayWidget
import com.monsivamon.golender.widget.MonthWidget
import com.monsivamon.golender.widget.WeekWidget

// 日・週・月の 3 種類のウィジェットを一括更新する責務を担う
class WidgetCoordinator(private val appContext: Context) {

    // 全ウィジェットを更新する（例外は握りつぶす）
    suspend fun updateAll() {
        try {
            listOf(DayWidget(), WeekWidget(), MonthWidget()).forEach { widget ->
                GlanceAppWidgetManager(appContext)
                    .getGlanceIds(widget::class.java)
                    .forEach { id -> widget.update(appContext, id) }
            }
        } catch (_: Exception) { }
    }
}