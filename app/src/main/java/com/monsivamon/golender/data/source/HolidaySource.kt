package com.monsivamon.golender.data.source

import com.monsivamon.golender.data.LocalEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.ZoneOffset

// 日本の祝日データを外部APIから取得するソース。
class HolidaySource {

    private val apiUrl = "https://holidays-jp.github.io/api/v1/date.json"

    // 祝日APIから全祝日を取得し、LocalEvent のリストとして返す。
    suspend fun fetchHolidays(): List<LocalEvent> = withContext(Dispatchers.IO) {
        try {
            val url = URL(apiUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000
            connection.readTimeout = 5000

            if (connection.responseCode != 200) return@withContext emptyList()

            val stream = connection.inputStream
            val jsonStr = stream.bufferedReader().use { it.readText() }
            val jsonObject = JSONObject(jsonStr)

            val holidays = mutableListOf<LocalEvent>()
            val keys = jsonObject.keys()
            while (keys.hasNext()) {
                val dateStr = keys.next()
                val title = jsonObject.getString(dateStr)

                val localDate = LocalDate.parse(dateStr)
                val startMillis = localDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
                val endMillis = localDate.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

                holidays.add(
                    LocalEvent(
                        title = title,
                        startTime = startMillis,
                        endTime = endMillis,
                        isAllDay = true,
                        location = "",
                        description = LocalEvent.DESCRIPTION_HOLIDAY,
                        rrule = null
                    )
                )
            }
            holidays
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
}