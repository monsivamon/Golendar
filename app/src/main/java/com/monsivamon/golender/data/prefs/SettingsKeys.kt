package com.monsivamon.golender.data.prefs

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import java.time.DayOfWeek

// DataStore で使用する設定キーを一元管理する
object SettingsKeys {
    // テーマモード
    val THEME       = stringPreferencesKey("theme_mode")
    // 週の開始曜日
    val WEEK_START  = stringPreferencesKey("week_start_day")
    // カレンダーモード（GOLENDAR / GOOGLE）
    val MODE        = stringPreferencesKey("calendar_mode")
    // 旧バージョンの選択アカウント（移行用）
    val ACCOUNT     = stringPreferencesKey("selected_account")
    // アプリ背景色
    val BG_COLOR    = stringPreferencesKey("calendar_bg_color")
    // 定刻通知 ON/OFF
    val NOTIFY_AT_START = booleanPreferencesKey("notify_at_start")
    // 10 分前通知 ON/OFF
    val NOTIFY_10MIN    = booleanPreferencesKey("notify_10min_before")
    // 祝日データの最終取得時刻
    val LAST_HOLIDAY_FETCH = longPreferencesKey("last_holiday_fetch_time")
    // 月表示下部リストの表示 ON/OFF
    val SHOW_BOTTOM_LIST = booleanPreferencesKey("show_bottom_list")
    // 通知セットアップ完了フラグ
    val NOTIFICATION_SETUP_DONE = booleanPreferencesKey("notification_setup_done")
    // カレンダーセットアップ完了フラグ
    val CALENDAR_SETUP_DONE = booleanPreferencesKey("calendar_setup_done")
    // 位置情報セットアップ完了フラグ
    val LOCATION_SETUP_DONE = booleanPreferencesKey("location_setup_done")
    // 写真もバックアップするかどうか
    val BACKUP_PHOTOS = booleanPreferencesKey("backup_photos")
    // ジェスチャー案内完了フラグ
    val GESTURE_SETUP_DONE = booleanPreferencesKey("gesture_setup_done")
    // AI 解析の初回説明完了フラグ
    val AI_SETUP_DONE = booleanPreferencesKey("ai_setup_done")

    // 選択中カレンダー（JSON 文字列）
    val SELECTED_CALENDARS = stringPreferencesKey("selected_calendars_json")
    // 祝日を表示するかどうか
    val SHOW_HOLIDAYS = booleanPreferencesKey("show_holidays")
    // カレンダー選択ダイアログ完了フラグ
    val CALENDAR_SELECTION_DONE = booleanPreferencesKey("calendar_selection_done")

    // 曜日ごとの色キーを動的に生成する
    fun dayColor(day: DayOfWeek) = stringPreferencesKey("day_color_${day.name}")

    // 色が未設定であることを示すセンチネル値
    const val COLOR_UNSPECIFIED = "UNSPECIFIED"
}