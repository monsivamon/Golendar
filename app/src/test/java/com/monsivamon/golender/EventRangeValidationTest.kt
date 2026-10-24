package com.monsivamon.golender

import com.monsivamon.golender.data.util.allDayMillisRange
import com.monsivamon.golender.data.util.isEndBeforeStart
import com.monsivamon.golender.data.util.resolveTimedEndDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

// 予定の開始・終了範囲チェックのユニットテスト
class EventRangeValidationTest {

    private val date = LocalDate.of(2026, 10, 24)

    // 時間指定で同じ日の終了時刻が開始時刻より前なら不正
    @Test
    fun timedEndBeforeStartOnSameDay_isInvalid() {
        assertTrue(
            isEndBeforeStart(
                startDate = date,
                startTime = LocalTime.of(10, 0),
                endDate = date,
                endTime = LocalTime.of(9, 0),
                isAllDay = false,
            )
        )
    }

    // 開始と終了が同時刻の場合は 0 分の予定として許容する
    @Test
    fun timedSameStartAndEnd_isAllowed() {
        assertFalse(
            isEndBeforeStart(
                startDate = date,
                startTime = LocalTime.of(10, 0),
                endDate = date,
                endTime = LocalTime.of(10, 0),
                isAllDay = false,
            )
        )
    }

    // 翌日の早い時刻は開始より後なので許容する
    @Test
    fun timedEndOnNextDay_isAllowed() {
        assertFalse(
            isEndBeforeStart(
                startDate = date,
                startTime = LocalTime.of(22, 0),
                endDate = date.plusDays(1),
                endTime = LocalTime.of(1, 0),
                isAllDay = false,
            )
        )
    }

    // 終日予定は日付のみで比較する
    @Test
    fun allDayEndBeforeStartIsInvalid() {
        assertTrue(
            isEndBeforeStart(
                startDate = date,
                startTime = LocalTime.MIDNIGHT,
                endDate = date.minusDays(1),
                endTime = LocalTime.MIDNIGHT,
                isAllDay = true,
            )
        )
    }

    // 終日予定で開始日と終了日が同じなら 1 日分として許容する
    @Test
    fun allDaySameDay_isAllowed() {
        assertFalse(
            isEndBeforeStart(
                startDate = date,
                startTime = LocalTime.MIDNIGHT,
                endDate = date,
                endTime = LocalTime.MIDNIGHT,
                isAllDay = true,
            )
        )
    }

    // 23:50〜01:30 のように終了時刻が開始時刻より前なら翌日の終了として解決する
    @Test
    fun timedOvernightEnd_resolvesToNextDay() {
        val resolved = resolveTimedEndDate(
            startDate = date,
            startTime = LocalTime.of(23, 50),
            endDate = date,
            endTime = LocalTime.of(1, 30),
        )
        assertEquals(date.plusDays(1), resolved)
        assertFalse(
            isEndBeforeStart(date, LocalTime.of(23, 50), resolved, LocalTime.of(1, 30), isAllDay = false)
        )
    }

    // 終了日を翌日に指定済みならそのまま使う
    @Test
    fun timedOvernightEnd_onNextDayStaysUnchanged() {
        val resolved = resolveTimedEndDate(
            startDate = date,
            startTime = LocalTime.of(23, 50),
            endDate = date.plusDays(1),
            endTime = LocalTime.of(1, 30),
        )
        assertEquals(date.plusDays(1), resolved)
    }

    // 同時刻は 0 分の予定として同じ日のままにする
    @Test
    fun timedSameTime_staysOnSameDay() {
        val resolved = resolveTimedEndDate(
            startDate = date,
            startTime = LocalTime.of(10, 0),
            endDate = date,
            endTime = LocalTime.of(10, 0),
        )
        assertEquals(date, resolved)
    }

    // 開始日より前の終了日は翌日化せず、不正なまま残す
    @Test
    fun timedEndDateBeforeStart_staysInvalid() {
        val resolved = resolveTimedEndDate(
            startDate = date,
            startTime = LocalTime.of(10, 0),
            endDate = date.minusDays(1),
            endTime = LocalTime.of(9, 0),
        )
        assertEquals(date.minusDays(1), resolved)
        assertTrue(
            isEndBeforeStart(date, LocalTime.of(10, 0), resolved, LocalTime.of(9, 0), isAllDay = false)
        )
    }

    // 終日 1 日分は翌日 0 時（UTC）までの exclusive な範囲になる
    @Test
    fun allDaySingleDay_becomesOneDayRange() {
        val range = allDayMillisRange(date, date)
        assertEquals(date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(), range.start)
        assertEquals(date.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(), range.end)
        assertEquals(86_400_000L, range.end - range.start)
    }

    // 終日 3 日分（開始日と終了日を含む）は 3 日分の範囲になる
    @Test
    fun allDayMultiDay_includesEndDate() {
        val range = allDayMillisRange(date, date.plusDays(2))
        assertEquals(date.plusDays(3).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(), range.end)
        assertEquals(3 * 86_400_000L, range.end - range.start)
    }

    // 保存した範囲を表示用の終了日（end - 1ms）へ戻すと、選択した終了日になる
    @Test
    fun allDayRange_roundTripsToInclusiveEndDate() {
        val endDate = date.plusDays(2)
        val range = allDayMillisRange(date, endDate)
        val displayedEnd = Instant.ofEpochMilli(range.end - 1).atZone(ZoneOffset.UTC).toLocalDate()
        assertEquals(endDate, displayedEnd)
    }
}
