package com.monsivamon.golender.viewmodel

import android.net.Uri
import com.monsivamon.golender.data.CalendarRepository
import com.monsivamon.golender.data.Event
import com.monsivamon.golender.data.EventPhoto
import com.monsivamon.golender.data.LocalEvent
import com.monsivamon.golender.data.SelectedCalendar
import com.monsivamon.golender.data.util.correctAllDayMillis
import com.monsivamon.golender.data.util.localStartDate
import com.monsivamon.golender.data.util.zone
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime

// 予定の追加・更新・削除・分割削除・AI 一括登録・写真取得を担当する
class EventCrudManager(private val repository: CalendarRepository) {

    // 予定を新規作成する。挿入先は targetCalendarId > 可視カレンダー先頭 > プライマリの順
    suspend fun add(
        mode: CalendarMode,
        selectedCalendars: List<SelectedCalendar>,
        title: String, startMillis: Long, endMillis: Long, isAllDay: Boolean,
        location: String, description: String, rrule: String?,
        newPhotoUris: List<Uri>,
        targetCalendarId: Long?,
        localCalendarId: Long? = null,
    ): Long {
        val (finalStart, finalEnd) = if (isAllDay) {
            val c = correctAllDayMillis(startMillis, endMillis); c.start to c.end
        } else startMillis to endMillis

        val isGoogle = mode == CalendarMode.GOOGLE
        val newEventId: Long = if (isGoogle) {
            val resolvedId = targetCalendarId
                ?: selectedCalendars.firstOrNull { it.isVisible }?.calendarId
            if (resolvedId != null) {
                repository.insertEventWithCalendarId(
                    title, finalStart, finalEnd, isAllDay,
                    location, description, rrule, resolvedId,
                ) ?: -1L
            } else {
                repository.insertEvent(
                    title, finalStart, finalEnd, isAllDay,
                    location, description, rrule, null,
                ) ?: -1L
            }
        } else {
            // 万一 localCalendarId が null/0 でも、既定 → 可視先頭 → 0L の順で解決する。
            // （0L は不可視扱いになるため、必ず既定カレンダーへ着地させる）
            val resolvedLocalId = localCalendarId?.takeIf { it > 0L }
                ?: repository.getDefaultLocalCalendar()?.id
                ?: repository.getVisibleLocalCalendars().firstOrNull()?.id
                ?: 0L
            repository.insertLocalEvent(
                title, finalStart, finalEnd, isAllDay,
                location, description, rrule, resolvedLocalId
            )
        }

        if (!isGoogle && newEventId > 0 && newPhotoUris.isNotEmpty()) {
            newPhotoUris.take(EventPhoto.MAX_PHOTOS_PER_EVENT).forEach { uri ->
                repository.attachPhotoToEvent(newEventId, uri)
            }
        }
        return newEventId
    }

    // 予定を更新する。写真の追加・削除も反映する
    suspend fun update(
        mode: CalendarMode,
        eventId: Long, title: String, startMillis: Long, endMillis: Long,
        isAllDay: Boolean, location: String, description: String, rrule: String?,
        newPhotoUris: List<Uri>, keptPhotoIds: List<Long>,
        localCalendarId: Long? = null,
    ) {
        val (finalStart, finalEnd) = if (isAllDay) {
            val c = correctAllDayMillis(startMillis, endMillis); c.start to c.end
        } else startMillis to endMillis

        val isGoogle = mode == CalendarMode.GOOGLE
        if (isGoogle) {
            repository.updateEvent(eventId, title, finalStart, finalEnd, isAllDay,
                location, description, rrule)
        } else {
            // 更新時も同様に、null/0 なら既定カレンダーへ解決してから書き戻す
            val resolvedLocalId = localCalendarId?.takeIf { it > 0L }
                ?: repository.getDefaultLocalCalendar()?.id
                ?: repository.getVisibleLocalCalendars().firstOrNull()?.id
                ?: 0L
            repository.updateLocalEvent(eventId, title, finalStart, finalEnd, isAllDay,
                location, description, rrule, resolvedLocalId)
            val existing = repository.getPhotosForEvent(eventId)
            existing.filter { it.id !in keptPhotoIds }.forEach { repository.removePhoto(it) }
            val room = (EventPhoto.MAX_PHOTOS_PER_EVENT - keptPhotoIds.size).coerceAtLeast(0)
            newPhotoUris.take(room).forEach { uri ->
                repository.attachPhotoToEvent(eventId, uri)
            }
        }
    }

    suspend fun delete(eventId: Long, mode: CalendarMode) {
        if (mode == CalendarMode.GOOGLE) repository.deleteEvent(eventId)
        else repository.deleteLocalEvent(eventId)
    }

    // 複数日予定から指定日だけを削除する。繰り返し・読み取り専用は対象外
    suspend fun splitAndDeleteDay(
        event: Event, dateToRemove: LocalDate,
        mode: CalendarMode, selectedCalendars: List<SelectedCalendar>,
    ) {
        if (event.rrule != null || event.isReadOnly) return
        val isGoogle = mode == CalendarMode.GOOGLE

        val zone = event.zone()
        val eventStart = event.localStartDate()
        val adjustedEndTime = if (event.endTime > event.startTime) event.endTime - 1 else event.endTime
        val eventEnd = LocalDateTime.ofInstant(Instant.ofEpochMilli(adjustedEndTime), zone).toLocalDate()
        if (eventStart == eventEnd) return

        val newStart2 = dateToRemove.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val newEnd1 = if (event.isAllDay) {
            dateToRemove.atStartOfDay(zone).toInstant().toEpochMilli()
        } else {
            dateToRemove.minusDays(1).atTime(23, 59, 59).atZone(zone).toInstant().toEpochMilli()
        }

        when (dateToRemove) {
            eventStart -> {
                if (isGoogle) repository.updateEvent(event.id, event.title, newStart2, event.endTime, event.isAllDay, event.location, event.description, null)
                else repository.updateLocalEvent(event.id, event.title, newStart2, event.endTime, event.isAllDay, event.location, event.description, null, event.calendarId)
            }
            eventEnd -> {
                if (isGoogle) repository.updateEvent(event.id, event.title, event.startTime, newEnd1, event.isAllDay, event.location, event.description, null)
                else repository.updateLocalEvent(event.id, event.title, event.startTime, newEnd1, event.isAllDay, event.location, event.description, null, event.calendarId)
            }
            else -> {
                if (isGoogle) {
                    repository.updateEvent(event.id, event.title, event.startTime, newEnd1, event.isAllDay, event.location, event.description, null)
                    val splitCalendarId = when {
                        event.calendarId > 0L -> event.calendarId
                        else -> selectedCalendars.firstOrNull { it.isVisible }?.calendarId
                    }
                    if (splitCalendarId != null) {
                        repository.insertEventWithCalendarId(
                            event.title, newStart2, event.endTime, event.isAllDay,
                            event.location, event.description, null, splitCalendarId,
                        )
                    } else {
                        repository.insertEvent(
                            event.title, newStart2, event.endTime, event.isAllDay,
                            event.location, event.description, null, null,
                        )
                    }
                } else {
                    repository.updateLocalEvent(event.id, event.title, event.startTime, newEnd1, event.isAllDay, event.location, event.description, null, event.calendarId)
                    repository.insertLocalEvent(event.title, newStart2, event.endTime, event.isAllDay, event.location, event.description, null, event.calendarId)
                }
            }
        }
    }

    // AI 解析結果をローカル DB に一括登録する。
    // targetCalendarId が指定されていればそれを最優先し、
    // 無ければ既定カレンダー → 可視先頭 → 0L の順で解決する。
    suspend fun addFromAi(events: List<LocalEvent>, targetCalendarId: Long? = null) {
        if (events.isEmpty()) return
        val targetId = targetCalendarId?.takeIf { it > 0L }
            ?: repository.getDefaultLocalCalendar()?.id
            ?: repository.getVisibleLocalCalendars().firstOrNull()?.id
            ?: 0L
        val fixed = events.map { it.copy(calendarId = targetId) }
        repository.appendLocalEvents(fixed)
    }

    suspend fun photos(eventId: Long): List<EventPhoto> =
        repository.getPhotosForEvent(eventId)
}