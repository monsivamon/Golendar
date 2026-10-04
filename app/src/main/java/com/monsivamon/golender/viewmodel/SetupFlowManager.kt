package com.monsivamon.golender.viewmodel

import com.monsivamon.golender.data.prefs.SettingsKeys

// 初回起動時のセットアップ進捗（Welcome→通知→カレンダー→選択→ジェスチャー）を管理する
class SetupFlowManager(private val store: SettingsDataStore) {

    // セットアップ進捗のスナップショット（Welcome は decideWelcome で別途判定）
    data class State(
        val showNotification: Boolean = false,
        val showCalendar: Boolean = false,
        val showGesture: Boolean = false,
        val aiDone: Boolean = true,
    )

    // DataStore から現在の進捗を計算する
    suspend fun compute(): State {
        val prefs = store.all()
        val notifDone   = prefs[SettingsKeys.NOTIFICATION_SETUP_DONE] ?: false
        val calDone     = prefs[SettingsKeys.CALENDAR_SETUP_DONE] ?: false
        val gestureDone = prefs[SettingsKeys.GESTURE_SETUP_DONE] ?: false
        val aiDone      = prefs[SettingsKeys.AI_SETUP_DONE] ?: false
        return State(
            showNotification = !notifDone,
            showCalendar     = notifDone && !calDone,
            showGesture      = notifDone && calDone && !gestureDone,
            aiDone           = aiDone,
        )
    }

    // Welcome の表示判定。null なら表示しない。
    // 既存ユーザーで「記録だけ必要」な場合は、ここで LAST_WELCOME_VERSION を
    // 書き込んで null を返す（アップデート後に突然 Welcome を見せないため）。
    suspend fun decideWelcome(currentVersion: String): WelcomeInfo? {
        val prefs = store.all()
        val lastVersion = prefs[SettingsKeys.LAST_WELCOME_VERSION]
        val legacyShown = prefs[SettingsKeys.WELCOME_SHOWN] ?: false
        val notifDone   = prefs[SettingsKeys.NOTIFICATION_SETUP_DONE] ?: false
        val calDone     = prefs[SettingsKeys.CALENDAR_SETUP_DONE] ?: false
        val gestureDone = prefs[SettingsKeys.GESTURE_SETUP_DONE] ?: false
        val existingUser = legacyShown || notifDone || calDone || gestureDone

        return when {
            // 記録なし + 既存ユーザー → 記録だけして表示しない
            lastVersion == null && existingUser -> {
                markWelcomeShown(currentVersion)
                null
            }
            // 記録なし + 完全新規 → 初回 Welcome
            lastVersion == null ->
                WelcomeInfo(WelcomeMode.FIRST_LAUNCH, currentVersion)
            // バージョン違い → アップデート Welcome
            lastVersion != currentVersion ->
                WelcomeInfo(WelcomeMode.VERSION_UPDATE, currentVersion, lastVersion)
            // 同じバージョン → 表示しない
            else -> null
        }
    }

    // Welcome の表示完了を記録する（バージョン名を保存）
    suspend fun markWelcomeShown(version: String) {
        store.saveLastWelcomeVersion(version)
        // 後方互換のため旧フラグも立てておく
        store.markSetupDone(SettingsKeys.WELCOME_SHOWN)
    }

    // 通知セットアップ完了を記録し、次のステップの State を返す
    suspend fun markNotificationDone(): State {
        store.markSetupDone(SettingsKeys.NOTIFICATION_SETUP_DONE)
        return compute()
    }

    // カレンダーセットアップ完了を記録し、次のステップの State を返す
    suspend fun markCalendarDone(): State {
        store.markSetupDone(SettingsKeys.CALENDAR_SETUP_DONE)
        return compute()
    }

    // ジェスチャー案内完了を記録する
    suspend fun markGestureDone() {
        store.markSetupDone(SettingsKeys.GESTURE_SETUP_DONE)
    }

    // AI 解析の初回説明完了を記録する
    suspend fun markAiDone() {
        store.markSetupDone(SettingsKeys.AI_SETUP_DONE)
    }

    // カレンダー選択ダイアログが未完了かどうかを返す
    suspend fun isCalendarSelectionPending(): Boolean {
        val prefs = store.all()
        val selectionDone = prefs[SettingsKeys.CALENDAR_SELECTION_DONE] ?: false
        return !selectionDone
    }

    // 位置情報セットアップ完了フラグを取得する
    suspend fun isLocationSetupDone(): Boolean {
        val prefs = store.all()
        return prefs[SettingsKeys.LOCATION_SETUP_DONE] ?: false
    }
}