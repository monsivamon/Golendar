package com.monsivamon.golender.notification

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.monsivamon.golender.MainActivity
import com.monsivamon.golender.R
import com.monsivamon.golender.data.prefs.LanguageSetting
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

// 受信した内容をシステム通知として表示するレシーバ
class NotificationReceiver : BroadcastReceiver() {
    // Intent から通知内容を取得してシステム通知を表示する
    override fun onReceive(context: Context, intent: Intent) {
        // アプリ内言語設定を反映した Context を使用する
        val localized = LanguageSetting.wrap(context)
        // extras からタイトル・本文・通知 ID を取り出す
        val title = intent.getStringExtra(NotificationConfig.EXTRA_TITLE)
            ?: localized.getString(R.string.vm_notification_default_title)
        val message = intent.getStringExtra(NotificationConfig.EXTRA_MESSAGE) ?: ""
        val notificationId = intent.getIntExtra(
            NotificationConfig.EXTRA_ID, System.currentTimeMillis().toInt(),
        )

        val notificationManager =
            localized.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // 通知タップでアプリの月表示画面を直接開く Intent。
        // タップ時 MainActivity が既にあれば onNewIntent 経由でルート要求を処理する。
        val contentIntent = PendingIntent.getActivity(
            localized,
            0,
            Intent(localized, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(MainActivity.EXTRA_ROUTE, com.monsivamon.golender.ui.Routes.MONTHLY)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        // 高優先度・自動キャンセルの通知を組み立てる
        val notification = NotificationCompat.Builder(localized, NotificationConfig.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()

        // 通知を発行する
        notificationManager.notify(notificationId, notification)

        // 発火後に残りのアラームを再構成する。
        // WorkManager の 6 時間周期更新のみだと省電力状態で取りこぼす可能性があるため、
        // 通知 1 回ごとに残スケジュールを延長する（自己持続チェーン）。
        // updateAlarms 内部で「未来の予定のみ」を登録するため二重発火にはならない。
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                NotificationScheduler.updateAlarms(localized)
            } catch (_: Exception) {
                // 再構成に失敗しても通知表示済みなので無視（次回周期で再登録される）
            } finally {
                pendingResult.finish()
            }
        }
    }
}