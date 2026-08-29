package com.monsivamon.golender.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.monsivamon.golender.data.CalendarRepository
import com.monsivamon.golender.data.Event
import com.monsivamon.golender.data.dataStore
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.ZoneId

object NotificationScheduler {
    private val scheduledIntents = mutableListOf<PendingIntent>()

    // アラームを全解除し、最新データに基づいて再スケジュールする
    suspend fun updateAlarms(context: Context) {
        val prefs = context.dataStore.data.first()
        val notifyAtStart = prefs[booleanPreferencesKey("notify_at_start")] ?: true
        val notify10MinBefore = prefs[booleanPreferencesKey("notify_10min_before")] ?: true

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        // 正確なアラーム権限がなければ処理を中断
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            return
        }

        // 既存のアラームを全てクリア
        scheduledIntents.forEach { alarmManager.cancel(it) }
        scheduledIntents.clear()

        // 通知設定が全てオフの場合は終了
        if (!notifyAtStart && !notify10MinBefore) return

        val repo = CalendarRepository(context)
        val mode = prefs[stringPreferencesKey("calendar_mode")] ?: "GOLENDAR"
        val account = prefs[stringPreferencesKey("selected_account")]

        // 今日から3ヶ月分の予定を取得
        val startMillis = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val endMillis = LocalDate.now().plusMonths(3).atTime(23, 59, 59).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        val events = if (mode == "GOOGLE") {
            val calendarIds = account?.let { repo.getCalendarIdsForAccount(it) }
            repo.getEventsForMonth(startMillis, endMillis, calendarIds)
        } else {
            repo.getLocalEventsForMonth(startMillis, endMillis)
        }

        val now = System.currentTimeMillis()

        // 過去予定を除外し、直近50件までに制限
        val futureEvents = events.filter { it.startTime > now - (10 * 60 * 1000) }
            .sortedBy { it.startTime }
            .take(50)

        // 各予定に対し、10分前と開始時刻の通知を設定
        for (event in futureEvents) {
            if (notify10MinBefore) {
                val time10MinBefore = event.startTime - (10 * 60 * 1000)
                if (time10MinBefore > now) {
                    scheduleExactAlarm(context, alarmManager, event, time10MinBefore, true)
                }
            }

            if (notifyAtStart) {
                if (event.startTime > now) {
                    scheduleExactAlarm(context, alarmManager, event, event.startTime, false)
                }
            }
        }
    }

    // 個別のアラームをAlarmManagerに登録
    private fun scheduleExactAlarm(
        context: Context,
        alarmManager: AlarmManager,
        event: Event,
        triggerTime: Long,
        is10MinBefore: Boolean
    ) {
        val intent = Intent(context, NotificationReceiver::class.java).apply {
            val prefix = if (is10MinBefore) "[10分前] " else ""
            putExtra("EXTRA_TITLE", prefix + event.title)
            putExtra("EXTRA_MESSAGE", event.description)
            putExtra("EXTRA_ID", event.id.toInt() + if (is10MinBefore) 100000 else 0)
        }

        val requestCode = event.id.toInt() * 10 + (if (is10MinBefore) 1 else 0)

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerTime,
                pendingIntent
            )
            scheduledIntents.add(pendingIntent)
        } catch (e: SecurityException) {
            // 権限不足などは無視
        }
    }
}