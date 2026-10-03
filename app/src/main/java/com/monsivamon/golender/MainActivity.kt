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

// アプリのエントリーポイント（Compose UI 起動と Intent 処理を担う Activity）
class MainActivity : ComponentActivity() {

    // ViewModel を Activity スコープで生成する
    private val viewModel: CalendarViewModel by viewModels()

    // エッジツーエッジ設定・通知チャンネル作成・Compose UI 起動を行う
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        createNotificationChannel()

        // 初回起動時のみ Intent を処理する（再生成時は二重処理を避ける）
        if (savedInstanceState == null) {
            handleIntent(intent)
        }

        setContent {
            AppNavigation(viewModel)
        }
    }

    // 新しい Intent を Activity に引き渡されたときに処理する
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    // ショートカットアクションとルート情報を ViewModel へ伝達する
    private fun handleIntent(intent: Intent?) {
        intent ?: return

        // ショートカット経由のアクションを ViewModel へ要求
        val shortcutAction = intent.getStringExtra(EXTRA_SHORTCUT_ACTION)
        if (shortcutAction != null) {
            intent.removeExtra(EXTRA_SHORTCUT_ACTION)
            viewModel.requestShortcut(shortcutAction)
        }

        // 特定画面への遷移要求を ViewModel へ要求
        val route = intent.getStringExtra(EXTRA_ROUTE) ?: return
        intent.removeExtra(EXTRA_ROUTE)
        viewModel.requestNavigation(route)
    }

    // 通知チャンネルを 1 度だけ作成する
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // チャンネル定義を組み立てる
            val channel = NotificationChannel(
                NotificationConfig.CHANNEL_ID,
                NotificationConfig.CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = NotificationConfig.CHANNEL_DESCRIPTION
            }

            // システムへ登録する
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
    }

    // Intent extras のキーを保持する定数
    companion object {
        const val EXTRA_ROUTE = "com.monsivamon.golender.EXTRA_ROUTE"
        const val EXTRA_SHORTCUT_ACTION = "com.monsivamon.golender.EXTRA_SHORTCUT_ACTION"
    }
}