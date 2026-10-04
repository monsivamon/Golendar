package com.monsivamon.golender.viewmodel

import com.monsivamon.golender.data.Event

// 検索対象イベントの遅延ロードキャッシュとフィルタ処理を担当する
class SearchCacheManager {

    // キャッシュ本体（null = 未ロード）
    // IO ディスパッチャからの書き込みと Main スレッドからの読み出しが並行するため、
    // @Volatile で可視性を保証する
    @Volatile
    private var cache: List<Event>? = null

    // キャッシュが存在するかどうかを返す
    fun hasCache(): Boolean = cache != null

    // キャッシュを取得する（未ロード時は null）
    fun get(): List<Event>? = cache

    // キャッシュを保存する
    fun set(events: List<Event>) {
        cache = events
    }

    // キャッシュを無効化する
    fun invalidate() {
        cache = null
    }

    // クエリでフィルタした結果を返す（クエリ空なら空リスト）
    fun filter(source: List<Event>, query: String): List<Event> {
        if (query.isBlank()) return emptyList()
        return source.filter { it.title.contains(query, ignoreCase = true) }
    }
}