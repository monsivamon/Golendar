package com.monsivamon.golender.notification

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat

// 受信した通知をシステム通知として表示する
class NotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Intentから通知内容を取得（未設定時はフォールバック値）
        val title = intent.getStringExtra(NotificationConfig.EXTRA_TITLE) ?: "予定の時間です"
        val message = intent.getStringExtra(NotificationConfig.EXTRA_MESSAGE) ?: ""
        val notificationId = intent.getIntExtra(
            NotificationConfig.EXTRA_ID, System.currentTimeMillis().toInt(),
        )

        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // 高優先度の通知を生成して表示
        val notification = NotificationCompat.Builder(context, NotificationConfig.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(notificationId, notification)
    }
}