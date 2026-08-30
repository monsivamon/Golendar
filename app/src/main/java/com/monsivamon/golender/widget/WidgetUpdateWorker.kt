package com.monsivamon.golender.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.first
import com.monsivamon.golender.data.dataStore
import com.monsivamon.golender.data.CalendarRepository

// バックグラウンドで定期的にウィジェットを更新し、祝日データも30日ごとに更新するワーカー
class WidgetUpdateWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return withContext(Dispatchers.IO) {
            try {
                val context = applicationContext

                // 祝日データを30日ごとに更新（前回取得から30日以上経過していれば実行）
                val lastFetchKey = longPreferencesKey("last_holiday_fetch_time")
                val prefs = context.dataStore.data.first()
                val lastFetch = prefs[lastFetchKey] ?: 0L
                val now = System.currentTimeMillis()
                val thirtyDays = 30L * 24 * 60 * 60 * 1000L

                if (now - lastFetch > thirtyDays) {
                    CalendarRepository(context).fetchAndSaveHolidays()
                    context.dataStore.edit { it[lastFetchKey] = now }
                }

                // 全ウィジェット（日・週・月）を強制更新
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