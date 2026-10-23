package com.monsivamon.golender.data.holiday

import org.json.JSONArray

// 選択中の祝日対象国（ISO コード配列）と JSON 文字列を相互変換するユーティリティ
object HolidayCountryJson {

    // ISO コードのリストを JSON 文字列へ変換する
    fun toJson(isos: List<String>): String {
        val arr = JSONArray()
        isos.forEach { arr.put(it) }
        return arr.toString()
    }

    // JSON 文字列から ISO コードのリストへ復元する（解析失敗時は null）
    fun fromJson(raw: String?): List<String>? {
        if (raw.isNullOrBlank()) return null
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i -> arr.optString(i).takeIf { it.isNotBlank() } }
        } catch (_: Exception) {
            null
        }
    }
}
