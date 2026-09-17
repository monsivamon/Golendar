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

// アプリのエントリーポイント（通知チャンネル作成とCompose UIの起動）。
class MainActivity : ComponentActivity() {

    private val viewModel: CalendarViewModel by viewModels()

    // 通知チャンネル作成・Intent処理・Compose UI起動を行う。
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        createNotificationChannel()

        if (savedInstanceState == null) {
            handleIntent(intent)
        }

        setContent {
            AppNavigation(viewModel)
        }
    }

    // 新しいIntentを受け取り処理する。
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    // Intentに含まれるルートやショートカットアクションをViewModelに伝える。
    private fun handleIntent(intent: Intent?) {
        intent ?: return

        val shortcutAction = intent.getStringExtra(EXTRA_SHORTCUT_ACTION)
        if (shortcutAction != null) {
            intent.removeExtra(EXTRA_SHORTCUT_ACTION)
            viewModel.requestShortcut(shortcutAction)
        }

        val route = intent.getStringExtra(EXTRA_ROUTE) ?: return
        intent.removeExtra(EXTRA_ROUTE)
        viewModel.requestNavigation(route)
    }

    // 通知チャンネルを作成する。
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

    // Intent extras で使うキー定数を保持する。
    companion object {
        const val EXTRA_ROUTE = "com.monsivamon.golender.EXTRA_ROUTE"
        const val EXTRA_SHORTCUT_ACTION = "com.monsivamon.golender.EXTRA_SHORTCUT_ACTION"
    }
}