package com.monsivamon.golender.viewmodel

import android.content.Context
import android.net.Uri
import com.monsivamon.golender.R
import com.monsivamon.golender.data.CalendarRepository
import com.monsivamon.golender.data.SelectedCalendar
import com.monsivamon.golender.data.prefs.LanguageSetting

// バックアップ／復元のエクスポート・インポートと復元後の祝日再取得を担当する
class BackupCoordinator(
    private val context: Context,
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

    // 復元前のプレビュー情報を取得する
    suspend fun peek(uri: Uri): BackupManager.Metadata? = backupManager.peek(uri)

    // ファイルから予定をインポートし、成功時は祝日データを再取得する
    suspend fun import(
        uri: Uri,
        isAppend: Boolean,
        mode: CalendarMode,
        selectedCalendars: List<SelectedCalendar>,
        targetGoogleCalendarId: Long?,
        targetLocalCalendarId: Long?,
        selectedSourceIds: Set<Long>?,
        autoCreateMissingCalendars: Boolean,
        onProgress: (phase: String, current: Int, total: Int) -> Unit,
    ): BackupManager.Result {
        val options = BackupManager.RestoreOptions(
            targetGoogleCalendarId = targetGoogleCalendarId,
            targetLocalCalendarId = targetLocalCalendarId,
            selectedSourceCalendarIds = selectedSourceIds,
            autoCreateMissingCalendars = autoCreateMissingCalendars,
        )
        val result = backupManager.import(
            uri, isAppend, mode, selectedCalendars, options,
            onProgress = { p, c, t -> onProgress(p, c, t) },
        )
        if (result is BackupManager.Result.Success) {
            // 進行状況の文言も選択中のアプリ内言語で返す
            onProgress(LanguageSetting.wrap(context).getString(R.string.vm_progress_recreating_holidays), 0, 0)
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