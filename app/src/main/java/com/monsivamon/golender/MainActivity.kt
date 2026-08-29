package com.monsivamon.golender

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel
import com.monsivamon.golender.ui.AppNavigation
import com.monsivamon.golender.viewmodel.CalendarViewModel

// アプリのエントリーポイント（通知チャンネル作成とCompose UIの起動）
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 予定通知用のチャンネルを初期化（Android 8.0以上必須）
        createNotificationChannel()

        setContent {
            val viewModel: CalendarViewModel = viewModel()
            AppNavigation(viewModel)
        }
    }

    // 通知チャンネルを作成（「GOLENDAR_CHANNEL_ID」で管理）
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "予定の通知"
            val descriptionText = "カレンダーの予定時刻や10分前にお知らせします"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel("GOLENDAR_CHANNEL_ID", name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}