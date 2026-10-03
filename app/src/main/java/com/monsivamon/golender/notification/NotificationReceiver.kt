package com.monsivamon.golender.notification

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.monsivamon.golender.R

// 受信した内容をシステム通知として表示するレシーバ
class NotificationReceiver : BroadcastReceiver() {
    // Intent から通知内容を取得してシステム通知を表示する
    override fun onReceive(context: Context, intent: Intent) {
        // extras からタイトル・本文・通知 ID を取り出す
        val title = intent.getStringExtra(NotificationConfig.EXTRA_TITLE) ?: "予定の時間です"
        val message = intent.getStringExtra(NotificationConfig.EXTRA_MESSAGE) ?: ""
        val notificationId = intent.getIntExtra(
            NotificationConfig.EXTRA_ID, System.currentTimeMillis().toInt(),
        )

        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // 高優先度・自動キャンセルの通知を組み立てる
        val notification = NotificationCompat.Builder(context, NotificationConfig.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        // 通知を発行する
        notificationManager.notify(notificationId, notification)
    }
}