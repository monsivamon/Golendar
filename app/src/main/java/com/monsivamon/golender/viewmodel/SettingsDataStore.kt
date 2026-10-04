package com.monsivamon.golender.viewmodel

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.monsivamon.golender.data.SelectedCalendarJson
import com.monsivamon.golender.data.prefs.SettingsKeys
import kotlinx.coroutines.flow.first
import java.time.DayOfWeek

// DataStore への読み書きを一元化する薄いファサード
class SettingsDataStore(
    private val store: DataStore<Preferences>,
) {
    // 全設定を一括読み込みする
    suspend fun all(): Preferences = store.data.first()

    // 祝日データの最終取得時刻を取得する（未設定は 0L）
    suspend fun lastHolidayFetch(): Long =
        all()[SettingsKeys.LAST_HOLIDAY_FETCH] ?: 0L

    // 祝日データの最終取得時刻を保存する
    suspend fun updateLastHolidayFetch(timestamp: Long) {
        store.edit { it[SettingsKeys.LAST_HOLIDAY_FETCH] = timestamp }
    }

    // 祝日表示 ON/OFF を保存する
    suspend fun saveShowHolidays(show: Boolean) {
        store.edit { it[SettingsKeys.SHOW_HOLIDAYS] = show }
    }

    // テーマモードを保存する
    suspend fun saveTheme(mode: ThemeMode) {
        store.edit { it[SettingsKeys.THEME] = mode.name }
    }

    // 週の開始曜日を保存する
    suspend fun saveWeekStart(day: DayOfWeek) {
        store.edit { it[SettingsKeys.WEEK_START] = day.name }
    }

    // カレンダーモードを保存する
    suspend fun saveMode(mode: CalendarMode) {
        store.edit { it[SettingsKeys.MODE] = mode.name }
    }

    // 旧バージョンの選択アカウントキーを削除する
    suspend fun removeAccount() {
        store.edit { it.remove(SettingsKeys.ACCOUNT) }
    }

    // アプリ背景色を保存する（COLOR_UNSPECIFIED も含む）
    suspend fun saveBgColor(value: String) {
        store.edit { it[SettingsKeys.BG_COLOR] = value }
    }

    // 曜日ごとの文字色を保存する
    suspend fun saveDayColor(day: DayOfWeek, value: String) {
        store.edit { it[SettingsKeys.dayColor(day)] = value }
    }

    // 月表示の下部予定リスト表示 ON/OFF を保存する
    suspend fun saveShowBottomList(show: Boolean) {
        store.edit { it[SettingsKeys.SHOW_BOTTOM_LIST] = show }
    }

    // 定刻・10 分前通知の ON/OFF をまとめて保存する
    suspend fun saveNotify(atStart: Boolean, before10: Boolean) {
        store.edit { prefs ->
            prefs[SettingsKeys.NOTIFY_AT_START] = atStart
            prefs[SettingsKeys.NOTIFY_10MIN] = before10
        }
    }

    // 選択中カレンダーの JSON 文字列を保存する
    suspend fun saveSelectedCalendarsJson(json: String) {
        store.edit { it[SettingsKeys.SELECTED_CALENDARS] = json }
    }

    // カレンダー選択完了フラグを保存する
    suspend fun setCalendarSelectionDone(done: Boolean) {
        store.edit { it[SettingsKeys.CALENDAR_SELECTION_DONE] = done }
    }

    // 任意のセットアップ完了フラグを true に設定する
    suspend fun markSetupDone(key: Preferences.Key<Boolean>) {
        store.edit { it[key] = true }
    }

    // 写真もバックアップ ON/OFF を保存する
    suspend fun saveBackupPhotos(enabled: Boolean) {
        store.edit { it[SettingsKeys.BACKUP_PHOTOS] = enabled }
    }

    // 最後に Welcome を表示したバージョン名を保存する
    suspend fun saveLastWelcomeVersion(version: String) {
        store.edit { it[SettingsKeys.LAST_WELCOME_VERSION] = version }
    }

    // 旧アカウントからカレンダー選択への移行を原子的に完了する
    suspend fun completeMigration() {
        store.edit {
            it[SettingsKeys.CALENDAR_SELECTION_DONE] = true
            it.remove(SettingsKeys.ACCOUNT)
        }
    }

    // Google モードから Golendar モードへのフォールバックを原子的に書き込む
    suspend fun applyGolendarFallback() {
        store.edit {
            it[SettingsKeys.MODE] = CalendarMode.GOLENDAR.name
            it[SettingsKeys.SELECTED_CALENDARS] = SelectedCalendarJson.toJson(emptyList())
            it[SettingsKeys.CALENDAR_SELECTION_DONE] = false
        }
    }
}