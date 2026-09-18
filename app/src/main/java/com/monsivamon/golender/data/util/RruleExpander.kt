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

// 繰り返しルール（RRULE）の解析と展開を一元管理する。
object RruleExpander {

    // 平日のみの繰り返しを表す共通ルール。
    const val RRULE_WEEKDAYS = "FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR"

    private val UNTIL_REGEX = Regex("UNTIL=(\\d{8})")
    private val UNTIL_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd")

    // UNTIL=yyyyMMdd... から日付を抽出する（無ければ null）。
    fun parseUntilDate(rule: String?): LocalDate? {
        if (rule == null) return null
        val match = UNTIL_REGEX.find(rule) ?: return null
        return try {
            LocalDate.parse(match.groupValues[1], UNTIL_DATE_FORMAT)
        } catch (_: Exception) { null }
    }

    // rule から UNTIL 部分を除去して基本ルールだけを返す。
    fun baseRuleOf(rule: String): String =
        rule.split(";").filter { !it.startsWith("UNTIL=") }.joinToString(";")

    // 指定日を RRULE の UNTIL 形式文字列に整形する。
    fun formatUntil(date: LocalDate): String =
        date.format(UNTIL_DATE_FORMAT) + "T235959Z"

    // 基本ルールに基づき次の発生日時を返す（該当なしなら null）。
    fun nextOccurrence(from: LocalDateTime, rule: String): LocalDateTime? {
        return when (rule) {
            "FREQ=DAILY" -> from.plusDays(1)
            "FREQ=WEEKLY" -> from.plusWeeks(1)
            RRULE_WEEKDAYS -> {
                var next = from.plusDays(1)
                while (next.dayOfWeek == DayOfWeek.SATURDAY ||
                    next.dayOfWeek == DayOfWeek.SUNDAY) {
                    next = next.plusDays(1)
                }
                next
            }
            "FREQ=MONTHLY" -> from.plusMonths(1)
            "FREQ=YEARLY" -> from.plusYears(1)
            else -> null
        }
    }

    // LocalEvent を期間内の Event インスタンス群に展開する。
    fun expand(
        event: LocalEvent,
        queryStart: Long,
        queryEnd: Long,
        zone: ZoneId,
    ): List<Event> {
        val rawRule = event.rrule?.trim()?.uppercase()

        if (rawRule.isNullOrEmpty()) {
            return if (event.startTime <= queryEnd && event.endTime >= queryStart) {
                listOf(
                    Event(
                        event.id, event.title, event.startTime, event.endTime,
                        event.isAllDay, -1L, event.location, event.description,
                        null, isReadOnly = false
                    )
                )
            } else emptyList()
        }

        val untilDate = parseUntilDate(rawRule)
        val baseRule = baseRuleOf(rawRule)
        val isWeekdayOnly = baseRule == RRULE_WEEKDAYS

        val eventStartLdt = LocalDateTime.ofInstant(Instant.ofEpochMilli(event.startTime), zone)
        val eventEndLdt = LocalDateTime.ofInstant(Instant.ofEpochMilli(event.endTime), zone)
        val eventDuration = Duration.between(eventStartLdt, eventEndLdt)

        val queryStartLdt = LocalDateTime.ofInstant(Instant.ofEpochMilli(queryStart), zone)
        val queryEndLdt = LocalDateTime.ofInstant(Instant.ofEpochMilli(queryEnd), zone)

        var currentStart = eventStartLdt

        if (isWeekdayOnly) {
            while (currentStart.dayOfWeek == DayOfWeek.SATURDAY ||
                currentStart.dayOfWeek == DayOfWeek.SUNDAY) {
                currentStart = currentStart.plusDays(1)
            }
        }

        val limit = minOf(queryEndLdt, currentStart.plusYears(5))
        val result = mutableListOf<Event>()

        while (!currentStart.isAfter(limit)) {
            if (untilDate != null && currentStart.toLocalDate().isAfter(untilDate)) break

            val dow = currentStart.dayOfWeek
            val isWeekend = dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY
            val shouldSkip = isWeekdayOnly && isWeekend

            if (!shouldSkip && !currentStart.isAfter(queryEndLdt)) {
                val currentEnd = currentStart.plus(eventDuration)
                if (!currentEnd.isBefore(queryStartLdt)) {
                    result.add(
                        Event(
                            event.id, event.title,
                            currentStart.atZone(zone).toInstant().toEpochMilli(),
                            currentEnd.atZone(zone).toInstant().toEpochMilli(),
                            event.isAllDay, -1L, event.location, event.description,
                            event.rrule, isReadOnly = false
                        )
                    )
                }
            }

            currentStart = nextOccurrence(currentStart, baseRule) ?: break
        }
        return result
    }
}