package com.monsivamon.golender.viewmodel

import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.edit
import com.monsivamon.golender.data.CalendarRepository
import com.monsivamon.golender.data.Event
import com.monsivamon.golender.data.LocalEvent
import com.monsivamon.golender.data.dataStore
import com.monsivamon.golender.data.prefs.SettingsKeys
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek

// 予定と設定のJSONバックアップ／復元を担当するマネージャ。
class BackupManager(
    private val context: Context,
    private val repository: CalendarRepository,
) {
    // バックアップ／復元の結果を表す密封クラス。
    sealed class Result {
        data class Success(val message: String) : Result()
        data class Failure(val message: String) : Result()
    }

    // 設定と予定をJSON形式でエクスポートする（祝日データは除外）。
    suspend fun export(
        uri: Uri, calendarMode: CalendarMode, selectedAccount: String?,
    ): Result = withContext(Dispatchers.IO) {
        try {
            val prefs = context.dataStore.data.first()
            val root = JSONObject()
            root.put("settings", buildSettingsJson(prefs))

            val events = if (calendarMode == CalendarMode.GOOGLE) {
                repository.getAllGoogleEvents(selectedAccount)
                    .filter { !it.isHolidayCalendar }
            } else {
                repository.getAllLocalEvents()
                    .filter { it.description != LocalEvent.DESCRIPTION_HOLIDAY }
                    .map {
                        Event(
                            it.id, it.title, it.startTime, it.endTime, it.isAllDay,
                            LocalEvent.LOCAL_CALENDAR_ID, it.location, it.description, it.rrule,
                        )
                    }
            }
            root.put("events", eventsToJson(events))
            context.contentResolver.openOutputStream(uri)?.use {
                it.write(root.toString().toByteArray())
            }
            Result.Success("バックアップが完了しました")
        } catch (e: Exception) {
            Result.Failure("バックアップに失敗しました: ${e.message}")
        }
    }

    // JSONファイルから設定と予定をインポートする（追記または上書き）。
    suspend fun import(
        uri: Uri, isAppend: Boolean,
        calendarMode: CalendarMode, selectedAccount: String?,
    ): Result = withContext(Dispatchers.IO) {
        try {
            if (calendarMode == CalendarMode.GOOGLE && !isAppend) {
                return@withContext Result.Failure("Googleモードでは誤削除防止のため、追記のみ可能です")
            }
            val json = context.contentResolver.openInputStream(uri)?.use {
                it.bufferedReader().use { r -> r.readText() }
            } ?: return@withContext Result.Failure("ファイルが開けませんでした")

            val root = JSONObject(json)
            if (root.has("settings")) applySettingsJson(root.getJSONObject("settings"))
            if (root.has("events")) {
                val imported = jsonToLocalEvents(root.getJSONArray("events"))
                    .filter { it.description != LocalEvent.DESCRIPTION_HOLIDAY }

                if (calendarMode == CalendarMode.GOOGLE) {
                    for (ev in imported) {
                        repository.insertEvent(
                            ev.title, ev.startTime, ev.endTime, ev.isAllDay,
                            ev.location, ev.description, ev.rrule, selectedAccount,
                        )
                    }
                } else if (isAppend) repository.appendLocalEvents(imported)
                else repository.restoreLocalEvents(imported)
            }
            Result.Success(if (isAppend) "追記が完了しました" else "復元が完了しました")
        } catch (e: Exception) {
            Result.Failure("復元に失敗しました: 不正なファイルです")
        }
    }

    // DataStoreの設定をJSONオブジェクトに変換する。
    private fun buildSettingsJson(
        prefs: androidx.datastore.preferences.core.Preferences,
    ): JSONObject {
        val s = JSONObject()
        s.put("theme_mode", prefs[SettingsKeys.THEME] ?: "SYSTEM")
        s.put("week_start_day", prefs[SettingsKeys.WEEK_START] ?: "SUNDAY")
        s.put("calendar_mode", prefs[SettingsKeys.MODE] ?: "GOLENDAR")
        s.put("calendar_bg_color", prefs[SettingsKeys.BG_COLOR] ?: SettingsKeys.COLOR_UNSPECIFIED)
        val dc = JSONObject()
        DayOfWeek.values().forEach { day ->
            dc.put(day.name, prefs[SettingsKeys.dayColor(day)] ?: SettingsKeys.COLOR_UNSPECIFIED)
        }
        s.put("day_colors", dc)
        return s
    }

    // JSONの設定をDataStoreに反映する。
    private suspend fun applySettingsJson(s: JSONObject) {
        context.dataStore.edit { prefs ->
            if (s.has("theme_mode")) prefs[SettingsKeys.THEME] = s.getString("theme_mode")
            if (s.has("week_start_day")) prefs[SettingsKeys.WEEK_START] = s.getString("week_start_day")
            if (s.has("calendar_mode")) prefs[SettingsKeys.MODE] = s.getString("calendar_mode")
            if (s.has("calendar_bg_color")) prefs[SettingsKeys.BG_COLOR] = s.getString("calendar_bg_color")
            if (s.has("day_colors")) {
                val dc = s.getJSONObject("day_colors")
                DayOfWeek.values().forEach { day ->
                    if (dc.has(day.name)) {
                        prefs[SettingsKeys.dayColor(day)] = dc.getString(day.name)
                    }
                }
            }
        }
    }

    // 予定リストをJSON配列に変換する。
    private fun eventsToJson(events: List<Event>): JSONArray {
        val arr = JSONArray()
        for (e in events) {
            val o = JSONObject()
            o.put("title", e.title)
            o.put("startTime", e.startTime)
            o.put("endTime", e.endTime)
            o.put("isAllDay", e.isAllDay)
            o.put("location", e.location)
            o.put("description", e.description)
            o.put("rrule", e.rrule ?: JSONObject.NULL)
            arr.put(o)
        }
        return arr
    }

    // JSON配列をLocalEventリストに変換する（IDは0で自動採番）。
    private fun jsonToLocalEvents(arr: JSONArray): List<LocalEvent> {
        val list = mutableListOf<LocalEvent>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            list.add(
                LocalEvent(
                    id = 0,
                    title = o.getString("title"),
                    startTime = o.getLong("startTime"),
                    endTime = o.getLong("endTime"),
                    isAllDay = o.getBoolean("isAllDay"),
                    location = o.optString("location", ""),
                    description = o.optString("description", ""),
                    rrule = if (o.isNull("rrule")) null else o.getString("rrule"),
                )
            )
        }
        return list
    }
}