package com.monsivamon.golender.viewmodel

import com.monsivamon.golender.data.CalendarRepository
import com.monsivamon.golender.data.Event
import com.monsivamon.golender.data.LocalEvent
import com.monsivamon.golender.data.util.localStartDate
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

// カレンダーイベントの読み込み・マージ・フラグ付けを担当する
class CalendarEventLoader(private val repository: CalendarRepository) {

    // Google モードの予定を読み込み、ローカル祝日とマージして返す
    suspend fun loadGoogleEvents(
        start: Long, end: Long, visibleCalendarIds: List<Long>,
        colorMap: Map<Long, Int>, displayNameMap: Map<Long, String>, showHolidays: Boolean,
    ): List<Event> {
        val calendarIds = visibleCalendarIds.ifEmpty { listOf(-1L) }
        val googleEvents = repository.getEventsForMonth(start, end, calendarIds)

        val officialHolidays = if (showHolidays) {
            repository.getLocalEventsForMonth(start, end).filter { it.description == LocalEvent.DESCRIPTION_HOLIDAY }
        } else emptyList()

        val officialDates = officialHolidays.map {
            LocalDateTime.ofInstant(Instant.ofEpochMilli(it.startTime), ZoneOffset.UTC).toLocalDate()
        }.toSet()

        val mappedGoogleEvents = googleEvents.map { event ->
            val isBirthday = event.isBirthdayCalendar ||
                    (event.title.contains("誕生日") && !event.title.contains("天皇誕生日")) ||
                    event.title.contains("Birthday", ignoreCase = true)

            var updatedEvent = event.copy(
                isBirthdayCalendar = isBirthday,
                calendarColorArgb = colorMap[event.calendarId],
                calendarDisplayName = displayNameMap[event.calendarId] ?: ""
            )

            if (!isBirthday && (updatedEvent.isHolidayCalendar || (updatedEvent.isAllDay && updatedEvent.isReadOnly))) {
                val date = updatedEvent.localStartDate()
                if (officialDates.isNotEmpty()) {
                    val isOfficial = officialDates.contains(date)
                    updatedEvent = updatedEvent.copy(isHolidayCalendar = isOfficial, isCulturalEvent = isOfficial)
                } else {
                    val isCultural = listOf(
                        "七夕", "バレンタイン", "節分", "ひな祭り", "母の日",
                        "父の日", "ハロウィン", "クリスマス", "大晦日", "元日",
                    ).any { updatedEvent.title.contains(it) }
                    if (isCultural || updatedEvent.isHolidayCalendar) {
                        updatedEvent = updatedEvent.copy(isCulturalEvent = true)
                    }
                }
            }
            updatedEvent
        }

        val existingHolidayDates = mappedGoogleEvents
            .filter { it.isHolidayCalendar || it.isCulturalEvent }
            .map { it.localStartDate() }
            .toSet()

        val missingHolidays = if (showHolidays) {
            officialHolidays.filter { localHoliday ->
                val localDate = LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(localHoliday.startTime), ZoneOffset.UTC
                ).toLocalDate()
                !existingHolidayDates.contains(localDate)
            }.map { localHoliday ->
                Event(
                    id = -(localHoliday.id + 1000L),
                    title = localHoliday.title,
                    startTime = localHoliday.startTime,
                    endTime = localHoliday.endTime,
                    isAllDay = true,
                    calendarId = -1L,
                    location = "",
                    description = "",
                    isReadOnly = true,
                    isHolidayCalendar = true,
                    isCulturalEvent = true,
                    calendarDisplayName = "祝日"
                )
            }
        } else emptyList()

        return (mappedGoogleEvents + missingHolidays)
            .distinctBy { ev ->
                if (ev.isHolidayCalendar || ev.isCulturalEvent) {
                    "holiday_${ev.localStartDate()}_${ev.title}"
                } else {
                    "event_${ev.calendarId}_${ev.id}_${ev.startTime}"
                }
            }
            .sortedBy { it.startTime }
    }

    // Golendar モードのローカル予定を読み込む。
    // visibleCalendarIds が指定されれば可視カレンダーでフィルタする。
    // calendarColorMap / calendarNameMap を渡すと、各予定にカレンダーの色と名前を付与する。
    suspend fun loadLocalEvents(
        start: Long, end: Long, showHolidays: Boolean,
        visibleCalendarIds: List<Long>? = null,
        calendarColorMap: Map<Long, Int> = emptyMap(),
        calendarNameMap: Map<Long, String> = emptyMap(),
    ): List<Event> {
        return repository.getLocalEventsForMonth(start, end, visibleCalendarIds).map { event ->
            val isBirthday = (event.title.contains("誕生日") && !event.title.contains("天皇誕生日")) ||
                    event.title.contains("Birthday", ignoreCase = true)
            val isSystemHoliday = event.description == LocalEvent.DESCRIPTION_HOLIDAY

            // 祝日は「祝日」名義・アクセント色（文化イベント緑）で表示する
            val displayName: String
            val colorArgb: Int?
            if (isSystemHoliday) {
                displayName = "祝日"
                colorArgb = null
            } else {
                displayName = calendarNameMap[event.calendarId] ?: ""
                colorArgb = calendarColorMap[event.calendarId]
            }

            event.copy(
                isBirthdayCalendar = isBirthday,
                isReadOnly = isSystemHoliday,
                isHolidayCalendar = isSystemHoliday,
                isCulturalEvent = isSystemHoliday,
                description = if (isSystemHoliday) "" else event.description,
                calendarDisplayName = displayName,
                calendarColorArgb = colorArgb,
            )
        }.filter { ev ->
            if (!showHolidays && ev.isHolidayCalendar) false else true
        }.distinctBy { ev ->
            if (ev.isHolidayCalendar || ev.isCulturalEvent) {
                "holiday_${ev.localStartDate()}_${ev.title}"
            } else {
                "event_${ev.id}_${ev.startTime}"
            }
        }.sortedBy { it.startTime }
    }

    suspend fun loadEventsForSearch(
        start: Long, end: Long, calendarMode: CalendarMode,
        visibleCalendarIds: List<Long>, colorMap: Map<Long, Int>,
        displayNameMap: Map<Long, String>, showHolidays: Boolean,
    ): List<Event> {
        val events = if (calendarMode == CalendarMode.GOOGLE) {
            val calendarIds = visibleCalendarIds.ifEmpty { listOf(-1L) }
            repository.getEventsForMonth(start, end, calendarIds)
                .map { ev ->
                    ev.copy(
                        calendarColorArgb = colorMap[ev.calendarId],
                        calendarDisplayName = displayNameMap[ev.calendarId] ?: ""
                    )
                }
                .filter { !it.isHolidayCalendar && !it.isCulturalEvent && !it.isBirthdayCalendar }
        } else {
            // Golendar モード: 祝日と誕生日を除外する（文化イベントは概念なし）
            repository.getLocalEventsForMonth(start, end)
                .filter { it.description != LocalEvent.DESCRIPTION_HOLIDAY }
                .filter { ev ->
                    val isBirthday =
                        (ev.title.contains("誕生日") && !ev.title.contains("天皇誕生日")) ||
                        ev.title.contains("Birthday", ignoreCase = true)
                    !isBirthday
                }
        }
        return if (!showHolidays) events.filter { !it.isHolidayCalendar } else events
    }
}