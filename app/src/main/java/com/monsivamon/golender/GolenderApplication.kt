package com.monsivamon.golender

import android.app.Application
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.monsivamon.golender.data.prefs.AppTimeZone
import com.monsivamon.golender.widget.WidgetUpdateWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

// アプリ全体の起動処理とバックグラウンドタスク登録を行う Application クラス
class GolenderApplication : Application() {

    // アプリ起動時にウィジェット更新の定期タスクを WorkManager に登録する
    override fun onCreate() {
        super.onCreate()

        // 表示タイムゾーン設定を最小コストで初期化する。
        // ミラー SharedPreferences があれば O(1)。初回（旧Verからの更新時）のみ
        // DataStore を 1 度ブロッキング読み取りし、以降はミラーで済む。
        AppTimeZone.init(this)

        // 復元時に deleteLocalEventRecordOnly で DB レコードのみ削除した写真実体
        // （孤児ファイル）を起動時にバックグラウンドで掃除する。
        // 複数回の復元で filesDir/event_photos/ に参照されないファイルが
        // 蓄積するのを防ぐ。
        CoroutineScope(Dispatchers.IO).launch {
            try {
                com.monsivamon.golender.data.CalendarRepository(this@GolenderApplication)
                    .purgeOrphanPhotoFiles()
            } catch (_: Exception) { }
        }

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