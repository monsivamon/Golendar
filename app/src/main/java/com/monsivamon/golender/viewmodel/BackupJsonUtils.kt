package com.monsivamon.golender.viewmodel

import com.monsivamon.golender.data.Event
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

        // 選択中カレンダー（JSON 文字列そのまま）を埋め込む
        val selectedCalendarsJson = prefs[SettingsKeys.SELECTED_CALENDARS]
        if (!selectedCalendarsJson.isNullOrBlank()) {
            s.put("selected_calendars", selectedCalendarsJson)
        }

        // 祝日表示の ON/OFF（既定 true）
        val showHolidays = prefs[SettingsKeys.SHOW_HOLIDAYS] ?: true
        s.put("show_holidays", showHolidays)

        // 曜日ごとの色を day_colors オブジェクトにまとめる
        val dc = JSONObject()
        DayOfWeek.values().forEach { d ->
            dc.put(d.name, prefs[SettingsKeys.dayColor(d)] ?: SettingsKeys.COLOR_UNSPECIFIED)
        }
        s.put("day_colors", dc)
        return s
    }

    // Event リストを JSON 配列に変換する（添付写真のメタ情報も埋め込む）
    fun eventsToJson(events: List<Event>, evPhotos: JSONObject?): JSONArray {
        val arr = JSONArray()
        for (e in events) {
            val o = JSONObject()
            o.put("id", e.id); o.put("title", e.title)
            o.put("startTime", e.startTime); o.put("endTime", e.endTime)
            o.put("isAllDay", e.isAllDay); o.put("location", e.location)
            o.put("description", e.description); o.put("rrule", e.rrule ?: JSONObject.NULL)
            // カレンダー色と表示名も保存する
            o.put("calendarColorArgb", e.calendarColorArgb)
            o.put("calendarDisplayName", e.calendarDisplayName)
            // 該当イベントに写真があれば photos を付与する
            if (evPhotos != null && evPhotos.has(e.id.toString())) {
                o.put("photos", evPhotos.getJSONArray(e.id.toString()))
            }
            arr.put(o)
        }
        return arr
    }

    // JSON 配列を LocalEvent リストに変換する（写真は扱わない）
    fun jsonToLocalEvents(arr: JSONArray): List<LocalEvent> {
        val list = mutableListOf<LocalEvent>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            list.add(LocalEvent(0, o.getString("title"), o.getLong("startTime"),
                o.getLong("endTime"), o.getBoolean("isAllDay"),
                o.optString("location", ""), o.optString("description", ""),
                if (o.isNull("rrule")) null else o.getString("rrule")))
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
            // 予定本体を組み立てる
            val ev = LocalEvent(0, o.getString("title"), o.getLong("startTime"),
                o.getLong("endTime"), o.getBoolean("isAllDay"),
                o.optString("location", ""), o.optString("description", ""),
                if (o.isNull("rrule")) null else o.getString("rrule"))
            // photos 配列を (新ファイル名, position) のリストへ変換する
            val photos = mutableListOf<Pair<String, Int>>()
            if (o.has("photos")) {
                val pa = o.getJSONArray("photos")
                for (j in 0 until pa.length()) {
                    val p = pa.getJSONObject(j)
                    val orig = p.getString("fileName")
                    // 実ファイル名が抽出済みマップにあるものだけ採用する
                    val nn = extracted[orig] ?: continue
                    photos.add(nn to p.optInt("position", j))
                }
            }
            list.add(ev to photos)
        }
        return list
    }
}