package com.monsivamon.golender.viewmodel

import com.monsivamon.golender.data.CalendarRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

// 祝日データを 30 日ごとに更新する責務を担う。多重実行は Mutex で防止する
class HolidayRefreshManager(
    private val repository: CalendarRepository,
    private val store: SettingsDataStore,
) {
    private val mutex = Mutex()
    private val THIRTY_DAYS_MS = 30L * 24 * 60 * 60 * 1000L

    // force 指定、または前回取得から 30 日以上経過していれば再取得する
    // 再取得した場合は true を返す
    suspend fun refreshIfNeeded(force: Boolean): Boolean = mutex.withLock {
        val lastFetch = store.lastHolidayFetch()
        val now = System.currentTimeMillis()
        if (force || now - lastFetch > THIRTY_DAYS_MS) {
            repository.fetchAndSaveHolidays()
            store.updateLastHolidayFetch(now)
            true
        } else false
    }
}