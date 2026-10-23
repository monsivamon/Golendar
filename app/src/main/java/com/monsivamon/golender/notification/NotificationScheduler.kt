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
import com.monsivamon.golender.data.prefs.AppTimeZone
import com.monsivamon.golender.data.prefs.SettingsKeys
import com.monsivamon.golender.data.util.allDayNotificationBaseMillis
import com.monsivamon.golender.R
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate

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

            // Android 12+ で正確なアラーム権限が無い場合でも、ここでは中断しない。
            // 各登録で setExactAndAllowWhileIdle が SecurityException を投げた場合、
            // scheduleExactAlarm 内のフォールバックが ±15 分許容の setWindow で再登録し、
            // 権限 OFF 時でも通知が完全に消失するのを防ぐ（チェックリスト 3-2 対応）。

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

            // 先読み期間（今日〜N ヶ月後）を求める。
            // 「今日」は表示タイムゾーン基準（固定 ON なら固定ゾーンの今日）
            val zone = AppTimeZone.effective()
            val nowDate = LocalDate.now(zone)
            val startMillis = nowDate.atStartOfDay(zone).toInstant().toEpochMilli()
            val endMillis = nowDate.plusMonths(NotificationConfig.LOOKAHEAD_MONTHS)
                .atTime(23, 59, 59).atZone(zone).toInstant().toEpochMilli()

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
                // Golendar モードではシステム祝日データを除外する。
                // 可視カレンダーに限定する（非表示カレンダーは通知対象外：README 仕様と整合）。
                val visibleLocalIds = repo.getVisibleLocalCalendars().map { it.id }
                if (visibleLocalIds.isEmpty()) {
                    context.dataStore.edit { it[SettingsKeys.SCHEDULED_ALARM_IDS] = "" }
                    return@withLock
                }
                repo.getLocalEventsForMonth(startMillis, endMillis, visibleLocalIds)
                    .filter { it.description != LocalEvent.DESCRIPTION_HOLIDAY }
            }

            // 未来の予定のみを通知基準時刻順に上限件数だけ抽出する。
            // 終日予定は UTC 深夜保存のため、そのままでは地域によって深夜0時や
            // 前日夕方に通知が鳴る。表示タイムゾーンの 9:00 を基準にする。
            val now = System.currentTimeMillis()
            val futureEvents = events
                .map { event -> event to notificationBaseMillis(event) }
                .filter { (_, base) -> base > now - NotificationConfig.TEN_MIN_BEFORE_MS }
                .sortedBy { (_, base) -> base }
                .take(NotificationConfig.MAX_SCHEDULED)

            // 各予定について 10 分前／定刻のアラームを登録する。
            // 通知固定モード（現地の時計に合わせる）では発火時刻を現地壁時計時刻へ寄せる
            for ((event, base) in futureEvents) {
                if (notify10MinBefore) {
                    val t = AppTimeZone.notifyTriggerMillis(base, NotificationConfig.TEN_MIN_BEFORE_MS)
                    if (t > now) {
                        val rc = scheduleExactAlarm(context, alarmManager, event, t, true)
                        if (rc >= 0) currentIds.add(rc)
                    }
                }
                if (notifyAtStart && base > now) {
                    val t = AppTimeZone.notifyTriggerMillis(base, 0L)
                    val rc = scheduleExactAlarm(context, alarmManager, event, t, false)
                    if (rc >= 0) currentIds.add(rc)
                }
            }

            // 今回登録した要求コードを台帳へ永続化する
            context.dataStore.edit { it[SettingsKeys.SCHEDULED_ALARM_IDS] = currentIds.joinToString(",") }
        }
    }

    // 終日予定の通知基準時刻を求める。
    // 終日予定は UTC 日付境界（深夜0時）で保存されるため、その時刻をそのまま
    // 通知に使うとタイムゾーンによって深夜0時や前日夕方に鳴ってしまう。
    // 表示タイムゾーンにおける当日 9:00 を通知基準にする。
    private fun notificationBaseMillis(event: Event): Long {
        if (!event.isAllDay) return event.startTime
        return allDayNotificationBaseMillis(event.startTime, AppTimeZone.effective())
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
        // event.id (Long) をそのまま Int に切り詰めると別 ID と衝突し、
        // PendingIntent の FLAG_UPDATE_CURRENT によって既存通知が上書き消失する恐れがある。
        // 下位 31bit を 1 億未満へ正規化した「安定 ID」を基準にすることで
        // 実用上無視できる衝突確率まで低減する。
        // 繰り返し予定の全発生日は event.id が同一のため、startTime も
        // 組み合わせて発生回ごとに一意な「安定 ID」を作る。
        // これがないと同一 PendingIntent が FLAG_UPDATE_CURRENT で上書きされ、
        // 直近の 1 回分の通知しか生き残らない（カレンダーアプリの根幹不具合）。
        val uniqueKey = (event.id * 31L) xor (event.startTime / 60_000L)
        val baseId = ((uniqueKey and 0x7FFFFFFFL) % 100_000_000L).toInt()
        val notificationId = baseId + if (is10MinBefore) NotificationConfig.TEN_MIN_ID_OFFSET else 0

        // 通知内容を Intent に詰める
        val intent = Intent(context, NotificationReceiver::class.java).apply {
            val prefix = if (is10MinBefore) context.getString(R.string.vm_notification_prefix_10min) else ""
            putExtra(NotificationConfig.EXTRA_TITLE, prefix + event.title)
            putExtra(NotificationConfig.EXTRA_MESSAGE, event.description)
            putExtra(NotificationConfig.EXTRA_ID, notificationId)
        }

        // 発生回ごとに一意な要求コードを組み立てる
        val requestCode = baseId * 10 + if (is10MinBefore) 1 else 0

        val pendingIntent = PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return try {
            // Doze 中でも発火する正確なアラームとして登録する
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent,
            )
            requestCode
        } catch (_: SecurityException) {
            // 正確アラーム権限が無い端末では ±15 分許容の setWindow で代用する。
            // 無視して沈黙するよりは、多少の遅延でも通知が届く方を優先する。
            try {
                alarmManager.setWindow(
                    AlarmManager.RTC_WAKEUP, triggerTime,
                    15 * 60_000L, pendingIntent,
                )
                requestCode
            } catch (_: Exception) {
                -1
            }
        }
    }
}