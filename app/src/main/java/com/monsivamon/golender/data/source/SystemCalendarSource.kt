package com.monsivamon.golender.data.source

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.provider.CalendarContract
import com.monsivamon.golender.data.Event
import java.util.TimeZone

// カレンダーごとのアクセス権限・祝日・誕生日情報を保持するデータクラス。
data class CalendarInfo(val accessLevel: Int, val isHoliday: Boolean, val isBirthday: Boolean)

// システムカレンダー（CalendarContract）の読み書きを担当するソース。
class SystemCalendarSource(private val context: Context) {

    // カレンダーIDごとのアクセス権限・祝日・誕生日情報を取得する。
    private fun getCalendarInfo(): Map<Long, CalendarInfo> {
        val map = mutableMapOf<Long, CalendarInfo>()
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.NAME
        )
        try {
            context.contentResolver.query(CalendarContract.Calendars.CONTENT_URI, projection, null, null, null)?.use { cursor ->
                while (cursor.moveToNext()) {
                    val accessLevel = cursor.getInt(1)
                    val dispName = cursor.getString(2) ?: ""
                    val accountName = cursor.getString(3) ?: ""
                    val sysName = cursor.getString(4) ?: ""

                    val isHoliday = dispName.contains("祝日") ||
                            dispName.contains("休日") ||
                            dispName.contains("holiday", ignoreCase = true) ||
                            accountName.contains("holiday", ignoreCase = true) ||
                            sysName.contains("holiday", ignoreCase = true)

                    val isBirthday = dispName.contains("誕生日") ||
                            dispName.contains("birthdays", ignoreCase = true) ||
                            accountName.contains("#contacts@group.v.calendar.google.com") ||
                            sysName.contains("contacts", ignoreCase = true)

                    map[cursor.getLong(0)] = CalendarInfo(accessLevel, isHoliday, isBirthday)
                }
            }
        } catch (_: Exception) {}
        return map
    }

    // 月表示用にシステムカレンダーの予定を取得する（読み取り専用・祝日・誕生日フラグ付き）。
    fun getEventsForMonth(startMillis: Long, endMillis: Long, calendarIds: List<Long>? = null): List<Event> {
        val events = mutableListOf<Event>()
        val calInfo = getCalendarInfo()

        val builder = CalendarContract.Instances.CONTENT_URI.buildUpon()
        ContentUris.appendId(builder, startMillis)
        ContentUris.appendId(builder, endMillis)
        val uri = builder.build()

        val projection = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.CALENDAR_ID,
            CalendarContract.Instances.EVENT_LOCATION,
            CalendarContract.Instances.DESCRIPTION,
            CalendarContract.Instances.RRULE
        )

        val selectionBuilder = java.lang.StringBuilder("1=1")
        val selectionArgs = mutableListOf<String>()

        if (!calendarIds.isNullOrEmpty()) {
            selectionBuilder.append(" AND ${CalendarContract.Instances.CALENDAR_ID} IN (${calendarIds.joinToString(",") { "?" }})")
            selectionArgs.addAll(calendarIds.map { it.toString() })
        }

        context.contentResolver.query(uri, projection, selectionBuilder.toString(), selectionArgs.toTypedArray(), "${CalendarContract.Instances.BEGIN} ASC")?.use { cursor ->
            val idIdx = cursor.getColumnIndex(CalendarContract.Instances.EVENT_ID)
            val titleIdx = cursor.getColumnIndex(CalendarContract.Instances.TITLE)
            val startIdx = cursor.getColumnIndex(CalendarContract.Instances.BEGIN)
            val endIdx = cursor.getColumnIndex(CalendarContract.Instances.END)
            val allDayIdx = cursor.getColumnIndex(CalendarContract.Instances.ALL_DAY)
            val calendarIdIdx = cursor.getColumnIndex(CalendarContract.Instances.CALENDAR_ID)
            val locIdx = cursor.getColumnIndex(CalendarContract.Instances.EVENT_LOCATION)
            val descIdx = cursor.getColumnIndex(CalendarContract.Instances.DESCRIPTION)
            val rruleIdx = cursor.getColumnIndex(CalendarContract.Instances.RRULE)

            while (cursor.moveToNext()) {
                val calId = cursor.getLong(calendarIdIdx)
                val info = calInfo[calId] ?: CalendarInfo(500, false, false)
                val isReadOnly = info.accessLevel < 500
                val isHolidayCalendar = info.isHoliday
                val isBirthdayCalendar = info.isBirthday

                val rawDescription = cursor.getString(descIdx) ?: ""
                val finalDescription = if (isHolidayCalendar || rawDescription.contains("非表示")) "" else rawDescription

                events.add(
                    Event(
                        id = cursor.getLong(idIdx),
                        title = cursor.getString(titleIdx) ?: "予定なし",
                        startTime = cursor.getLong(startIdx),
                        endTime = cursor.getLong(endIdx),
                        isAllDay = cursor.getInt(allDayIdx) == 1,
                        calendarId = calId,
                        location = cursor.getString(locIdx) ?: "",
                        description = finalDescription,
                        rrule = cursor.getString(rruleIdx),
                        isReadOnly = isReadOnly,
                        isHolidayCalendar = isHolidayCalendar,
                        isBirthdayCalendar = isBirthdayCalendar
                    )
                )
            }
        }
        return events
    }

    // バックアップ用に指定アカウントの全イベントを取得する（祝日・誕生日フラグ付き）。
    fun getAllGoogleEvents(accountName: String?): List<Event> {
        val events = mutableListOf<Event>()
        val calInfo = getCalendarInfo()

        val projection = arrayOf(
            CalendarContract.Events._ID,
            CalendarContract.Events.TITLE,
            CalendarContract.Events.DTSTART,
            CalendarContract.Events.DTEND,
            CalendarContract.Events.ALL_DAY,
            CalendarContract.Events.CALENDAR_ID,
            CalendarContract.Events.EVENT_LOCATION,
            CalendarContract.Events.DESCRIPTION,
            CalendarContract.Events.RRULE
        )

        val selectionBuilder = java.lang.StringBuilder("${CalendarContract.Events.DELETED} != 1")
        val selectionArgs = mutableListOf<String>()

        if (accountName != null) {
            val calendarIds = getCalendarIdsForAccount(accountName)
            if (calendarIds.isNotEmpty()) {
                selectionBuilder.append(" AND ${CalendarContract.Events.CALENDAR_ID} IN (${calendarIds.joinToString(",") { "?" }})")
                selectionArgs.addAll(calendarIds.map { it.toString() })
            } else {
                return emptyList()
            }
        }

        context.contentResolver.query(
            CalendarContract.Events.CONTENT_URI,
            projection,
            selectionBuilder.toString(),
            selectionArgs.toTypedArray(),
            "${CalendarContract.Events.DTSTART} ASC"
        )?.use { cursor ->
            val idIdx = cursor.getColumnIndex(CalendarContract.Events._ID)
            val titleIdx = cursor.getColumnIndex(CalendarContract.Events.TITLE)
            val startIdx = cursor.getColumnIndex(CalendarContract.Events.DTSTART)
            val endIdx = cursor.getColumnIndex(CalendarContract.Events.DTEND)
            val allDayIdx = cursor.getColumnIndex(CalendarContract.Events.ALL_DAY)
            val calendarIdIdx = cursor.getColumnIndex(CalendarContract.Events.CALENDAR_ID)
            val locIdx = cursor.getColumnIndex(CalendarContract.Events.EVENT_LOCATION)
            val descIdx = cursor.getColumnIndex(CalendarContract.Events.DESCRIPTION)
            val rruleIdx = cursor.getColumnIndex(CalendarContract.Events.RRULE)

            while (cursor.moveToNext()) {
                val calId = cursor.getLong(calendarIdIdx)
                val info = calInfo[calId] ?: CalendarInfo(500, false, false)
                val isReadOnly = info.accessLevel < 500
                val isHolidayCalendar = info.isHoliday
                val isBirthdayCalendar = info.isBirthday

                val start = cursor.getLong(startIdx)
                val end = cursor.getLong(endIdx).takeIf { it > 0 } ?: start

                val rawDescription = cursor.getString(descIdx) ?: ""
                val finalDescription = if (isHolidayCalendar || rawDescription.contains("非表示")) "" else rawDescription

                events.add(
                    Event(
                        id = cursor.getLong(idIdx),
                        title = cursor.getString(titleIdx) ?: "予定なし",
                        startTime = start,
                        endTime = end,
                        isAllDay = cursor.getInt(allDayIdx) == 1,
                        calendarId = calId,
                        location = cursor.getString(locIdx) ?: "",
                        description = finalDescription,
                        rrule = cursor.getString(rruleIdx),
                        isReadOnly = isReadOnly,
                        isHolidayCalendar = isHolidayCalendar,
                        isBirthdayCalendar = isBirthdayCalendar
                    )
                )
            }
        }
        return events
    }

    // 利用可能なGoogleアカウント一覧を取得する（「@」を含む実アカウントのみ）。
    fun getAccountNames(): List<String> {
        val cursor = context.contentResolver.query(CalendarContract.Calendars.CONTENT_URI, arrayOf(CalendarContract.Calendars.ACCOUNT_NAME), null, null, null)
        val accounts = mutableSetOf<String>()
        cursor?.use {
            while (it.moveToNext()) {
                val accountName = it.getString(0) ?: ""
                if (accountName.contains("@")) {
                    accounts.add(accountName)
                }
            }
        }
        return accounts.toList().sorted()
    }

    // アカウント名に紐づくカレンダーIDリストを取得する。
    fun getCalendarIdsForAccount(accountName: String): List<Long> {
        val cursor = context.contentResolver.query(CalendarContract.Calendars.CONTENT_URI, arrayOf(CalendarContract.Calendars._ID), "${CalendarContract.Calendars.ACCOUNT_NAME} = ?", arrayOf(accountName), null)
        val ids = mutableListOf<Long>()
        cursor?.use { while (it.moveToNext()) { ids.add(it.getLong(0)) } }
        return ids
    }

    // 祝日・誕生日カレンダーのIDのみを取得する（特殊カレンダーの識別用）。
    fun getSpecialCalendarIds(): List<Long> {
        return getCalendarInfo().filter { it.value.isHoliday || it.value.isBirthday }.map { it.key }
    }

    // 予定作成に使う優先カレンダーIDを解決する（プライマリ→最初のID→デフォルト1）。
    private fun getTargetCalendarId(accountName: String?): Long {
        if (accountName != null) {
            context.contentResolver.query(CalendarContract.Calendars.CONTENT_URI, arrayOf(CalendarContract.Calendars._ID), "${CalendarContract.Calendars.ACCOUNT_NAME} = ? AND ${CalendarContract.Calendars.IS_PRIMARY} = 1", arrayOf(accountName), null)?.use { if (it.moveToFirst()) return it.getLong(0) }
            val ids = getCalendarIdsForAccount(accountName)
            if (ids.isNotEmpty()) return ids.first()
        }
        context.contentResolver.query(CalendarContract.Calendars.CONTENT_URI, arrayOf(CalendarContract.Calendars._ID), "${CalendarContract.Calendars.IS_PRIMARY} = 1", null, null)?.use { if (it.moveToFirst()) return it.getLong(0) }
        return 1L
    }

    // システムカレンダーに予定を新規作成する（終日予定はタイムゾーンをUTCに設定）。
    fun insertEvent(title: String, startMillis: Long, endMillis: Long, isAllDay: Boolean, location: String, description: String, rrule: String?, accountName: String? = null): Long? {
        val values = ContentValues().apply {
            put(CalendarContract.Events.DTSTART, startMillis)
            put(CalendarContract.Events.DTEND, endMillis)
            put(CalendarContract.Events.TITLE, title)
            put(CalendarContract.Events.CALENDAR_ID, getTargetCalendarId(accountName))
            put(CalendarContract.Events.EVENT_TIMEZONE, if (isAllDay) "UTC" else TimeZone.getDefault().id)
            put(CalendarContract.Events.ALL_DAY, if (isAllDay) 1 else 0)
            put(CalendarContract.Events.EVENT_LOCATION, location)
            put(CalendarContract.Events.DESCRIPTION, description)
            if (rrule != null) put(CalendarContract.Events.RRULE, rrule)
        }
        return context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)?.lastPathSegment?.toLongOrNull()
    }

    // システムカレンダーの予定を更新する（終日予定はタイムゾーンをUTCに設定）。
    fun updateEvent(eventId: Long, title: String, startMillis: Long, endMillis: Long, isAllDay: Boolean, location: String, description: String, rrule: String?): Boolean {
        val values = ContentValues().apply {
            put(CalendarContract.Events.DTSTART, startMillis)
            put(CalendarContract.Events.DTEND, endMillis)
            put(CalendarContract.Events.TITLE, title)
            put(CalendarContract.Events.ALL_DAY, if (isAllDay) 1 else 0)
            put(CalendarContract.Events.EVENT_TIMEZONE, if (isAllDay) "UTC" else TimeZone.getDefault().id)
            put(CalendarContract.Events.EVENT_LOCATION, location)
            put(CalendarContract.Events.DESCRIPTION, description)
            put(CalendarContract.Events.RRULE, rrule)
        }
        return context.contentResolver.update(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId), values, null, null) > 0
    }

    // システムカレンダーの予定を削除する。
    fun deleteEvent(eventId: Long): Boolean = context.contentResolver.delete(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId), null, null) > 0
}