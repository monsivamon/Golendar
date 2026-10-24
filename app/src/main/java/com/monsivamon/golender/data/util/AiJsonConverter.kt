package com.monsivamon.golender.data.util

import com.monsivamon.golender.data.LocalEvent
import com.monsivamon.golender.data.prefs.AppTimeZone
import org.json.JSONObject
import org.json.JSONArray
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

// AI が返した JSON 文字列を LocalEvent リストへ変換するユーティリティ
object AiJsonConverter {
    // 変換結果（イベント一覧と警告メッセージ）を保持するデータクラス
    data class Result(val events: List<LocalEvent>, val warnings: List<String>)

    // 生の AI 出力（JSON）を解析し LocalEvent リストを生成する
    fun convert(raw: String): Result {
        // コードフェンスを除去し前後の空白を削除する
        val cleaned = raw.replace(Regex("```(?:json)?\\s*([\\s\\S]*?)```"), "$1").trim()
        return try {
            // ルート JSON と events/warnings 配列を取得する
            val json = JSONObject(cleaned)
            val eventsArray = json.optJSONArray("events") ?: JSONArray()
            val warningsArray = json.optJSONArray("warnings") ?: JSONArray()
            val warnings = (0 until warningsArray.length()).map { warningsArray.getString(it) }.toMutableList()

            // 各イベント要素を LocalEvent に変換する（失敗要素はスキップ）
            val events = (0 until eventsArray.length()).mapNotNull { i ->
                val o = eventsArray.getJSONObject(i)
                try {
                    // タイトルを取得する（空なら「名称未設定」）
                    val title = o.optString("title").ifBlank { "名称未設定" }

                    // 開始日を解析する（不正・欠落なら要素ごとスキップ）
                    val dateStr = if (o.isNull("date")) null else o.optString("date").replace("/", "-").takeIf { it.isNotBlank() && it != "null" }
                    if (dateStr.isNullOrBlank()) return@mapNotNull null
                    val date = try { LocalDate.parse(dateStr) } catch (_: Exception) { return@mapNotNull null }

                    // 終了日を解析する（任意項目）
                    val endDateStr = if (o.isNull("end_date")) null else o.optString("end_date").replace("/", "-").takeIf { it.isNotBlank() && it != "null" }
                    val endDate = if (endDateStr.isNullOrBlank()) null else try { LocalDate.parse(endDateStr) } catch (_: Exception) { null }

                    // 終日フラグを取得する
                    val allDay = o.optBoolean("all_day", false)

                    // 開始・終了時刻の文字列を取得する（任意項目）
                    val startTimeStr = if (o.isNull("start_time")) null else o.optString("start_time").takeIf { it.isNotBlank() && it != "null" }
                    val endTimeStr = if (o.isNull("end_time")) null else o.optString("end_time").takeIf { it.isNotBlank() && it != "null" }

                    // 時刻文字列を LocalTime に変換する
                    val startTime = if (startTimeStr.isNullOrBlank()) null else try { LocalTime.parse(startTimeStr.padStart(5, '0')) } catch (_: Exception) { null }
                    val endTime = if (endTimeStr.isNullOrBlank()) null else try { LocalTime.parse(endTimeStr.padStart(5, '0')) } catch (_: Exception) { null }

                    // 場所と説明文を取得する
                    val location = if (o.isNull("location")) "" else o.optString("location", "")
                    val description = if (o.isNull("description")) "" else o.optString("description", "")

                    // 繰り返し設定を RRULE 文字列に変換する
                    val recObj = o.optJSONObject("recurrence")
                    val rrule = if (recObj != null && !recObj.isNull("freq")) {
                        // 大小文字の揺れ・前後空白を正規化する（AI 出力の表記揺れ対策）
                        val freq = recObj.getString("freq").trim().uppercase()
                        val bydayArr = recObj.optJSONArray("byday")
                        // BYDAY の有効コードのみを受け付け、表記揺れを正規化する
                        val validByday = setOf("MO", "TU", "WE", "TH", "FR", "SA", "SU")
                        val bydayList = if (bydayArr != null) (0 until bydayArr.length())
                            .map { bydayArr.getString(it).trim().uppercase() }
                            .filter { it in validByday }
                        else emptyList()
                        // UNTIL は日付 (YYYY-MM-DD / YYYY/MM/DD) として検証する
                        val untilStr = if (recObj.isNull("until")) "" else recObj.optString("until").trim()
                        val untilDate: LocalDate? = if (untilStr.isNotBlank() && untilStr != "null") {
                            try { LocalDate.parse(untilStr.replace("/", "-").take(10)) } catch (_: Exception) { null }
                        } else null
                        if (untilStr.isNotBlank() && untilStr != "null" && untilDate == null) {
                            warnings.add("IGNORED invalid UNTIL: $untilStr")
                        }
                        // 開始日より前の UNTIL は展開結果が 0 件確定のため、
                        // event ごとスキップする（UNTIL を外すと 5 年分の
                        // 無限繰り返し予定が生成されてしまう）。
                        if (untilDate != null && untilDate.isBefore(date)) {
                            warnings.add("SKIPPED event: UNTIL before start: $untilStr")
                            return@mapNotNull null
                        }

                        // freq に応じた基本ルールを構築する
                        val base = when (freq) {
                            "DAILY" -> "FREQ=DAILY"
                            "WEEKLY" -> {
                                val weekdays = listOf("MO", "TU", "WE", "TH", "FR")
                                if (bydayList.sorted() == weekdays.sorted()) RruleExpander.RRULE_WEEKDAYS
                                else if (bydayList.isNotEmpty()) "FREQ=WEEKLY;BYDAY=${bydayList.joinToString(",")}"
                                else "FREQ=WEEKLY"
                            }
                            "MONTHLY" -> "FREQ=MONTHLY"
                            "YEARLY" -> "FREQ=YEARLY"
                            else -> {
                                warnings.add("IGNORED invalid freq: $freq")
                                null
                            }
                        }
                        // UNTIL 指定があれば付与する
                        if (base != null && untilDate != null) "$base;UNTIL=${RruleExpander.formatUntil(untilDate)}" else base
                    } else null

                    // 終日／時間指定それぞれの方式でミリ秒へ変換する
                    val zone = AppTimeZone.effective()
                    val startMillis: Long
                    var endMillis: Long

                    if (allDay) {
                        // 終日予定は UTC 日付境界で表現する
                        startMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
                        endMillis = (endDate ?: date).plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
                    } else {
                        // 時間指定予定はシステム TZ で表現する（時刻未指定時は 0 時〜+1h）
                        val st = startTime ?: LocalTime.MIDNIGHT
                        val et = endTime ?: st.plusHours(1)
                        // 同じ日付で終了時刻が開始時刻より前なら日をまたぐ予定として翌日にする
                        val endLocalDate = resolveTimedEndDate(date, st, endDate ?: date, et)
                        startMillis = date.atTime(st).atZone(zone).toInstant().toEpochMilli()
                        endMillis = endLocalDate.atTime(et).atZone(zone).toInstant().toEpochMilli()
                    }

                    // 開始以前の終了は不正なので、終日は 1 日後、時間指定は 1 時間後に補正する
                    val endIsInvalid = if (allDay) endMillis <= startMillis else endMillis < startMillis
                    if (endIsInvalid) {
                        endMillis = startMillis + if (allDay) 86_400_000L else 3_600_000L
                        warnings.add("FIXED end before start")
                    }

                    // LocalEvent を生成して返す
                    LocalEvent(0, title, startMillis, endMillis, allDay, location, description, rrule)
                } catch (e: Exception) {
                    null
                }
            }
            Result(events, warnings.toList())
        } catch (e: Exception) {
            // JSON 全体の解析に失敗した場合
            Result(emptyList(), listOf("JSON parse error: ${e.message}"))
        }
    }
}