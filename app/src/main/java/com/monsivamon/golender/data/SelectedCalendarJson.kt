package com.monsivamon.golender.data

import org.json.JSONArray
import org.json.JSONObject

// SelectedCalendar リストと JSON 文字列を相互変換するユーティリティ
object SelectedCalendarJson {

    // リストを JSON 文字列へ変換する
    fun toJson(list: List<SelectedCalendar>): String {
        val arr = JSONArray()
        for (cal in list) {
            // 1 件分のオブジェクトを組み立てる
            val obj = JSONObject()
            obj.put("calendarId", cal.calendarId)
            obj.put("accountName", cal.accountName)
            obj.put("displayName", cal.displayName)
            obj.put("colorArgb", cal.colorArgb)
            obj.put("isVisible", cal.isVisible)
            arr.put(obj)
        }
        return arr.toString()
    }

    // JSON 文字列からリストへ復元する（空文字列・解析失敗時は空リスト）
    fun fromJson(raw: String): List<SelectedCalendar> {
        if (raw.isBlank()) return emptyList()
        val list = mutableListOf<SelectedCalendar>()
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    SelectedCalendar(
                        calendarId = obj.getLong("calendarId"),
                        accountName = obj.optString("accountName", ""),
                        displayName = obj.optString("displayName", ""),
                        colorArgb = obj.optInt("colorArgb", 0),
                        isVisible = obj.optBoolean("isVisible", true)
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }
}