package com.monsivamon.golender.viewmodel

import com.monsivamon.golender.data.CalendarRepository
import com.monsivamon.golender.data.SelectedCalendar
import com.monsivamon.golender.data.SelectedCalendarJson
import com.monsivamon.golender.data.source.CalendarMeta

// Google カレンダーの選択・マージ・永続化を担当する
class CalendarSelectionManager(
    private val repository: CalendarRepository,
    private val store: SettingsDataStore,
) {

    // 実アカウント（@ を含む）のカレンダーメタを返す
    suspend fun googleCalendarMetas(): List<CalendarMeta> =
        repository.getAllCalendars().filter { it.accountName.contains("@") }

    // 全カレンダーのメタ情報を返す（復元先ピッカー等で使用）
    suspend fun allCalendarMetas(): List<CalendarMeta> =
        repository.getAllCalendars()

    // 選択カレンダーリストを永続化する
    suspend fun persist(list: List<SelectedCalendar>) {
        store.saveSelectedCalendarsJson(SelectedCalendarJson.toJson(list))
    }

    // フォールバック用に Golendar モード＋空リストを永続化する
    suspend fun persistFallback() {
        store.applyGolendarFallback()
    }

    // 現在のリストと端末の実カレンダーを突き合わせて更新後リストを返す
    // 変更がない場合は null を返す
    suspend fun refresh(current: List<SelectedCalendar>): List<SelectedCalendar>? {
        val metas = repository.getAllCalendars().filter { it.accountName.contains("@") }
        val currentIds = current.map { it.calendarId }.toSet()

        // 追加分（新規カレンダーは非表示で追加）
        val added = metas.filter { it.id !in currentIds }
            .map { SelectedCalendar(it.id, it.accountName, it.displayName, 0, false) }

        // 削除済みカレンダーを除去
        val validIds = metas.map { it.id }.toSet()
        val filtered = (current + added).filter { validIds.contains(it.calendarId) }

        return if (added.isNotEmpty() || filtered.size != current.size) filtered else null
    }

    // 旧アカウント名から選択カレンダーへの移行を原子的に実行し、移行後リストを返す
    suspend fun migrateFromOldAccount(accountName: String): List<SelectedCalendar> {
        val metas = repository.getAllCalendars()
            .filter { it.accountName == accountName && it.accountName.contains("@") }
        val selected = metas.map {
            SelectedCalendar(it.id, it.accountName, it.displayName, 0, true)
        }
        store.saveSelectedCalendarsJson(SelectedCalendarJson.toJson(selected))
        store.completeMigration()
        return selected
    }
}