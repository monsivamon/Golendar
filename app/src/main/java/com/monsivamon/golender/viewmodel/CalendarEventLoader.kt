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
    suspend fun loadGoogleEvents(start: Long, end: Long, visibleCalendarIds: List<Long>, colorMap: Map<Long, Int>, displayNameMap: Map<Long, String>, showHolidays: Boolean): List<Event> {
        // 可視カレンダーが空なら番兵 ID で何も取れないようにする
        val calendarIds = visibleCalendarIds.ifEmpty { listOf(-1L) }
        val googleEvents = repository.getEventsForMonth(start, end, calendarIds)

        // 祝日表示 ON のときのみローカル祝日を取得する
        val officialHolidays = if (showHolidays) {
            repository.getLocalEventsForMonth(start, end).filter { it.description == LocalEvent.DESCRIPTION_HOLIDAY }
        } else emptyList()

        // 祝日 API の日付集合（Google 側の判定に使う）
        val officialDates = officialHolidays.map {
            LocalDateTime.ofInstant(Instant.ofEpochMilli(it.startTime), ZoneOffset.UTC).toLocalDate()
        }.toSet()

        // Google イベントに誕生日・祝日・文化イベントのフラグとカレンダー色/名を付与する
        val mappedGoogleEvents = googleEvents.map { event ->
            val isBirthday = event.isBirthdayCalendar ||
                    (event.title.contains("誕生日") && !event.title.contains("天皇誕生日")) ||
                    event.title.contains("Birthday", ignoreCase = true)

            var updatedEvent = event.copy(
                isBirthdayCalendar = isBirthday,
                calendarColorArgb = colorMap[event.calendarId],
                calendarDisplayName = displayNameMap[event.calendarId] ?: ""
            )

            // 祝日カレンダーの予定は公式祝日の日付と突き合わせて祝日フラグを決定する
            if (!isBirthday && (updatedEvent.isHolidayCalendar || (updatedEvent.isAllDay && updatedEvent.isReadOnly))) {
                val date = updatedEvent.localStartDate()
                if (officialDates.isNotEmpty()) {
                    val isOfficial = officialDates.contains(date)
                    updatedEvent = updatedEvent.copy(isHolidayCalendar = isOfficial, isCulturalEvent = isOfficial)
                } else {
                    // 公式データが無い場合は文化イベント名で判定する
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

        // Google 側に既に存在する祝日の日付集合
        val existingHolidayDates = mappedGoogleEvents
            .filter { it.isHolidayCalendar || it.isCulturalEvent }
            .map { it.localStartDate() }
            .toSet()

        // Google 側に存在しないローカル祝日を補完イベントとして追加する
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

        // マージ後に祝日重複を除去し、開始時刻順にソートする
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

    // Golendar モードのローカル予定を読み込み、フラグ付けして返す
    suspend fun loadLocalEvents(start: Long, end: Long, showHolidays: Boolean): List<Event> {
        // ローカル予定に誕生日・祝日フラグを付与する
        return repository.getLocalEventsForMonth(start, end).map { event ->
            val isBirthday = (event.title.contains("誕生日") && !event.title.contains("天皇誕生日")) ||
                    event.title.contains("Birthday", ignoreCase = true)
            val isSystemHoliday = event.description == LocalEvent.DESCRIPTION_HOLIDAY
            event.copy(
                isBirthdayCalendar = isBirthday,
                isReadOnly = isSystemHoliday,
                isHolidayCalendar = isSystemHoliday,
                isCulturalEvent = isSystemHoliday,
                description = if (isSystemHoliday) "" else event.description,
            )
        // 祝日非表示の設定なら祝日を除外する
        }.filter { ev ->
            if (!showHolidays && ev.isHolidayCalendar) false else true
        }
        // 祝日重複を除去して開始時刻順に並べる
        .distinctBy { ev ->
            if (ev.isHolidayCalendar || ev.isCulturalEvent) {
                "holiday_${ev.localStartDate()}_${ev.title}"
            } else {
                "event_${ev.id}_${ev.startTime}"
            }
        }
        .sortedBy { it.startTime }
    }

    // 検索対象の予定を取得する
    suspend fun loadEventsForSearch(
        start: Long,
        end: Long,
        calendarMode: CalendarMode,
        visibleCalendarIds: List<Long>,
        colorMap: Map<Long, Int>,
        displayNameMap: Map<Long, String>,
        showHolidays: Boolean
    ): List<Event> {
        // モード別に取得し、検索対象外（祝日・文化・誕生日）を除外する
        val events = if (calendarMode == CalendarMode.GOOGLE) {
            val calendarIds = visibleCalendarIds.ifEmpty { listOf(-1L) }
            repository.getEventsForMonth(start, end, calendarIds)
                .map { ev ->
                    // 検索結果でもカレンダー色・表示名を反映する
                    ev.copy(
                        calendarColorArgb = colorMap[ev.calendarId],
                        calendarDisplayName = displayNameMap[ev.calendarId] ?: ""
                    )
                }
                .filter { !it.isHolidayCalendar && !it.isCulturalEvent && !it.isBirthdayCalendar }
        } else {
            repository.getLocalEventsForMonth(start, end)
                .filter { it.description != LocalEvent.DESCRIPTION_HOLIDAY }
        }

        // 祝日非表示の設定なら祝日を除外する
        return if (!showHolidays) {
            events.filter { !it.isHolidayCalendar }
        } else events
    }
}