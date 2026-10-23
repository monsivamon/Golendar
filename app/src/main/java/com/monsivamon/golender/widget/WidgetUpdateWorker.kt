package com.monsivamon.golender.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.first
import com.monsivamon.golender.data.dataStore
import com.monsivamon.golender.data.CalendarRepository
import com.monsivamon.golender.data.holiday.HolidayCountry
import com.monsivamon.golender.data.holiday.HolidayCountryJson
import com.monsivamon.golender.data.prefs.LanguageSetting
import com.monsivamon.golender.data.prefs.SettingsKeys
import com.monsivamon.golender.notification.NotificationScheduler

// バックグラウンドで定期的にウィジェットを更新し、祝日データも 30 日ごとに更新するワーカー
class WidgetUpdateWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    // バックグラウンド処理を実行する
    override suspend fun doWork(): Result {
        return withContext(Dispatchers.IO) {
            try {
                val context = applicationContext

                // 祝日データを 30 日ごと、または選択国が変わったときに更新する
                val prefs = context.dataStore.data.first()
                val lastFetch = prefs[SettingsKeys.LAST_HOLIDAY_FETCH] ?: 0L
                val now = System.currentTimeMillis()
                val thirtyDays = 30L * 24 * 60 * 60 * 1000L

                val countries = HolidayCountry.fromIsoList(
                    HolidayCountryJson.fromJson(prefs[SettingsKeys.HOLIDAY_COUNTRIES])
                        ?: listOf(HolidayCountry.JP.iso)
                )
                val selectedIso = countries.map { it.iso }.sorted()
                val lastCountries = (
                    HolidayCountryJson.fromJson(prefs[SettingsKeys.LAST_HOLIDAY_FETCH_COUNTRIES])
                        ?: emptyList()
                    ).sorted()

                if (selectedIso != lastCountries || now - lastFetch > thirtyDays) {
                    val ok = CalendarRepository(context).refreshHolidays(countries)
                    if (ok) {
                        context.dataStore.edit {
                            it[SettingsKeys.LAST_HOLIDAY_FETCH] = now
                            it[SettingsKeys.LAST_HOLIDAY_FETCH_COUNTRIES] =
                                HolidayCountryJson.toJson(selectedIso)
                        }
                    }
                }

                // 全ウィジェット（日・週・月）を強制更新する
                // （アプリ内言語設定を反映した locale Context を渡す）
                val widgets = listOf(DayWidget(), WeekWidget(), MonthWidget())
                widgets.forEach { widget ->
                    val manager = GlanceAppWidgetManager(context)
                    val glanceIds = manager.getGlanceIds(widget::class.java)
                    glanceIds.forEach { id ->
                        widget.update(LanguageSetting.wrap(context), id)
                    }
                }
                // 祝日データ更新後に古い通知アラームが残り続けないよう、
                // 最新の予定に合わせて再スケジュールする
                NotificationScheduler.updateAlarms(context)

                Result.success()
            } catch (_: Exception) {
                // 失敗時はリトライを要求する
                Result.retry()
            }
        }
    }

    companion object {
        const val WORK_NAME = "widget_update_worker"
    }
}