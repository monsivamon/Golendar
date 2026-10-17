package com.monsivamon.golender.data.util

import android.content.Context
import com.monsivamon.golender.R

// カレンダー画像解析 AI へ渡すプロンプトテンプレートを提供するオブジェクト。
// 本文は言語リソース（strings_ai.xml の ai_prompt_template）に持たせ、locale ごとに切り替わる。
object AiPromptTemplate {
    // AI への指示文（解析ルール・出力スキーマ・入力タイプ別処理を含む）。
    // {{TODAY}} / {{TZ}} のプレースホルダは呼び出し側で置換する。
    fun promptTemplate(context: Context): String = context.getString(R.string.ai_prompt_template)
}
