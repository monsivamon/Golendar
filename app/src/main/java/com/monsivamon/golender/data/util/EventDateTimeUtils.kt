package com.monsivamon.golender.data.util

import android.content.Context
import androidx.compose.ui.graphics.Color
import com.monsivamon.golender.R
import com.monsivamon.golender.data.Event
import com.monsivamon.golender.data.prefs.AppTimeZone
import com.monsivamon.golender.ui.theme.AppColors
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import android.icu.text.DateTimePatternGenerator

// Context から表示用 locale を取り出す
private fun Context.displayLocale(): java.util.Locale =
    resources.configuration.locales[0]

// 時刻表示用フォーマッタ（24 時間表記）
private val TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

// locale に応じた最適パターンで日付フォーマッタを組み立てる
private fun LocalDate.formatLocal(context: Context, skeleton: String): String {
    val locale = context.displayLocale()
    val pattern = DateTimePatternGenerator.getInstance(locale).getBestPattern(skeleton)
    return format(DateTimeFormatter.ofPattern(pattern, locale))
}

// 予定の日付解釈に使う TZ を返す（終日=UTC / 時間指定=表示設定のタイムゾーン）。
// 表示タイムゾーン固定 ON なら固定ゾーン、OFF なら端末のシステムゾーン。
fun Event.zone(): ZoneId = if (isAllDay) ZoneOffset.UTC else AppTimeZone.effective()

// 予定の開始日をローカル日付で返す
fun Event.localStartDate(): LocalDate =
    LocalDateTime.ofInstant(Instant.ofEpochMilli(startTime), zone()).toLocalDate()

// 予定の終了日をローカル日付で返す（終日は endTime-1ms で当日に丸める）
fun Event.localEndDate(): LocalDate {
    val adjusted = if (endTime > startTime) endTime - 1 else endTime
    return LocalDateTime.ofInstant(Instant.ofEpochMilli(adjusted), zone()).toLocalDate()
}

// 指定日に予定が発生するかを判定する
fun Event.occursOn(date: LocalDate): Boolean = date in localStartDate()..localEndDate()

// 予定の開始日時をローカル日時で返す
fun Event.localStartDateTime(): LocalDateTime =
    LocalDateTime.ofInstant(Instant.ofEpochMilli(startTime), zone())

// 予定の終了日時をローカル日時で返す（終日は endTime-1ms で当日に丸める）
fun Event.localEndDateTime(): LocalDateTime {
    val adjusted = if (isAllDay && endTime > startTime) endTime - 1 else endTime
    return LocalDateTime.ofInstant(Instant.ofEpochMilli(adjusted), zone())
}

// 「09:00 - 10:00」または「終日」の形式で時刻範囲を返す
fun Event.timeRangeString(context: Context): String {
    if (isAllDay) return context.getString(R.string.dt_all_day)
    return "${localStartDateTime().format(TIME_FORMATTER)} - ${localEndDateTime().format(TIME_FORMATTER)}"
}

// 終日予定（UTC 深夜保存）の通知基準時刻を返す。
// 保存されている日付を、表示タイムゾーンにおける 9:00 として解釈する。
// これにより UTC 圏の深夜0時や米国圏の前日夕方に通知が鳴るのを防ぐ。
fun allDayNotificationBaseMillis(startTime: Long, zone: ZoneId): Long {
    val date = Instant.ofEpochMilli(startTime)
        .atZone(ZoneOffset.UTC)
        .toLocalDate()
    return date.atTime(9, 0).atZone(zone).toInstant().toEpochMilli()
}

// 開始時刻を「HH:mm」で返す（終日予定は空文字）
fun Event.startTimeText(): String =
    if (isAllDay) "" else localStartDateTime().format(TIME_FORMATTER)

// 終了時刻を「HH:mm」で返す（終日予定は空文字）
fun Event.endTimeText(): String =
    if (isAllDay) "" else localEndDateTime().format(TIME_FORMATTER)

// 「2024年1月1日」（ja）/「Jan 1, 2024」（en）等、locale 適応の単日表記を返す
fun Event.dateRangeString(context: Context): String {
    val s = localStartDate()
    val e = localEndDate()
    if (s == e) return s.formatLocal(context, "yMMMMd")
    return "${s.formatLocal(context, "MMMd")} - ${e.formatLocal(context, "MMMd")}"
}

// 「2024/1/1」（ja）/「1/1/2024」（en）等、locale 適応の年月日表記を返す
fun LocalDate.shortDate(context: Context): String = formatLocal(context, "yMd")

// 曜日を locale の短縮名（ja=「日」/ en=「Mon」/ ko=「일」）で返す
fun DayOfWeek.dayShort(context: Context): String =
    getDisplayName(TextStyle.SHORT, context.displayLocale())

// 予定の種別に応じたアクセント色を返す
fun Event.accentColor(colors: AppColors): Color = when {
    // 予定ごとの任意色が設定されていれば最優先で使う
    colorArgb != 0 -> Color(colorArgb)
    calendarColorArgb != null && calendarColorArgb != 0 -> Color(calendarColorArgb)
    isBirthdayCalendar -> Color(0xFFFF9800)
    isCulturalEvent -> Color(0xFF4CAF50)
    else -> colors.primaryAccent
}

// 終日予定の補正後ミリ秒（開始・終了）を保持するデータクラス
data class CorrectedMillis(val start: Long, val end: Long)

// 終日予定の開始／終了ミリ秒を UTC 日付境界に補正して返す
fun correctAllDayMillis(startMillis: Long, endMillis: Long): CorrectedMillis {
    val dayMs = 86_400_000L
    var start = startMillis
    var end = endMillis
    // 開始が UTC 境界でなければローカル日付の 0 時（UTC 基準）に丸める
    if (startMillis % dayMs != 0L) {
        start = Instant.ofEpochMilli(startMillis)
            .atZone(AppTimeZone.effective()).toLocalDate()
            .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    }
    // 終了が UTC 境界でなければ 23 時以降かどうかで翌日／当日に丸める
    if (endMillis % dayMs != 0L) {
        val endZoned = Instant.ofEpochMilli(endMillis).atZone(AppTimeZone.effective())
        end = if (endZoned.hour >= 23) {
            endZoned.toLocalDate().plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        } else {
            endZoned.toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        }
    }
    // 終了が開始以下なら 1 日分に補正する
    if (end <= start) end = start + dayMs
    return CorrectedMillis(start, end)
}