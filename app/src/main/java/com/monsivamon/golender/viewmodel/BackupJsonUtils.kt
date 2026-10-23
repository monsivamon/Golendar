package com.monsivamon.golender.viewmodel

import com.monsivamon.golender.data.Event
import com.monsivamon.golender.data.LocalCalendar
import com.monsivamon.golender.data.LocalEvent
import com.monsivamon.golender.data.prefs.SettingsKeys
import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek

// バックアップの JSON 構築／復元に使う共通ユーティリティ
object BackupJsonUtils {

    // DataStore の設定値から settings オブジェクトを組み立てる
    fun buildSettingsJson(prefs: androidx.datastore.preferences.core.Preferences): JSONObject {
        val s = JSONObject()
        s.put("theme_mode", prefs[SettingsKeys.THEME] ?: "SYSTEM")
        s.put("week_start_day", prefs[SettingsKeys.WEEK_START] ?: "SUNDAY")
        s.put("calendar_mode", prefs[SettingsKeys.MODE] ?: "GOLENDAR")
        s.put("calendar_bg_color", prefs[SettingsKeys.BG_COLOR] ?: SettingsKeys.COLOR_UNSPECIFIED)

        val selectedCalendarsJson = prefs[SettingsKeys.SELECTED_CALENDARS]
        if (!selectedCalendarsJson.isNullOrBlank()) {
            s.put("selected_calendars", selectedCalendarsJson)
        }

        val dc = JSONObject()
        DayOfWeek.values().forEach { d ->
            val stored = prefs[SettingsKeys.dayColor(d)]
            // DataStore にキーが無い場合、日曜・土曜はアプリ既定色
            // （赤 / 青）を埋めておく。これをやらないと「未設定」が
            // UNSPECIFIED として復元され、既定色が消える。
            val value = stored ?: when (d) {
                DayOfWeek.SUNDAY   -> SettingsKeys.DEFAULT_SUNDAY_COLOR_ARGB.toString()
                DayOfWeek.SATURDAY -> SettingsKeys.DEFAULT_SATURDAY_COLOR_ARGB.toString()
                else               -> SettingsKeys.COLOR_UNSPECIFIED
            }
            dc.put(d.name, value)
        }
        s.put("day_colors", dc)
        return s
    }

    // Event リストを JSON 配列に変換する（calendarId も保存する）
    fun eventsToJson(events: List<Event>, evPhotos: JSONObject?): JSONArray {
        val arr = JSONArray()
        for (e in events) {
            val o = JSONObject()
            o.put("id", e.id); o.put("title", e.title)
            o.put("startTime", e.startTime); o.put("endTime", e.endTime)
            o.put("isAllDay", e.isAllDay); o.put("location", e.location)
            o.put("description", e.description); o.put("rrule", e.rrule ?: JSONObject.NULL)
            // 予定ごとの任意色（0 = 未設定）
            o.put("colorArgb", e.colorArgb)
            // 復元時にカレンダー振り分けを行うため calendarId を保存する
            o.put("calendarId", e.calendarId)
            o.put("calendarColorArgb", e.calendarColorArgb)
            o.put("calendarDisplayName", e.calendarDisplayName)
            if (evPhotos != null && evPhotos.has(e.id.toString())) {
                o.put("photos", evPhotos.getJSONArray(e.id.toString()))
            }
            arr.put(o)
        }
        return arr
    }

    // JSON 配列を LocalEvent リストに変換する（写真なし）
    fun jsonToLocalEvents(arr: JSONArray): List<LocalEvent> {
        val list = mutableListOf<LocalEvent>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            list.add(LocalEvent(0, o.getString("title"), o.getLong("startTime"),
                o.getLong("endTime"), o.getBoolean("isAllDay"),
                o.optString("location", ""), o.optString("description", ""),
                if (o.isNull("rrule")) null else o.getString("rrule"),
                o.optLong("calendarId", 0L), o.optInt("colorArgb", 0)))
        }
        return list
    }

    // JSON 配列を LocalEvent + 添付写真メタのペアに変換する（ZIP 復元用）
    fun jsonToLocalEventsWithPhotos(
        arr: JSONArray, extracted: Map<String, String>,
    ): List<Pair<LocalEvent, List<Pair<String, Int>>>> {
        val list = mutableListOf<Pair<LocalEvent, List<Pair<String, Int>>>>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val ev = LocalEvent(0, o.getString("title"), o.getLong("startTime"),
                o.getLong("endTime"), o.getBoolean("isAllDay"),
                o.optString("location", ""), o.optString("description", ""),
                if (o.isNull("rrule")) null else o.getString("rrule"),
                o.optLong("calendarId", 0L), o.optInt("colorArgb", 0))
            val photos = mutableListOf<Pair<String, Int>>()
            if (o.has("photos")) {
                val pa = o.getJSONArray("photos")
                for (j in 0 until pa.length()) {
                    val p = pa.getJSONObject(j)
                    val orig = p.getString("fileName")
                    val nn = extracted[orig] ?: continue
                    photos.add(nn to p.optInt("position", j))
                }
            }
            list.add(ev to photos)
        }
        return list
    }

    // LocalCalendar リストを JSON 配列に変換する
    fun localCalendarsToJson(calendars: List<LocalCalendar>): JSONArray {
        val arr = JSONArray()
        for (c in calendars) {
            val o = JSONObject()
            o.put("id", c.id); o.put("name", c.name)
            o.put("colorArgb", c.colorArgb); o.put("isVisible", c.isVisible)
            o.put("sortOrder", c.sortOrder); o.put("isDefault", c.isDefault)
            o.put("createdAt", c.createdAt)
            arr.put(o)
        }
        return arr
    }

    // JSON 配列を LocalCalendar リストに変換する
    fun jsonToLocalCalendars(arr: JSONArray): List<LocalCalendar> {
        val list = mutableListOf<LocalCalendar>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            list.add(LocalCalendar(
                id = o.optLong("id", 0),
                name = o.getString("name"),
                colorArgb = o.getInt("colorArgb"),
                isVisible = o.optBoolean("isVisible", true),
                sortOrder = o.optInt("sortOrder", 0),
                isDefault = o.optBoolean("isDefault", false),
                createdAt = o.optLong("createdAt", System.currentTimeMillis())
            ))
        }
        return list
    }
}