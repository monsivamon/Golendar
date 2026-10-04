package com.monsivamon.golender.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.datastore.preferences.core.edit
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

// 予定のリマインダー通知を AlarmManager に登録・再登録するスケジューラ。
// 登録済みの要求コードは DataStore に台帳として保存し、プロセスが死んでも
// 次回 updateAlarms 時に確実に解除できるようにする。
object NotificationScheduler {
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

            // 前回登録した要求コードを DataStore から読み出し、全て解除する。
            // メモリ上のリストではなく永続台帳を使うため、プロセス死後も取りこぼさない。
            val previousIds = prefs[SettingsKeys.SCHEDULED_ALARM_IDS]
                ?.split(",")
                ?.mapNotNull { it.trim().toIntOrNull() }
                ?: emptyList()
            previousIds.forEach { requestCode ->
                cancelByRequestCode(context, alarmManager, requestCode)
            }

            // 今回の登録分を蓄積する台帳
            val currentIds = mutableListOf<Int>()

            // Android 12+ で正確なアラーム権限が無ければ台帳を空にして終了
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                context.dataStore.edit { it[SettingsKeys.SCHEDULED_ALARM_IDS] = "" }
                return@withLock
            }

            // 通知が全て OFF なら台帳を空にして終了
            if (!notifyAtStart && !notify10MinBefore) {
                context.dataStore.edit { it[SettingsKeys.SCHEDULED_ALARM_IDS] = "" }
                return@withLock
            }

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
                if (visibleIds.isEmpty()) {
                    context.dataStore.edit { it[SettingsKeys.SCHEDULED_ALARM_IDS] = "" }
                    return@withLock
                }
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
                    if (t > now) {
                        val rc = scheduleExactAlarm(context, alarmManager, event, t, true)
                        if (rc >= 0) currentIds.add(rc)
                    }
                }
                if (notifyAtStart && event.startTime > now) {
                    val rc = scheduleExactAlarm(context, alarmManager, event, event.startTime, false)
                    if (rc >= 0) currentIds.add(rc)
                }
            }

            // 今回登録した要求コードを台帳へ永続化する
            context.dataStore.edit { it[SettingsKeys.SCHEDULED_ALARM_IDS] = currentIds.joinToString(",") }
        }
    }

    // 要求コードから PendingIntent を復元してアラームを解除する。
    // FLAG_NO_CREATE により、存在しない場合は null が返るので安全。
    private fun cancelByRequestCode(
        context: Context,
        alarmManager: AlarmManager,
        requestCode: Int,
    ) {
        try {
            val intent = Intent(context, NotificationReceiver::class.java)
            val pi = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
            ) ?: return
            alarmManager.cancel(pi)
            pi.cancel()
        } catch (_: Exception) {
            // 無効な要求コードなどは無視
        }
    }

    // 1 件分の正確なアラームを登録し、要求コードを返す（失敗時は -1）
    private fun scheduleExactAlarm(
        context: Context,
        alarmManager: AlarmManager,
        event: Event,
        triggerTime: Long,
        is10MinBefore: Boolean,
    ): Int {
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

        return try {
            // Doze 中でも発火する正確アラームとして登録する
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent,
            )
            requestCode
        } catch (_: SecurityException) {
            // 権限不足などは無視して -1 を返す
            -1
        }
    }
}