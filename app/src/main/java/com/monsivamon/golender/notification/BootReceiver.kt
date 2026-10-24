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
                } catch (_: Exception) {
                    // 再起動直後の再登録失敗は次回起動・6時間周期更新・
                    // 次回通知発火時の自己修復に委ねる（未処理例外でプロセスを落とさない）
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}