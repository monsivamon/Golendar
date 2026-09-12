package com.monsivamon.golender.data.prefs

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import java.time.DayOfWeek

// DataStoreで使用する設定キーを一元管理するオブジェクト
object SettingsKeys {
    // テーマモード（SYSTEM / LIGHT / DARK）
    val THEME       = stringPreferencesKey("theme_mode")
    // 週の開始曜日（SUNDAY / MONDAY）
    val WEEK_START  = stringPreferencesKey("week_start_day")
    // カレンダーモード（GOLENDAR / GOOGLE）
    val MODE        = stringPreferencesKey("calendar_mode")
    // 選択中のGoogleアカウント名
    val ACCOUNT     = stringPreferencesKey("selected_account")
    // カレンダー背景色（ARGB整数値を文字列化、未設定は COLOR_UNSPECIFIED）
    val BG_COLOR    = stringPreferencesKey("calendar_bg_color")
    // 定刻通知のON/OFF
    val NOTIFY_AT_START = booleanPreferencesKey("notify_at_start")
    // 10分前通知のON/OFF
    val NOTIFY_10MIN    = booleanPreferencesKey("notify_10min_before")
    // 祝日データの最終取得時刻（30日ごとの更新判定に使用）
    val LAST_HOLIDAY_FETCH = longPreferencesKey("last_holiday_fetch_time")

    // 曜日ごとの色キーを動的に生成
    fun dayColor(day: DayOfWeek) = stringPreferencesKey("day_color_${day.name}")

    // 色が未設定であることを示すセンチネル値
    const val COLOR_UNSPECIFIED = "UNSPECIFIED"
}