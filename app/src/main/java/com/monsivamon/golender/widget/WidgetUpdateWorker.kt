package com.monsivamon.golender.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// バックグラウンドで全ウィジェットを更新するワーカー
class WidgetUpdateWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return withContext(Dispatchers.IO) {
            try {
                val context = applicationContext
                val widgets = listOf(DayWidget(), WeekWidget(), MonthWidget())
                widgets.forEach { widget ->
                    val manager = GlanceAppWidgetManager(context)
                    val glanceIds = manager.getGlanceIds(widget::class.java)
                    glanceIds.forEach { id ->
                        widget.update(context, id)
                    }
                }
                Result.success()
            } catch (_: Exception) {
                Result.retry()
            }
        }
    }

    companion object {
        const val WORK_NAME = "widget_update_worker"
    }
}