package com.monsivamon.golender.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.monsivamon.golender.data.CalendarRepository
import com.monsivamon.golender.data.Event
import com.monsivamon.golender.data.LocalEvent
import com.monsivamon.golender.data.SelectedCalendarJson
import com.monsivamon.golender.data.dataStore
import com.monsivamon.golender.data.prefs.SettingsKeys
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.time.ZoneId

// 予定のリマインダー通知を AlarmManager に登録・再登録するスケジューラ
object NotificationScheduler {
    // 登録済みの PendingIntent を保持して次回更新時に解除できるようにする
    private val scheduledIntents = mutableListOf<PendingIntent>()
    // 多重実行を防ぐためのミューテックス
    private val mutex = Mutex()

    // 既存アラームを全解除し、最新データに基づいて再スケジュールする
    suspend fun updateAlarms(context: Context) {
        mutex.withLock {
            // 通知 ON/OFF 設定を読み込む
            val prefs = context.dataStore.data.first()
            val notifyAtStart = prefs[SettingsKeys.NOTIFY_AT_START] ?: true
            val notify10MinBefore = prefs[SettingsKeys.NOTIFY_10MIN] ?: true

            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

            // Android 12+ で正確なアラーム権限が無ければ何もしない
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                return@withLock
            }

            // 既存アラームをすべて解除する
            val snapshot = scheduledIntents.toList()
            snapshot.forEach { alarmManager.cancel(it) }
            scheduledIntents.clear()

            // 通知が全て OFF なら何も登録しない
            if (!notifyAtStart && !notify10MinBefore) return@withLock

            val repo = CalendarRepository(context)
            val mode = prefs[SettingsKeys.MODE] ?: "GOLENDAR"

            // 可視カレンダー ID（Google モード時のみ使用）
            val selectedJson = prefs[SettingsKeys.SELECTED_CALENDARS]
            val visibleIds = SelectedCalendarJson.fromJson(selectedJson ?: "")
                .filter { it.isVisible }
                .map { it.calendarId }

            // 先読み期間（今日〜N ヶ月後）を求める
            val nowDate = LocalDate.now()
            val startMillis = nowDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val endMillis = nowDate.plusMonths(NotificationConfig.LOOKAHEAD_MONTHS)
                .atTime(23, 59, 59).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

            // モードに応じて対象イベントを取得する
            val events = if (mode == "GOOGLE") {
                if (visibleIds.isEmpty()) return@withLock
                // 祝日・文化イベントは除外。誕生日は天皇誕生日の祝日判定で除かれる
                repo.getEventsForMonth(startMillis, endMillis, visibleIds)
                    .filter { !it.isHolidayCalendar && !it.isCulturalEvent }
            } else {
                // Golendar モードではシステム祝日データを除外する
                repo.getLocalEventsForMonth(startMillis, endMillis)
                    .filter { it.description != LocalEvent.DESCRIPTION_HOLIDAY }
            }

            // 未来の予定のみを開始時刻順に上限件数だけ抽出する
            val now = System.currentTimeMillis()
            val futureEvents = events
                .filter { it.startTime > now - NotificationConfig.TEN_MIN_BEFORE_MS }
                .sortedBy { it.startTime }
                .take(NotificationConfig.MAX_SCHEDULED)

            // 各予定について 10 分前／定刻のアラームを登録する
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

    // 1 件分の正確なアラームを AlarmManager に登録する
    private fun scheduleExactAlarm(
        context: Context,
        alarmManager: AlarmManager,
        event: Event,
        triggerTime: Long,
        is10MinBefore: Boolean,
    ) {
        // 通知内容を Intent に詰める
        val intent = Intent(context, NotificationReceiver::class.java).apply {
            val prefix = if (is10MinBefore) "[10分前] " else ""
            putExtra(NotificationConfig.EXTRA_TITLE, prefix + event.title)
            putExtra(NotificationConfig.EXTRA_MESSAGE, event.description)
            putExtra(
                NotificationConfig.EXTRA_ID,
                event.id.toInt() + if (is10MinBefore) NotificationConfig.TEN_MIN_ID_OFFSET else 0,
            )
        }

        // イベント ID と種別から要求コードを組み立てる
        val requestCode = event.id.toInt() * 10 + if (is10MinBefore) 1 else 0

        val pendingIntent = PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        try {
            // Doze 中でも発火する正確アラームとして登録する
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent,
            )
            scheduledIntents.add(pendingIntent)
        } catch (_: SecurityException) {
            // 権限不足などは無視
        }
    }
}