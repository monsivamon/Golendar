package com.monsivamon.golender

import android.app.Application
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.monsivamon.golender.widget.WidgetUpdateWorker
import java.util.concurrent.TimeUnit

// アプリケーションクラス（バックグラウンドでのウィジェット定期更新を設定）
class GolenderApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // 6時間ごとにウィジェットを更新する定期タスクをWorkManagerに登録
        val workRequest = PeriodicWorkRequestBuilder<WidgetUpdateWorker>(
            6, TimeUnit.HOURS
        ).build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            WidgetUpdateWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }
}