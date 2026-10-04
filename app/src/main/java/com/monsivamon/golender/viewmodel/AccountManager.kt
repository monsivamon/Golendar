package com.monsivamon.golender.viewmodel

import com.monsivamon.golender.data.CalendarRepository

// 端末に登録された Google アカウント一覧の取得を担当する
class AccountManager(private val repository: CalendarRepository) {

    // アカウント一覧を取得する（SecurityException 時は空リスト）
    suspend fun load(): List<String> = try {
        repository.getAccountNames()
    } catch (_: SecurityException) {
        emptyList()
    }
}