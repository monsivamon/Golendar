package com.monsivamon.golender.data.source

import com.monsivamon.golender.data.LocalEvent
import com.monsivamon.golender.data.holiday.HolidayCountry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.ZoneOffset

// 祝日データを外部 API から取得するソース。
// 日本は holidays-jp、それ以外の対象国は vacanza/holidays の静的 JSON を使う。
class HolidaySource {

    // 日本の祝日データ配信エンドポイント
    private val japanApiUrl = "https://holidays-jp.github.io/api/v1/date.json"

    // vacanza/holidays の国別静的 JSON（public カテゴリのみ）
    private fun worldApiUrl(country: HolidayCountry): String =
        "https://vacanza.github.io/holidays/downloads/ics/countries/" +
            "${country.iso}/ALL_en_US_public.json"

    // 指定国の祝日を取得する。取得に失敗した場合は null を返す
    // （呼び出し側は前回 DB に保存済みのデータをそのまま使い続ける）。
    suspend fun fetchHolidays(country: HolidayCountry): List<LocalEvent>? = withContext(Dispatchers.IO) {
        try {
            val url = if (country == HolidayCountry.JP) japanApiUrl else worldApiUrl(country)
            val jsonStr = httpGet(url) ?: return@withContext null
            val entries = parseEntries(jsonStr) ?: return@withContext null
            entries.map { (date, title) ->
                val startMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
                val endMillis = date.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
                LocalEvent(
                    title = title,
                    startTime = startMillis,
                    endTime = endMillis,
                    isAllDay = true,
                    location = "",
                    description = LocalEvent.DESCRIPTION_HOLIDAY,
                    rrule = null,
                    holidayCountry = country.iso,
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    // HTTP GET して本文を返す（失敗時は null）
    private fun httpGet(urlStr: String): String? {
        val connection = URL(urlStr).openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            connection.setRequestProperty("Accept", "application/json")
            if (connection.responseCode != 200) return null
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    // レスポンス JSON を (日付, 祝日名) のリストへ変換する。
    // holidays-jp は { "2026-01-01": "元日" }、vacanza は
    // [{ "date": "2026-01-01", "name": "New Year's Day" }] 形式。
    private fun parseEntries(jsonStr: String): List<Pair<LocalDate, String>>? {
        val trimmed = jsonStr.trim()
        return try {
            when {
                trimmed.startsWith("[") -> {
                    val arr = JSONArray(trimmed)
                    (0 until arr.length()).mapNotNull { i ->
                        val obj = arr.optJSONObject(i) ?: return@mapNotNull null
                        val dateStr = obj.optString("date")
                        val name = obj.optString("name")
                        if (dateStr.isBlank() || name.isBlank()) null
                        else LocalDate.parse(dateStr) to name
                    }
                }
                trimmed.startsWith("{") -> {
                    val obj = JSONObject(trimmed)
                    val keys = obj.keys()
                    buildList {
                        while (keys.hasNext()) {
                            val dateStr = keys.next()
                            val name = obj.optString(dateStr)
                            if (name.isNotBlank()) add(LocalDate.parse(dateStr) to name)
                        }
                    }
                }
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }
}
