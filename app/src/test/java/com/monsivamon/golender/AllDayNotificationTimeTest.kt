package com.monsivamon.golender

import com.monsivamon.golender.data.util.allDayNotificationBaseMillis
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

// 終日予定の通知基準時刻（表示タイムゾーン 9:00）のユニットテスト
class AllDayNotificationTimeTest {

    // 2026-01-01 の終日予定（UTC 深夜保存）
    private val allDayStart = LocalDateTime.of(2026, 1, 1, 0, 0)
        .toInstant(ZoneOffset.UTC)
        .toEpochMilli()

    // UTC では 9:00 UTC に通知する
    @Test
    fun allDayInUtc_isNineAmUtc() {
        val expected = LocalDateTime.of(2026, 1, 1, 9, 0)
            .toInstant(ZoneOffset.UTC)
            .toEpochMilli()
        assertEquals(expected, allDayNotificationBaseMillis(allDayStart, ZoneOffset.UTC))
    }

    // 東京では 9:00 JST（= 00:00 UTC）に通知する
    @Test
    fun allDayInTokyo_isNineAmJst() {
        val expected = LocalDateTime.of(2026, 1, 1, 9, 0)
            .atZone(ZoneId.of("Asia/Tokyo"))
            .toInstant()
            .toEpochMilli()
        assertEquals(expected, allDayNotificationBaseMillis(allDayStart, ZoneId.of("Asia/Tokyo")))
    }

    // ニューヨークでは 9:00 EST（= 14:00 UTC）に通知する（前日夕方にならない）
    @Test
    fun allDayInNewYork_isNineAmEst() {
        val expected = LocalDateTime.of(2026, 1, 1, 9, 0)
            .atZone(ZoneId.of("America/New_York"))
            .toInstant()
            .toEpochMilli()
        assertEquals(expected, allDayNotificationBaseMillis(allDayStart, ZoneId.of("America/New_York")))
    }
}
