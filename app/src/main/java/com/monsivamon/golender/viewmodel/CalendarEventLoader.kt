package com.monsivamon.golender.viewmodel

import android.content.Context
import com.monsivamon.golender.R
import com.monsivamon.golender.data.CalendarRepository
import com.monsivamon.golender.data.Event
import com.monsivamon.golender.data.LocalEvent
import com.monsivamon.golender.data.holiday.HolidayCountry
import com.monsivamon.golender.data.prefs.LanguageSetting
import com.monsivamon.golender.data.util.localStartDate

// カレンダーイベントの読み込み・マージ・フラグ付けを担当する
class CalendarEventLoader(private val baseContext: Context, private val repository: CalendarRepository) {
    // 文字列は取得のたびにアプリ内言語を差し込んで解決する
    // （データ生成は ViewModel スコープで行われ、Activity 再生成でも破棄されないため）。
    // matching キーは全ロケールで同一の日本語値のため判定ロジックには影響しない。
    private val context: Context
        get() = LanguageSetting.wrap(baseContext)

    // 同日に複数国の祝日がある場合のタイトル区切り
    private val HOLIDAY_TITLE_SEPARATOR = " / "

    // Google モードの予定を読み込み、ローカル祝日とマージして返す
    suspend fun loadGoogleEvents(
        start: Long, end: Long, visibleCalendarIds: List<Long>,
        colorMap: Map<Long, Int>, displayNameMap: Map<Long, String>,
        holidayCountries: List<HolidayCountry>,
    ): List<Event> {
        val calendarIds = visibleCalendarIds.ifEmpty { listOf(-1L) }
        val googleEvents = repository.getEventsForMonth(start, end, calendarIds)

        val selectedIso = holidayCountries.map { it.iso }.toSet()
        val officialHolidays = if (selectedIso.isEmpty()) emptyList()
        else repository.getLocalEventsForMonth(start, end)
            .filter { it.description == LocalEvent.DESCRIPTION_HOLIDAY }
            .filter { it.holidayCountries.any { iso -> iso in selectedIso } }

        val officialDates = officialHolidays.map { it.localStartDate() }.toSet()
        val birthdayKeyword = context.getString(R.string.vm_birthday_keyword)
        val emperorBirthdayKeyword = context.getString(R.string.vm_emperor_birthday_keyword)

        val mappedGoogleEvents = googleEvents.mapNotNull { event ->
            // Google の祝日カレンダー由来のイベントは誕生日として扱わない
            // （「King's Birthday」等の誤判定防止）
            val isBirthday = !event.isHolidayCalendar && (
                event.isBirthdayCalendar ||
                    (event.title.contains(birthdayKeyword) && !event.title.contains(emperorBirthdayKeyword)) ||
                    event.title.contains("Birthday", ignoreCase = true)
                )
            val holidayLike = !isBirthday &&
                (event.isHolidayCalendar || (event.isAllDay && event.isReadOnly))

            // ローカル祝日と同日の Google 祝日イベントは、ローカル側を優先して表示しない
            if (officialDates.isNotEmpty() && holidayLike && event.localStartDate() in officialDates) {
                return@mapNotNull null
            }

            var updatedEvent = event.copy(
                isBirthdayCalendar = isBirthday,
                calendarColorArgb = colorMap[event.calendarId],
                calendarDisplayName = displayNameMap[event.calendarId] ?: ""
            )

            // ローカル祝日を表示している場合、対象外の Google 祝日イベントは
            // 祝日扱いせず通常の終日予定として表示する（🗾 誤表示の防止）
            if (officialDates.isNotEmpty() && holidayLike) {
                updatedEvent = updatedEvent.copy(isHolidayCalendar = false)
            }

            // 祝日を 1 つも選択していない場合のみ、従来の文化イベント判定を維持する
            if (officialDates.isEmpty() && !isBirthday && holidayLike) {
                val isCultural = listOf(
                    context.getString(R.string.vm_cultural_tanabata),
                    context.getString(R.string.vm_cultural_valentine),
                    context.getString(R.string.vm_cultural_setsubun),
                    context.getString(R.string.vm_cultural_hinamatsuri),
                    context.getString(R.string.vm_cultural_mothers_day),
                    context.getString(R.string.vm_cultural_fathers_day),
                    context.getString(R.string.vm_cultural_halloween),
                    context.getString(R.string.vm_cultural_christmas),
                    context.getString(R.string.vm_cultural_omisoka),
                    context.getString(R.string.vm_cultural_ganjitsu),
                ).any { updatedEvent.title.contains(it) }
                if (isCultural || updatedEvent.isHolidayCalendar) {
                    updatedEvent = updatedEvent.copy(isCulturalEvent = true)
                }
            }
            updatedEvent
        }

        // ローカル祝日は Google 側の有無にかかわらず必ず表示する（同日分は 1 行にまとめる）
        val localHolidayRows = officialHolidays.map { localHoliday ->
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
                calendarDisplayName = context.getString(R.string.vm_holiday_display_name),
                holidayCountries = localHoliday.holidayCountries,
            )
        }

        return mergeHolidayRows(mappedGoogleEvents + localHolidayRows)
            .distinctBy { ev -> distinctKey(ev) }
            .sortedBy { it.startTime }
    }

    // Golendar モードのローカル予定を読み込む。
    // visibleCalendarIds が指定されれば可視カレンダーでフィルタする。
    // calendarColorMap / calendarNameMap を渡すと、各予定にカレンダーの色と名前を付与する。
    suspend fun loadLocalEvents(
        start: Long, end: Long, holidayCountries: List<HolidayCountry>,
        visibleCalendarIds: List<Long>? = null,
        calendarColorMap: Map<Long, Int> = emptyMap(),
        calendarNameMap: Map<Long, String> = emptyMap(),
    ): List<Event> {
        val selectedIso = holidayCountries.map { it.iso }.toSet()
        val birthdayKeyword = context.getString(R.string.vm_birthday_keyword)
        val emperorBirthdayKeyword = context.getString(R.string.vm_emperor_birthday_keyword)

        val mapped = repository.getLocalEventsForMonth(start, end, visibleCalendarIds).mapNotNull { event ->
            val isSystemHoliday = event.description == LocalEvent.DESCRIPTION_HOLIDAY
            // 祝日を 1 つも選択していない場合、および選択外の国の祝日は表示しない
            if (isSystemHoliday && (selectedIso.isEmpty() || event.holidayCountries.none { it in selectedIso })) {
                return@mapNotNull null
            }
            // 祝日（「King's Birthday」等）を誕生日と誤判定しない
            val isBirthday = !isSystemHoliday && (
                (event.title.contains(birthdayKeyword) && !event.title.contains(emperorBirthdayKeyword)) ||
                    event.title.contains("Birthday", ignoreCase = true)
                )

            // 祝日は「祝日」名義・アクセント色（文化イベント緑）で表示する
            val displayName: String
            val colorArgb: Int?
            if (isSystemHoliday) {
                displayName = context.getString(R.string.vm_holiday_display_name)
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
        }

        return mergeHolidayRows(mapped)
            .distinctBy { ev -> distinctKey(ev) }
            .sortedBy { it.startTime }
    }

    suspend fun loadEventsForSearch(
        start: Long, end: Long, calendarMode: CalendarMode,
        visibleCalendarIds: List<Long>, colorMap: Map<Long, Int>,
        displayNameMap: Map<Long, String>,
    ): List<Event> {
        return if (calendarMode == CalendarMode.GOOGLE) {
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
                        (ev.title.contains(
                            context.getString(R.string.vm_birthday_keyword)
                        ) && !ev.title.contains(context.getString(R.string.vm_emperor_birthday_keyword))) ||
                        ev.title.contains("Birthday", ignoreCase = true)
                    !isBirthday
                }
        }
    }

    // 同日の祝日を 1 行にまとめ、タイトルと国コードを統合する
    private fun mergeHolidayRows(events: List<Event>): List<Event> {
        val holidays = events.filter { it.isHolidayCalendar }
        if (holidays.isEmpty()) return events
        val others = events.filterNot { it.isHolidayCalendar }

        val merged = holidays.groupBy { it.localStartDate() }.map { (_, group) ->
            val first = group.first()
            val countries = group.flatMap { it.holidayCountries }.distinct()
                .sortedBy { iso -> HolidayCountry.fromIso(iso)?.ordinal ?: Int.MAX_VALUE }
            if (group.size == 1) {
                first.copy(holidayCountries = countries)
            } else {
                first.copy(
                    title = group.map { it.title }.distinct().joinToString(HOLIDAY_TITLE_SEPARATOR),
                    holidayCountries = countries,
                )
            }
        }
        return others + merged
    }

    // 重複排除用キー。祝日は同日 1 行に統合済みのため日付のみで判定する
    private fun distinctKey(ev: Event): String =
        if (ev.isHolidayCalendar) {
            "holiday_${ev.localStartDate()}"
        } else {
            "event_${ev.calendarId}_${ev.id}_${ev.startTime}"
        }
}
