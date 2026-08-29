package com.monsivamon.golender.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.action.ActionCallback

// ウィジェット上の「🔄」ボタン押下時に全ウィジェットを強制更新する
class WidgetUpdateAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val widgets = listOf(DayWidget(), WeekWidget(), MonthWidget())
        widgets.forEach { widget ->
            try {
                val manager = GlanceAppWidgetManager(context)
                val glanceIds = manager.getGlanceIds(widget::class.java)
                glanceIds.forEach { id ->
                    widget.update(context, id)
                }
            } catch (_: Exception) {
                // エラー時はスキップ
            }
        }
    }
}