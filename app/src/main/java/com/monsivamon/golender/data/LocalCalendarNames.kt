package com.monsivamon.golender.data

import android.content.Context
import android.content.res.Configuration
import com.monsivamon.golender.R
import java.util.Locale

// 既定カレンダーの DB 上の名前センチネル。空文字は「locale 依存の既定名」を意味する。
const val DEFAULT_CALENDAR_NAME_SENTINEL = ""

// ローカルカレンダーの表示名を解決するユーティリティ群
object LocalCalendarNames {
    // バックアップ照合の対象に含める「アプリが対応する全ロケール」の言語タグ
    private val KNOWN_LANGUAGE_TAGS = listOf("ja", "en", "zh-CN", "ko")

    // DB の name を表示用の名前に解決する。
    // 既定カレンダーで名前が空文字の場合は locale の文字列リソースを返す。
    fun resolve(context: Context, calendar: LocalCalendar): String =
        if (calendar.isDefault && calendar.name == DEFAULT_CALENDAR_NAME_SENTINEL) {
            context.getString(R.string.default_calendar_name)
        } else {
            calendar.name
        }

    // 上書き保存系（バックアップ書き出し・共有テキストなど、DB 名そのものが
    // 必要な処理）向け。空文字センチネルを除外して locale 名に展開する。
    fun resolveExport(context: Context, calendar: LocalCalendar): String = resolve(context, calendar)

    // 既定カレンダーとして照合可能な全ロケール名の集合を返す。
    // バックアップは「書き出した端末の locale 名」で既定カレンダーが記録されるため、
    // 復元先の locale が違っても既定カレンダーと一致判定できるようにする。
    fun allDefaultNames(context: Context): Set<String> = buildSet {
        for (tag in KNOWN_LANGUAGE_TAGS) {
            val locale = Locale.forLanguageTag(tag)
            val config = Configuration(context.resources.configuration)
            config.setLocale(locale)
            val ctx = context.createConfigurationContext(config)
            add(ctx.getString(R.string.default_calendar_name))
        }
    }
}
