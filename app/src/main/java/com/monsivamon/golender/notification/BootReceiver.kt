package com.monsivamon.golender.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

// システム起動時またはアプリ更新時にアラームを再登録するレシーバ
class BootReceiver : BroadcastReceiver() {
    // 起動完了／パッケージ更新を検知して非同期でスケジューラを呼ぶ
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            // 非同期処理の完了までレシーバを保持する
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    NotificationScheduler.updateAlarms(context)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}