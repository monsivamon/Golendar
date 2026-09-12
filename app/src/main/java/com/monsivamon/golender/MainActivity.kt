package com.monsivamon.golender

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.monsivamon.golender.notification.NotificationConfig
import com.monsivamon.golender.ui.AppNavigation
import com.monsivamon.golender.viewmodel.CalendarViewModel

// アプリのエントリーポイント（通知チャンネル作成とCompose UIの起動）
class MainActivity : ComponentActivity() {

    // Activityスコープで共有されるViewModel
    private val viewModel: CalendarViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        // エッジツーエッジ表示を有効化
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // 予定通知用のチャンネルを初期化
        createNotificationChannel()

        // 新規起動時のみIntentを処理（回転時はスキップ）
        if (savedInstanceState == null) {
            handleIntent(intent)
        }

        setContent {
            AppNavigation(viewModel)
        }
    }

    // アプリ起動中にウィジェットをタップした場合の処理（launchMode="singleTop"）
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    // Intentに含まれる遷移先ルートをViewModelに伝える
    private fun handleIntent(intent: Intent?) {
        val route = intent?.getStringExtra(EXTRA_ROUTE) ?: return
        intent.removeExtra(EXTRA_ROUTE)
        viewModel.requestNavigation(route)
    }

    // 通知チャンネルを作成（Android 8.0以上必須）
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NotificationConfig.CHANNEL_ID,
                NotificationConfig.CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = NotificationConfig.CHANNEL_DESCRIPTION
            }

            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
    }

    companion object {
        // ウィジェットタップ時に渡す遷移先ルート用のExtraキー
        const val EXTRA_ROUTE = "com.monsivamon.golender.EXTRA_ROUTE"
    }
}