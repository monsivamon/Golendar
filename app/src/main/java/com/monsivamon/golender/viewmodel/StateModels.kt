package com.monsivamon.golender.viewmodel

// テーマモード（システム／ライト／ダーク）
enum class ThemeMode { SYSTEM, LIGHT, DARK }

// カレンダーデータソース（アプリ内／Google）
enum class CalendarMode { GOLENDAR, GOOGLE }

// Welcome の表示モード
enum class WelcomeMode { FIRST_LAUNCH, VERSION_UPDATE }

// Welcome 画面に表示する情報
data class WelcomeInfo(
    val mode: WelcomeMode,
    val currentVersion: String,
    val fromVersion: String? = null,
)