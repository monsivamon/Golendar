package com.monsivamon.golender.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.action.ActionCallback
import com.monsivamon.golender.data.prefs.LanguageSetting

// ウィジェット上の「🔄」ボタン押下時に全ウィジェットを強制更新するアクション
class WidgetUpdateAction : ActionCallback {
    // ウィジェット更新アクションを実行する
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        // アプリ内言語設定を反映した locale Context を使って更新する
        val localized = LanguageSetting.wrap(context)
        // 日・週・月の 3 種類をすべて更新する
        val widgets = listOf(DayWidget(), WeekWidget(), MonthWidget())
        widgets.forEach { widget ->
            try {
                val manager = GlanceAppWidgetManager(localized)
                val glanceIds = manager.getGlanceIds(widget::class.java)
                // 同種のウィジェットが複数配置されていてもすべて更新する
                glanceIds.forEach { id ->
                    widget.update(localized, id)
                }
            } catch (_: Exception) {
                // エラー時はスキップ
            }
        }
    }
}