package com.monsivamon.golender.ui.events

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.monsivamon.golender.data.Event
import com.monsivamon.golender.data.EventPhoto
import com.monsivamon.golender.viewmodel.CalendarViewModel
import kotlinx.coroutines.delay

// 予定編集画面 (Daily/Monthly/Weekly) で共通利用する写真ロードエフェクト。
// 3画面に重複していた以下の LaunchedEffect 群を1箇所へ集約する:
//   1) viewingEvent 変更時: 詳細ダイアログ用の写真一覧を取得
//   2) editingLoadToken 変更時: 編集ダイアログ用の写真一覧を最大3回リトライで取得
//      （一時失敗を「写真0枚」と誤確定させ、保存時に既存写真を全削除する事故を防ぐ）
//   3) editingEvent が null へ戻った瞬間: 前回の写真状態をクリア（写真残像防止）
//   4) dataVersion 変更時: イベントカードの📎バッジ用に写真枚数マップを再取得
@Composable
fun EventEditorPhotoEffects(
    viewModel: CalendarViewModel,
    viewingEvent: Event?,
    editingEvent: Event?,
    editingLoadToken: Long,
    dataVersion: Long,
    onDetailPhotosChange: (List<EventPhoto>) -> Unit,
    onEditingPhotosChange: (photos: List<EventPhoto>, loaded: Boolean) -> Unit,
    onPhotoCountsChange: (Map<Long, Int>) -> Unit,
    onEditingEventCleared: () -> Unit,
) {
    // 1) 詳細ダイアログ用の写真一覧
    LaunchedEffect(viewingEvent) {
        onDetailPhotosChange(viewingEvent?.let { viewModel.getPhotosForEvent(it.id) } ?: emptyList())
    }

    // 2) 編集ダイアログ用の写真一覧（3回リトライ）
    LaunchedEffect(editingLoadToken) {
        if (editingLoadToken == 0L) return@LaunchedEffect
        val ev = editingEvent ?: run {
            onEditingPhotosChange(emptyList(), true)
            return@LaunchedEffect
        }
        onEditingPhotosChange(emptyList(), false)
        var loaded = false
        var photos: List<EventPhoto> = emptyList()
        for (attempt in 1..3) {
            try {
                photos = viewModel.getPhotosForEvent(ev.id)
                loaded = true
                break
            } catch (_: Exception) {
                if (attempt < 3) delay(300L * attempt)
            }
        }
        onEditingPhotosChange(photos, loaded)
    }

    // 3) editingEvent が null へ戻った瞬間に写真状態をクリア（残像防止セーフティネット）
    LaunchedEffect(editingEvent) {
        if (editingEvent == null) onEditingEventCleared()
    }

    // 4) 📎バッジ用の写真枚数マップ
    LaunchedEffect(dataVersion) {
        val ids = viewModel.events.value.map { it.id }.filter { it > 0 }
        onPhotoCountsChange(
            if (ids.isEmpty()) emptyMap() else viewModel.getPhotoCountsForEvents(ids)
        )
    }
}