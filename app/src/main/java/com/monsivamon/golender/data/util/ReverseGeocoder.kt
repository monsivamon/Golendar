package com.monsivamon.golender.data.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

// 逆ジオコーディングで座標から住所情報を取得するユーティリティ
object ReverseGeocoder {

    // 国土地理院の逆ジオコーダエンドポイント
    private const val GSI_ENDPOINT =
        "https://mreversegeocoder.gsi.go.jp/reverse-geocoder/LonLatToAddress"

    // Nominatim の逆ジオコーディングエンドポイント
    private const val NOMINATIM_ENDPOINT =
        "https://nominatim.openstreetmap.org/reverse"

    // Nominatim に送る User-Agent（識別用）
    private const val USER_AGENT = "Golendar/1.1 (https://github.com/monsivamon/Golendar)"

    // 逆ジオコーディング結果（住所文字列と POI 名）を保持するデータクラス
    data class Result(
        val address: String,
        val poiName: String? = null,
    ) {
        // 「POI 名, 住所」の形式に整形する
        fun displayText(): String = when {
            poiName.isNullOrBlank() -> address
            address.isBlank() -> poiName
            else -> "$poiName, $address"
        }
    }

    // 座標から詳細な住所情報を取得する（Nominatim → GSI の順にフォールバック）
    suspend fun reverseGeocodeDetailed(latitude: Double, longitude: Double): Result? =
        withContext(Dispatchers.IO) {
            val nominatim = tryNominatim(latitude, longitude)
            if (nominatim != null) return@withContext nominatim

            val gsi = tryGsi(latitude, longitude)
            if (gsi != null) return@withContext Result(address = gsi)

            null
        }

    // 住所文字列だけを返す旧 API（後方互換）
    suspend fun reverseGeocode(latitude: Double, longitude: Double): String? =
        reverseGeocodeDetailed(latitude, longitude)?.displayText()

    // Nominatim から詳細住所と POI 名を取得する
    private fun tryNominatim(latitude: Double, longitude: Double): Result? {
        return try {
            // 日本語住所と POI 名を得るためのクエリを組み立てる
            val url = URL(
                "$NOMINATIM_ENDPOINT?format=jsonv2" +
                        "&lat=$latitude&lon=$longitude" +
                        "&zoom=18" +
                        "&addressdetails=1" +
                        "&accept-language=ja"
            )
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", USER_AGENT)
            connection.connectTimeout = 5000
            connection.readTimeout = 5000

            if (connection.responseCode != 200) return null

            // JSON をパースして住所と POI 名を取り出す
            val jsonStr = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(jsonStr)

            val displayNameRaw = json.optString("display_name").trim()
            if (displayNameRaw.isBlank()) return null

            // display_name をカンマで分割し空白で再結合する
            val fullAddress = displayNameRaw
                .split(",")
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .joinToString(" ")

            val topName = json.optString("name").takeIf { it.isNotBlank() }

            // 住所の先頭が POI 名と重複する場合は除去する
            val address = if (topName != null && fullAddress.startsWith(topName)) {
                fullAddress.removePrefix(topName).trim()
            } else {
                fullAddress
            }

            Result(address = address, poiName = topName)
        } catch (_: Exception) {
            null
        }
    }

    // 国土地理院の逆ジオコーダから市区町村レベルの住所を取得する
    private fun tryGsi(latitude: Double, longitude: Double): String? {
        return try {
            val url = URL("$GSI_ENDPOINT?lat=$latitude&lon=$longitude")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000
            connection.readTimeout = 5000

            if (connection.responseCode != 200) return null

            // JSON をパースして市区町村名を取得する
            val jsonStr = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(jsonStr)
            val results = json.optJSONObject("results") ?: return null
            results.optString("lv01Nm").takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }
}