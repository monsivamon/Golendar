package com.monsivamon.golender.data.util

import android.content.Context
import android.content.Intent
import android.net.Uri

// 住所文字列を使って外部の地図アプリで場所を開くユーティリティ。
object MapUtils {

    // geo: Intent でマップアプリを開く。
    fun openInMapApp(context: Context, location: String) {
        if (location.isBlank()) return

        val query = buildMapQuery(location)

        val uri = Uri.parse("geo:0,0?q=${Uri.encode(query)}")
        val intent = Intent(Intent.ACTION_VIEW, uri)
        context.startActivity(Intent.createChooser(intent, "地図アプリを選択"))
    }

    // 保存された位置文字列から地図検索用のクエリを組み立てる。
    private fun buildMapQuery(location: String): String {
        if (location.contains(", ")) {
            return location.replace(", ", " ").trim()
        }

        if (location.contains("（")) {
            return location
                .replace("（", " ")
                .replace("）", "")
                .replace("  ", " ")
                .trim()
        }

        return location.trim()
    }
}