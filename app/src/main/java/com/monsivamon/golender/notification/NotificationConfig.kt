package com.monsivamon.golender.notification

// 通知・アラーム関連の定数を一元管理するオブジェクト
object NotificationConfig {
    // 通知チャンネル設定
    const val CHANNEL_ID = "GOLENDAR_CHANNEL_ID"
    const val CHANNEL_NAME = "予定の通知"
    const val CHANNEL_DESCRIPTION = "カレンダーの予定時刻や10分前にお知らせします"

    // BroadcastReceiverに渡すExtraキー
    const val EXTRA_TITLE = "EXTRA_TITLE"
    const val EXTRA_MESSAGE = "EXTRA_MESSAGE"
    const val EXTRA_ID = "EXTRA_ID"

    // 時間関連の定数
    const val MINUTE_MS = 60_000L
    const val TEN_MIN_BEFORE_MS = 10 * MINUTE_MS

    // スケジュール設定
    const val LOOKAHEAD_MONTHS = 3L    // 先読みする月数
    const val MAX_SCHEDULED = 50       // 一度に登録する最大件数
    const val TEN_MIN_ID_OFFSET = 100_000 // 10分前通知のID衝突回避用オフセット
}