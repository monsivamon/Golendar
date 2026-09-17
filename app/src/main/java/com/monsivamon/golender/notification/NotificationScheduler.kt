package com.monsivamon.golender.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.monsivamon.golender.data.CalendarRepository
import com.monsivamon.golender.data.Event
import com.monsivamon.golender.data.dataStore
import com.monsivamon.golender.data.prefs.SettingsKeys
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.time.ZoneId

// 予定のリマインダー通知をAlarmManagerに登録・再登録するスケジューラ。
object NotificationScheduler {
    private val scheduledIntents = mutableListOf<PendingIntent>()
    private val mutex = Mutex()

    // 既存アラームを全解除し、最新データに基づいて再スケジュールする。
    suspend fun updateAlarms(context: Context) {
        mutex.withLock {
            val prefs = context.dataStore.data.first()
            val notifyAtStart = prefs[SettingsKeys.NOTIFY_AT_START] ?: true
            val notify10MinBefore = prefs[SettingsKeys.NOTIFY_10MIN] ?: true

            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                return@withLock
            }

            val snapshot = scheduledIntents.toList()
            snapshot.forEach { alarmManager.cancel(it) }
            scheduledIntents.clear()

            if (!notifyAtStart && !notify10MinBefore) return@withLock

            val repo = CalendarRepository(context)
            val mode = prefs[SettingsKeys.MODE] ?: "GOLENDAR"
            val account = prefs[SettingsKeys.ACCOUNT]

            val nowDate = LocalDate.now()
            val startMillis = nowDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val endMillis = nowDate.plusMonths(NotificationConfig.LOOKAHEAD_MONTHS)
                .atTime(23, 59, 59).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

            val events = if (mode == "GOOGLE") {
                val calendarIds = account?.let { repo.getCalendarIdsForAccount(it) }
                repo.getEventsForMonth(startMillis, endMillis, calendarIds)
            } else {
                repo.getLocalEventsForMonth(startMillis, endMillis)
            }

            val now = System.currentTimeMillis()

            val futureEvents = events
                .filter { it.startTime > now - NotificationConfig.TEN_MIN_BEFORE_MS }
                .sortedBy { it.startTime }
                .take(NotificationConfig.MAX_SCHEDULED)

            for (event in futureEvents) {
                if (notify10MinBefore) {
                    val t = event.startTime - NotificationConfig.TEN_MIN_BEFORE_MS
                    if (t > now) scheduleExactAlarm(context, alarmManager, event, t, true)
                }
                if (notifyAtStart && event.startTime > now) {
                    scheduleExactAlarm(context, alarmManager, event, event.startTime, false)
                }
            }
        }
    }

    // 個別のアラームをAlarmManagerに正確アラームとして登録する。
    private fun scheduleExactAlarm(
        context: Context,
        alarmManager: AlarmManager,
        event: Event,
        triggerTime: Long,
        is10MinBefore: Boolean,
    ) {
        val intent = Intent(context, NotificationReceiver::class.java).apply {
            val prefix = if (is10MinBefore) "[10分前] " else ""
            putExtra(NotificationConfig.EXTRA_TITLE, prefix + event.title)
            putExtra(NotificationConfig.EXTRA_MESSAGE, event.description)
            putExtra(
                NotificationConfig.EXTRA_ID,
                event.id.toInt() + if (is10MinBefore) NotificationConfig.TEN_MIN_ID_OFFSET else 0,
            )
        }

        val requestCode = event.id.toInt() * 10 + if (is10MinBefore) 1 else 0

        val pendingIntent = PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        try {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent,
            )
            scheduledIntents.add(pendingIntent)
        } catch (_: SecurityException) {
            // 権限不足などは無視
        }
    }
}