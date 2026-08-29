package com.monsivamon.golender.data

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.provider.CalendarContract
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.TimeZone

class CalendarRepository(private val context: Context) {

    private val database = AppDatabase.getInstance(context)
    private val localEventDao = database.localEventDao()

    // カレンダーIDごとのアクセス権限をマップで取得
    private fun getCalendarAccessLevels(): Map<Long, Int> {
        val map = mutableMapOf<Long, Int>()
        val projection = arrayOf(CalendarContract.Calendars._ID, CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL)
        try {
            context.contentResolver.query(CalendarContract.Calendars.CONTENT_URI, projection, null, null, null)?.use { cursor ->
                while (cursor.moveToNext()) {
                    map[cursor.getLong(0)] = cursor.getInt(1)
                }
            }
        } catch (_: Exception) {}
        return map
    }

    // 月表示用にシステムカレンダーの予定を取得（読み取り専用判定付き）
    fun getEventsForMonth(startMillis: Long, endMillis: Long, calendarIds: List<Long>? = null): List<Event> {
        val events = mutableListOf<Event>()
        val accessLevels = getCalendarAccessLevels()

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
                val accessLevel = accessLevels[calId] ?: 500
                val isReadOnly = accessLevel < 500

                events.add(
                    Event(
                        id = cursor.getLong(idIdx),
                        title = cursor.getString(titleIdx) ?: "予定なし",
                        startTime = cursor.getLong(startIdx),
                        endTime = cursor.getLong(endIdx),
                        isAllDay = cursor.getInt(allDayIdx) == 1,
                        calendarId = calId,
                        location = cursor.getString(locIdx) ?: "",
                        description = cursor.getString(descIdx) ?: "",
                        rrule = cursor.getString(rruleIdx),
                        isReadOnly = isReadOnly
                    )
                )
            }
        }
        return events
    }

    // バックアップ用に指定アカウントの全イベントを取得
    fun getAllGoogleEvents(accountName: String?): List<Event> {
        val events = mutableListOf<Event>()
        val accessLevels = getCalendarAccessLevels()

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
                val accessLevel = accessLevels[calId] ?: 500
                val isReadOnly = accessLevel < 500

                // DTENDが欠落している場合は開始時刻を代用
                val start = cursor.getLong(startIdx)
                val end = cursor.getLong(endIdx).takeIf { it > 0 } ?: start

                events.add(
                    Event(
                        id = cursor.getLong(idIdx),
                        title = cursor.getString(titleIdx) ?: "予定なし",
                        startTime = start,
                        endTime = end,
                        isAllDay = cursor.getInt(allDayIdx) == 1,
                        calendarId = calId,
                        location = cursor.getString(locIdx) ?: "",
                        description = cursor.getString(descIdx) ?: "",
                        rrule = cursor.getString(rruleIdx),
                        isReadOnly = isReadOnly
                    )
                )
            }
        }
        return events
    }

    // 利用可能なカレンダーアカウントの一覧を取得
    fun getAccountNames(): List<String> {
        val cursor = context.contentResolver.query(CalendarContract.Calendars.CONTENT_URI, arrayOf(CalendarContract.Calendars.ACCOUNT_NAME), null, null, null)
        val accounts = mutableSetOf<String>()
        cursor?.use { while (it.moveToNext()) { accounts.add(it.getString(0)) } }
        return accounts.toList().sorted()
    }

    // アカウント名からカレンダーIDのリストを取得
    fun getCalendarIdsForAccount(accountName: String): List<Long> {
        val cursor = context.contentResolver.query(CalendarContract.Calendars.CONTENT_URI, arrayOf(CalendarContract.Calendars._ID), "${CalendarContract.Calendars.ACCOUNT_NAME} = ?", arrayOf(accountName), null)
        val ids = mutableListOf<Long>()
        cursor?.use { while (it.moveToNext()) { ids.add(it.getLong(0)) } }
        return ids
    }

    // 優先カレンダーIDを解決（プライマリ→最初のID→デフォルト1）
    private fun getTargetCalendarId(accountName: String?): Long {
        if (accountName != null) {
            context.contentResolver.query(CalendarContract.Calendars.CONTENT_URI, arrayOf(CalendarContract.Calendars._ID), "${CalendarContract.Calendars.ACCOUNT_NAME} = ? AND ${CalendarContract.Calendars.IS_PRIMARY} = 1", arrayOf(accountName), null)?.use { if (it.moveToFirst()) return it.getLong(0) }
            val ids = getCalendarIdsForAccount(accountName)
            if (ids.isNotEmpty()) return ids.first()
        }
        context.contentResolver.query(CalendarContract.Calendars.CONTENT_URI, arrayOf(CalendarContract.Calendars._ID), "${CalendarContract.Calendars.IS_PRIMARY} = 1", null, null)?.use { if (it.moveToFirst()) return it.getLong(0) }
        return 1L
    }

    // システムカレンダーに予定を新規作成
    fun insertEvent(title: String, startMillis: Long, endMillis: Long, isAllDay: Boolean, location: String, description: String, rrule: String?, accountName: String? = null): Long? {
        val values = ContentValues().apply {
            put(CalendarContract.Events.DTSTART, startMillis)
            put(CalendarContract.Events.DTEND, endMillis)
            put(CalendarContract.Events.TITLE, title)
            put(CalendarContract.Events.CALENDAR_ID, getTargetCalendarId(accountName))
            put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
            put(CalendarContract.Events.ALL_DAY, if (isAllDay) 1 else 0)
            put(CalendarContract.Events.EVENT_LOCATION, location)
            put(CalendarContract.Events.DESCRIPTION, description)
            if (rrule != null) put(CalendarContract.Events.RRULE, rrule)
        }
        return context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)?.lastPathSegment?.toLongOrNull()
    }

    // システムカレンダーの予定を更新
    fun updateEvent(eventId: Long, title: String, startMillis: Long, endMillis: Long, isAllDay: Boolean, location: String, description: String, rrule: String?): Boolean {
        val values = ContentValues().apply {
            put(CalendarContract.Events.DTSTART, startMillis)
            put(CalendarContract.Events.DTEND, endMillis)
            put(CalendarContract.Events.TITLE, title)
            put(CalendarContract.Events.ALL_DAY, if (isAllDay) 1 else 0)
            put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
            put(CalendarContract.Events.EVENT_LOCATION, location)
            put(CalendarContract.Events.DESCRIPTION, description)
            put(CalendarContract.Events.RRULE, rrule)
        }
        return context.contentResolver.update(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId), values, null, null) > 0
    }

    // システムカレンダーの予定を削除
    fun deleteEvent(eventId: Long): Boolean = context.contentResolver.delete(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId), null, null) > 0

    // ── ローカルDB処理 ──

    // ローカル予定を取得し、繰り返しルールを展開して期間内のインスタンスを生成
    suspend fun getLocalEventsForMonth(startMillis: Long, endMillis: Long): List<Event> {
        val allLocalEvents = localEventDao.getEventsInRange(startMillis, endMillis)
        val expandedEvents = mutableListOf<Event>()

        val queryStart = LocalDateTime.ofInstant(Instant.ofEpochMilli(startMillis), ZoneId.systemDefault())
        val queryEnd = LocalDateTime.ofInstant(Instant.ofEpochMilli(endMillis), ZoneId.systemDefault())

        for (local in allLocalEvents) {
            if (local.rrule.isNullOrEmpty()) {
                if (local.startTime <= endMillis && local.endTime >= startMillis) {
                    expandedEvents.add(Event(local.id, local.title, local.startTime, local.endTime, local.isAllDay, -1L, local.location, local.description, null, isReadOnly = false))
                }
            } else {
                var currentStart = LocalDateTime.ofInstant(Instant.ofEpochMilli(local.startTime), ZoneId.systemDefault())
                var currentEnd = LocalDateTime.ofInstant(Instant.ofEpochMilli(local.endTime), ZoneId.systemDefault())
                val limit = minOf(queryEnd, currentStart.plusYears(5))

                while (!currentStart.isAfter(limit)) {
                    if (!currentEnd.isBefore(queryStart) && !currentStart.isAfter(queryEnd)) {
                        expandedEvents.add(Event(
                            local.id, local.title,
                            currentStart.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                            currentEnd.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                            local.isAllDay, -1L, local.location, local.description, local.rrule, isReadOnly = false
                        ))
                    }
                    val nextStep = when (local.rrule) {
                        "FREQ=DAILY" -> 1L to java.time.temporal.ChronoUnit.DAYS
                        "FREQ=WEEKLY" -> 1L to java.time.temporal.ChronoUnit.WEEKS
                        "FREQ=MONTHLY" -> 1L to java.time.temporal.ChronoUnit.MONTHS
                        "FREQ=YEARLY" -> 1L to java.time.temporal.ChronoUnit.YEARS
                        else -> break
                    }
                    currentStart = currentStart.plus(nextStep.first, nextStep.second)
                    currentEnd = currentEnd.plus(nextStep.first, nextStep.second)
                }
            }
        }
        return expandedEvents
    }

    // ローカルDBに予定を新規作成
    suspend fun insertLocalEvent(title: String, startMillis: Long, endMillis: Long, isAllDay: Boolean, location: String, description: String, rrule: String?): Long {
        return localEventDao.insert(LocalEvent(0, title, startMillis, endMillis, isAllDay, location, description, rrule))
    }

    // ローカルDBの予定を更新
    suspend fun updateLocalEvent(eventId: Long, title: String, startMillis: Long, endMillis: Long, isAllDay: Boolean, location: String, description: String, rrule: String?) {
        localEventDao.update(LocalEvent(eventId, title, startMillis, endMillis, isAllDay, location, description, rrule))
    }

    // ローカルDBの予定を削除
    suspend fun deleteLocalEvent(eventId: Long) {
        localEventDao.deleteById(eventId)
    }

    // ローカルDBの全予定を取得（バックアップ用）
    suspend fun getAllLocalEvents(): List<LocalEvent> {
        return localEventDao.getAllEvents()
    }

    // ローカルDBを全削除してリストで復元
    suspend fun restoreLocalEvents(events: List<LocalEvent>) {
        localEventDao.deleteAll()
        localEventDao.insertAll(events)
    }

    // ローカルDBにリストを追記（重複は置き換え）
    suspend fun appendLocalEvents(events: List<LocalEvent>) {
        localEventDao.insertAll(events)
    }
}