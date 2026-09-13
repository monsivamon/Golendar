package com.monsivamon.golender.data.util

import androidx.compose.ui.graphics.Color
import com.monsivamon.golender.data.Event
import com.monsivamon.golender.ui.theme.AppColors
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

// 予定の日付解釈に使うタイムゾーンを返す（終日=UTC、時間指定=システムTZ）。
fun Event.zone(): ZoneId = if (isAllDay) ZoneOffset.UTC else ZoneId.systemDefault()

// 予定の開始日をローカル日付で返す。
fun Event.localStartDate(): LocalDate =
    LocalDateTime.ofInstant(Instant.ofEpochMilli(startTime), zone()).toLocalDate()

// 予定の終了日をローカル日付で返す（終日予定は endTime-1ms で当日に丸める）。
fun Event.localEndDate(): LocalDate {
    val adjusted = if (endTime > startTime) endTime - 1 else endTime
    return LocalDateTime.ofInstant(Instant.ofEpochMilli(adjusted), zone()).toLocalDate()
}

// 指定日に予定が発生するかを判定する。
fun Event.occursOn(date: LocalDate): Boolean = date in localStartDate()..localEndDate()

// 予定の開始日時をローカル日時で返す。
fun Event.localStartDateTime(): LocalDateTime =
    LocalDateTime.ofInstant(Instant.ofEpochMilli(startTime), zone())

// 予定の終了日時をローカル日時で返す（終日予定は endTime-1ms で当日に丸める）。
fun Event.localEndDateTime(): LocalDateTime {
    val adjusted = if (isAllDay && endTime > startTime) endTime - 1 else endTime
    return LocalDateTime.ofInstant(Instant.ofEpochMilli(adjusted), zone())
}

// 「09:00 - 10:00」または「終日」の形式で時刻範囲を返す。
fun Event.timeRangeString(): String {
    if (isAllDay) return "終日"
    val fmt = DateTimeFormatter.ofPattern("HH:mm")
    return "${localStartDateTime().format(fmt)} - ${localEndDateTime().format(fmt)}"
}

// 「2024年1月1日」または「1月1日 - 1月3日」の形式で日付範囲を返す。
fun Event.dateRangeString(): String {
    val s = localStartDate()
    val e = localEndDate()
    return if (s == e) "${s.year}年${s.monthValue}月${s.dayOfMonth}日"
    else "${s.monthValue}月${s.dayOfMonth}日 - ${e.monthValue}月${e.dayOfMonth}日"
}

// 予定の種別に応じたアクセント色を返す（誕生日 > 文化イベント > 通常予定）。
fun Event.accentColor(colors: AppColors): Color = when {
    isBirthdayCalendar -> Color(0xFFFF9800)
    isCulturalEvent -> Color(0xFF4CAF50)
    else -> colors.primaryAccent
}

// 曜日を「日」〜「土」の1文字で返す。
fun DayOfWeek.jpShort(): String = when (this) {
    DayOfWeek.SUNDAY -> "日";    DayOfWeek.MONDAY -> "月"
    DayOfWeek.TUESDAY -> "火";   DayOfWeek.WEDNESDAY -> "水"
    DayOfWeek.THURSDAY -> "木";  DayOfWeek.FRIDAY -> "金"
    DayOfWeek.SATURDAY -> "土"
}

// jpShort() に委譲する後方互換用の曜日取得関数。
fun getJpDayOfWeek(dayOfWeek: DayOfWeek): String = dayOfWeek.jpShort()

// 終日予定の補正後ミリ秒（開始・終了）を保持するデータクラス。
data class CorrectedMillis(val start: Long, val end: Long)

// 終日予定の開始/終了ミリ秒をUTC日付境界に補正して返す。
fun correctAllDayMillis(startMillis: Long, endMillis: Long): CorrectedMillis {
    val dayMs = 86_400_000L
    var start = startMillis
    var end = endMillis

    if (startMillis % dayMs != 0L) {
        start = Instant.ofEpochMilli(startMillis)
            .atZone(ZoneId.systemDefault()).toLocalDate()
            .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    }
    if (endMillis % dayMs != 0L) {
        val endZoned = Instant.ofEpochMilli(endMillis).atZone(ZoneId.systemDefault())
        end = if (endZoned.hour >= 23) {
            endZoned.toLocalDate().plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        } else {
            endZoned.toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        }
    }
    if (end <= start) end = start + dayMs
    return CorrectedMillis(start, end)
}