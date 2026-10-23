package com.monsivamon.golender.viewmodel

import com.monsivamon.golender.data.CalendarRepository
import com.monsivamon.golender.data.holiday.HolidayCountry
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

// 祝日データを 30 日ごとに更新する責務を担う。多重実行は Mutex で防止する
class HolidayRefreshManager(
    private val repository: CalendarRepository,
    private val store: SettingsDataStore,
) {
    private val mutex = Mutex()
    private val THIRTY_DAYS_MS = 30L * 24 * 60 * 60 * 1000L

    // force 指定、選択国が前回取得時と異なる、または前回取得から 30 日以上
    // 経過していれば再取得する。再取得した場合は true を返す
    suspend fun refreshIfNeeded(force: Boolean, countries: List<HolidayCountry>): Boolean =
        mutex.withLock {
            val selectedIso = countries.map { it.iso }.sorted()
            val lastFetch = store.lastHolidayFetch()
            val lastCountries = store.lastHolidayFetchCountries().sorted()
            val now = System.currentTimeMillis()

            val countriesChanged = selectedIso != lastCountries
            if (force || countriesChanged || now - lastFetch > THIRTY_DAYS_MS) {
                val success = repository.refreshHolidays(countries)
                // 失敗した国がある場合は最終取得時刻を進めず、次回起動時に再試行する
                if (success) {
                    store.updateLastHolidayFetch(now, selectedIso)
                }
                true
            } else false
        }
}
