package com.monsivamon.golender

import android.app.Application
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.monsivamon.golender.widget.WidgetUpdateWorker
import java.util.concurrent.TimeUnit

// アプリ全体の起動処理とバックグラウンドタスク登録を行う Application クラス
class GolenderApplication : Application() {

    // アプリ起動時にウィジェット更新の定期タスクを WorkManager に登録する
    override fun onCreate() {
        super.onCreate()

        // 6 時間ごとにウィジェットを更新する定期リクエストを組み立てる
        val workRequest = PeriodicWorkRequestBuilder<WidgetUpdateWorker>(
            6, TimeUnit.HOURS
        ).build()

        // 同名タスクが既にあれば維持し、二重登録を防ぐ
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            WidgetUpdateWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }
}