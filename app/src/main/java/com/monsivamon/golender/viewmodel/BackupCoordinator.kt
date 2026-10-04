package com.monsivamon.golender.viewmodel

import android.net.Uri
import com.monsivamon.golender.data.CalendarRepository
import com.monsivamon.golender.data.SelectedCalendar

// バックアップ／復元のエクスポート・インポートと復元後の祝日再取得を担当する
class BackupCoordinator(
    private val backupManager: BackupManager,
    private val repository: CalendarRepository,
    private val store: SettingsDataStore,
) {

    // 予定と設定をエクスポートする
    suspend fun export(
        uri: Uri,
        mode: CalendarMode,
        selectedCalendars: List<SelectedCalendar>,
        includePhotos: Boolean,
        onProgress: (phase: String, current: Int, total: Int) -> Unit,
    ): BackupManager.Result = backupManager.export(
        uri, mode, selectedCalendars,
        includePhotos = includePhotos,
        onProgress = { p, c, t -> onProgress(p, c, t) },
    )

    // ファイルから予定をインポートし、成功時は祝日データを再取得する
    suspend fun import(
        uri: Uri,
        isAppend: Boolean,
        mode: CalendarMode,
        selectedCalendars: List<SelectedCalendar>,
        targetCalendarId: Long?,
        onProgress: (phase: String, current: Int, total: Int) -> Unit,
    ): BackupManager.Result {
        val result = backupManager.import(
            uri, isAppend, mode, selectedCalendars,
            targetCalendarId = targetCalendarId,
            onProgress = { p, c, t -> onProgress(p, c, t) },
        )
        if (result is BackupManager.Result.Success) {
            onProgress("祝日データを再取得中", 0, 0)
            val holidayOk = try { repository.fetchAndSaveHolidays() } catch (_: Exception) { false }
            try {
                store.updateLastHolidayFetch(
                    if (holidayOk) System.currentTimeMillis() else 0L
                )
            } catch (_: Exception) { }
        }
        return result
    }
}