package com.monsivamon.golender.data.util

import com.monsivamon.golender.data.Event
import com.monsivamon.golender.data.LocalEvent
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// 繰り返しルール（RRULE）の解析と展開を一元管理する
object RruleExpander {

    // 平日のみの繰り返しを表す共通ルール
    const val RRULE_WEEKDAYS = "FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR"

    private val UNTIL_REGEX = Regex("UNTIL=(\\d{8})")
    private val UNTIL_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd")
    // WEEKLY 系ルールで使われる BYDAY=MO,WE,... を抽出するための正規表現
    private val BYDAY_REGEX = Regex("BYDAY=([A-Z,]+)")

    // UNTIL=yyyyMMdd... から日付を抽出する（無ければ null）
    fun parseUntilDate(rule: String?): LocalDate? {
        if (rule == null) return null
        val match = UNTIL_REGEX.find(rule) ?: return null
        return try {
            LocalDate.parse(match.groupValues[1], UNTIL_DATE_FORMAT)
        } catch (_: Exception) { null }
    }

    // rule から UNTIL 部分を除去して基本ルールだけを返す
    fun baseRuleOf(rule: String): String =
        rule.split(";").filter { !it.startsWith("UNTIL=") }.joinToString(";")

    // 指定日を RRULE の UNTIL 形式文字列に整形する
    fun formatUntil(date: LocalDate): String =
        date.format(UNTIL_DATE_FORMAT) + "T235959Z"

    // 曜日コード（MO/TU/WE/TH/FR/SA/SU）を DayOfWeek へ変換する
    private fun dayOfWeekFromCode(code: String): DayOfWeek? = when (code.trim().uppercase()) {
        "MO" -> DayOfWeek.MONDAY
        "TU" -> DayOfWeek.TUESDAY
        "WE" -> DayOfWeek.WEDNESDAY
        "TH" -> DayOfWeek.THURSDAY
        "FR" -> DayOfWeek.FRIDAY
        "SA" -> DayOfWeek.SATURDAY
        "SU" -> DayOfWeek.SUNDAY
        else -> null
    }

    // WEEKLY 系ルールの BYDAY 集合を抽出する（指定が無ければ空集合）
    private fun extractBydaySet(rule: String): Set<DayOfWeek> {
        val match = BYDAY_REGEX.find(rule.uppercase()) ?: return emptySet()
        return match.groupValues[1]
            .split(",")
            .mapNotNull { dayOfWeekFromCode(it) }
            .toSet()
    }

    // 基本ルールに基づき次の発生日時を返す（該当なしなら null）
    fun nextOccurrence(from: LocalDateTime, rule: String): LocalDateTime? {
        return when {
            rule == "FREQ=DAILY" -> from.plusDays(1)
            rule == "FREQ=WEEKLY" -> from.plusWeeks(1)
            // 平日のみは週末を飛ばして次の平日を探す
            rule == RRULE_WEEKDAYS -> {
                var next = from.plusDays(1)
                while (next.dayOfWeek == DayOfWeek.SATURDAY ||
                    next.dayOfWeek == DayOfWeek.SUNDAY) {
                    next = next.plusDays(1)
                }
                next
            }
            // WEEKLY + BYDAY: 次の該当曜日まで進める（最大 14 日で打ち切り）
            rule.startsWith("FREQ=WEEKLY;BYDAY=") -> {
                val bydaySet = extractBydaySet(rule)
                if (bydaySet.isEmpty()) return null
                var next = from.plusDays(1)
                var guard = 0
                while (next.dayOfWeek !in bydaySet && guard < 14) {
                    next = next.plusDays(1)
                    guard++
                }
                if (next.dayOfWeek in bydaySet) next else null
            }
            rule == "FREQ=MONTHLY" -> from.plusMonths(1)
            rule == "FREQ=YEARLY" -> from.plusYears(1)
            else -> null
        }
    }

    // LocalEvent を期間内の Event インスタンス群に展開する
    fun expand(
        event: LocalEvent,
        queryStart: Long,
        queryEnd: Long,
        zone: ZoneId,
    ): List<Event> {
        val rawRule = event.rrule?.trim()?.uppercase()

        // 繰り返しが無い場合は期間重複を判定してそのまま返す
        if (rawRule.isNullOrEmpty()) {
            return if (event.startTime <= queryEnd && event.endTime >= queryStart) {
                listOf(
                    Event(
                        event.id, event.title, event.startTime, event.endTime,
                        event.isAllDay, event.calendarId, event.location, event.description,
                        null, isReadOnly = false
                    )
                )
            } else emptyList()
        }

        // ルールから UNTIL と基本ルールを取り出す
        val untilDate = parseUntilDate(rawRule)
        val baseRule = baseRuleOf(rawRule)
        // BYDAY 制約（WEEKLY 系のみ）。空集合なら曜日指定なし。
        val bydaySet = extractBydaySet(baseRule)

        // 予定の期間（継続時間）を求める
        val eventStartLdt = LocalDateTime.ofInstant(Instant.ofEpochMilli(event.startTime), zone)
        val eventEndLdt = LocalDateTime.ofInstant(Instant.ofEpochMilli(event.endTime), zone)
        val eventDuration = Duration.between(eventStartLdt, eventEndLdt)

        val queryStartLdt = LocalDateTime.ofInstant(Instant.ofEpochMilli(queryStart), zone)
        val queryEndLdt = LocalDateTime.ofInstant(Instant.ofEpochMilli(queryEnd), zone)

        var currentStart = eventStartLdt

        // クエリ開始より大幅に前なら、無駄な走査を避けるため一気に近傍まで進める。
        // 例: 5 年前に登録した毎日繰り返しを月表示で開く場合、
        // 1825 日分の 1 日ずつループを回避して ANR を防ぐ。
        if (currentStart.isBefore(queryStartLdt)) {
            when {
                baseRule == "FREQ=DAILY" -> {
                    val days = java.time.temporal.ChronoUnit.DAYS.between(currentStart, queryStartLdt)
                    if (days > 1) currentStart = currentStart.plusDays(days - 1)
                }
                baseRule == "FREQ=WEEKLY" ||
                    baseRule == RRULE_WEEKDAYS ||
                    baseRule.startsWith("FREQ=WEEKLY;BYDAY=") -> {
                    val weeks = java.time.temporal.ChronoUnit.WEEKS.between(currentStart, queryStartLdt)
                    if (weeks > 1) currentStart = currentStart.plusWeeks(weeks - 1)
                }
                baseRule == "FREQ=MONTHLY" -> {
                    val months = java.time.temporal.ChronoUnit.MONTHS.between(currentStart, queryStartLdt)
                    if (months > 1) currentStart = currentStart.plusMonths(months - 1)
                }
                baseRule == "FREQ=YEARLY" -> {
                    val years = java.time.temporal.ChronoUnit.YEARS.between(currentStart, queryStartLdt)
                    if (years > 1) currentStart = currentStart.plusYears(years - 1)
                }
            }
        }

        // BYDAY 制約がある場合、開始が該当曜日になるまで進める（最大 14 日で打ち切り）
        if (bydaySet.isNotEmpty()) {
            var guard = 0
            while (currentStart.dayOfWeek !in bydaySet && guard < 14) {
                currentStart = currentStart.plusDays(1)
                guard++
            }
        }

        // 探索上限はクエリ終端と 5 年後の早い方
        val limit = minOf(queryEndLdt, currentStart.plusYears(5))
        val result = mutableListOf<Event>()

        while (!currentStart.isAfter(limit)) {
            // UNTIL を超えたら終了
            if (untilDate != null && currentStart.toLocalDate().isAfter(untilDate)) break

            // BYDAY 制約に合致しない曜日はスキップする
            val shouldSkip = bydaySet.isNotEmpty() && currentStart.dayOfWeek !in bydaySet

            // 期間内かつスキップ対象でなければ Event を追加
            if (!shouldSkip && !currentStart.isAfter(queryEndLdt)) {
                val currentEnd = currentStart.plus(eventDuration)
                if (!currentEnd.isBefore(queryStartLdt)) {
                    result.add(
                        Event(
                            event.id, event.title,
                            currentStart.atZone(zone).toInstant().toEpochMilli(),
                            currentEnd.atZone(zone).toInstant().toEpochMilli(),
                            event.isAllDay, event.calendarId, event.location, event.description,
                            event.rrule, isReadOnly = false
                        )
                    )
                }
            }

            // 次の発生日時へ進める（該当なしで終了）
            currentStart = nextOccurrence(currentStart, baseRule) ?: break
        }
        return result
    }
}