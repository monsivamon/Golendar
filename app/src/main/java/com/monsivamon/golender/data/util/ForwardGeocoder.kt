package com.monsivamon.golender.data.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.abs

// 前方ジオコーディング（地名・施設名 → 座標）で場所を検索するユーティリティ。
// Nominatim の検索 API を使用する。公共 API への配慮として、
// 入力のたびに問い合わせず、検索操作が実行されたときだけリクエストする。
object ForwardGeocoder {

    // Nominatim の検索エンドポイント
    private const val NOMINATIM_ENDPOINT =
        "https://nominatim.openstreetmap.org/search"

    // Nominatim に送る User-Agent（識別用）
    private const val USER_AGENT = "Golendar/1.2 (https://github.com/monsivamon/Golendar)"

    // 取得する最大件数
    private const val MAX_RESULTS = 8

    // 通信タイムアウト（ミリ秒）
    private const val TIMEOUT_MS = 8000

    // 結果の広さが分からない場合のズーム
    private const val DEFAULT_RESULT_ZOOM = 17.0

    // 検索結果 1 件分
    data class Place(
        val name: String,
        val address: String,
        val latitude: Double,
        val longitude: Double,
        // 結果の広さに応じた推奨ズーム（市区町村なら広域、施設なら詳細）
        val zoom: Double,
    )

    // 検索の成否（0 件と通信失敗を区別する）
    sealed interface SearchResult {
        data class Success(val places: List<Place>) : SearchResult
        data object Failure : SearchResult
    }

    // 検索語から場所の一覧を取得する。
    // languageTag は Nominatim の accept-language に渡す（例: "ja", "en", "zh-CN"）。
    suspend fun search(query: String, languageTag: String): SearchResult =
        withContext(Dispatchers.IO) {
            val keyword = query.trim()
            if (keyword.isEmpty()) return@withContext SearchResult.Success(emptyList())

            try {
                val url = URL(
                    "$NOMINATIM_ENDPOINT?format=jsonv2" +
                            "&addressdetails=1" +
                            "&dedupe=1" +
                            "&limit=$MAX_RESULTS" +
                            "&accept-language=${encode(languageTag)}" +
                            "&q=${encode(keyword)}"
                )
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.setRequestProperty("User-Agent", USER_AGENT)
                connection.connectTimeout = TIMEOUT_MS
                connection.readTimeout = TIMEOUT_MS

                try {
                    if (connection.responseCode != 200) return@withContext SearchResult.Failure

                    val jsonStr = connection.inputStream.bufferedReader().use { it.readText() }
                    val array = JSONArray(jsonStr)
                    val places = buildList {
                        for (i in 0 until array.length()) {
                            val item = array.optJSONObject(i) ?: continue
                            parsePlace(item)?.let { add(it) }
                        }
                    }
                    SearchResult.Success(places)
                } finally {
                    connection.disconnect()
                }
            } catch (_: Exception) {
                SearchResult.Failure
            }
        }

    // URL クエリ用にエンコードする
    private fun encode(value: String): String =
        URLEncoder.encode(value, "UTF-8")

    // Nominatim の 1 件を表示用の Place に変換する
    private fun parsePlace(json: JSONObject): Place? {
        val latitude = json.optString("lat").toDoubleOrNull() ?: return null
        val longitude = json.optString("lon").toDoubleOrNull() ?: return null

        val displayName = json.optString("display_name").trim()
        if (displayName.isBlank()) return null

        // display_name をカンマで分割し空白で再結合する（逆ジオコーディングと同じ整形）
        val fullAddress = displayName
            .split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .joinToString(" ")

        // 施設名が無い場合は住所の先頭を名前として使う
        val firstSegment = displayName.split(",").firstOrNull()?.trim().orEmpty()
        val name = json.optString("name").trim().ifBlank { firstSegment }

        // 住所の先頭が名前と重複する場合は除去する
        val address = if (name.isNotBlank() && fullAddress.startsWith(name)) {
            fullAddress.removePrefix(name).trim()
        } else {
            fullAddress
        }

        return Place(
            name = name.ifBlank { fullAddress },
            address = address,
            latitude = latitude,
            longitude = longitude,
            zoom = zoomFor(json),
        )
    }

    // boundingbox の広さから推奨ズームを求める
    private fun zoomFor(json: JSONObject): Double {
        val box = json.optJSONArray("boundingbox") ?: return DEFAULT_RESULT_ZOOM
        if (box.length() < 4) return DEFAULT_RESULT_ZOOM

        val south = box.optString(0).toDoubleOrNull() ?: return DEFAULT_RESULT_ZOOM
        val north = box.optString(1).toDoubleOrNull() ?: return DEFAULT_RESULT_ZOOM
        val west = box.optString(2).toDoubleOrNull() ?: return DEFAULT_RESULT_ZOOM
        val east = box.optString(3).toDoubleOrNull() ?: return DEFAULT_RESULT_ZOOM

        val span = maxOf(abs(north - south), abs(east - west))
        return when {
            span > 2.0 -> 6.0
            span > 0.5 -> 9.0
            span > 0.1 -> 11.5
            span > 0.03 -> 13.5
            span > 0.008 -> 15.5
            else -> DEFAULT_RESULT_ZOOM
        }
    }
}
