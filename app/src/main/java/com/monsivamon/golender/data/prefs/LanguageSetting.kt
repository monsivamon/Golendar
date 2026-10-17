package com.monsivamon.golender.data.prefs

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import androidx.datastore.preferences.core.edit
import com.monsivamon.golender.data.dataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.util.Locale

// アプリ内言語選択の読み書きと Context への locale 適用を扱う。
// 「システムに追従」選択肢は廃止し、4 言語（ja / en / zh-CN / ko）のみとする。
// 方式: DataStore に選択値を保存し、MainActivity.attachBaseContext で
// createConfigurationContext により locale を差し込む（AppCompatDelegate を使わない手動方式）。
// 未設定・旧値（legacy）のときは端末の言語から対応言語を自動判定する。
object LanguageSetting {
    // 選択可能な言語タグ（並び順は設定画面の表示順）
    val LANGUAGES = listOf("ja", "en", "zh-CN", "ko")

    // 端末の言語から初期言語タグを判定する
    fun defaultTag(base: Context): String =
        when (base.resources.configuration.locales[0].language) {
            "ja" -> "ja"
            "ko" -> "ko"
            "zh" -> "zh-CN"
            else -> "en"
        }

    // 保存値の正規化。未設定・旧版の "system"・未知値は端末言語から初期言語へ解決する。
    fun normalize(tag: String?, base: Context): String =
        if (tag != null && tag in LANGUAGES) tag else defaultTag(base)

    // 現在の選択値を返す（未設定は端末言語から解決）
    suspend fun preference(context: Context): String =
        context.dataStore.data.first()[SettingsKeys.APP_LANGUAGE].let { normalize(it, context) }

    // 選択値を保存する。DataStore に加えて SharedPreferences へもミラーする。
    // ミラーは attachBaseContext / BroadcastReceiver など同期読み出し(O(1))用。
    suspend fun set(context: Context, tag: String) {
        context.dataStore.edit { it[SettingsKeys.APP_LANGUAGE] = tag }
        mirror(context).edit().putString(MIRROR_KEY, tag).apply()
    }

    // 選択値に応じて locale を差し込んだ Context を返す。
    // 選択値が不正な場合は defaultTag による初期値へフォールバックする。
    fun apply(base: Context, tag: String): Context {
        val locale = Locale.forLanguageTag(if (tag in LANGUAGES) tag else defaultTag(base))
        val config = Configuration(base.resources.configuration)
        val localeList = LocaleList(locale)
        config.setLocales(localeList)
        config.setLocale(locale)
        return base.createConfigurationContext(config)
    }

    // DataStore から選択値を読み出して即座に locale を差し込む（通知・ウィジェット向け）。
    fun wrap(context: Context): Context = apply(context, sync(context))

    // ---------- SharedPreferences ミラー ----------
    // ミラー用 prefs ファイル名とキー
    private const val PREFS_NAME = "app_language_mirror"
    private const val MIRROR_KEY = "tag"

    private fun mirror(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // 同期読み出し。日常パスでは SharedPreferences ミラー（O(1)）のみを参照するため
    // メインスレッドをブロックしない。ミラーが無い（プロセス初回 or 旧Verから更新）場合のみ
    // DataStore を runBlocking で読み取り、結果をミラーへ書き戻す。
    fun sync(context: Context): String {
        val prefs = mirror(context)
        val mirrored = prefs.getString(MIRROR_KEY, null)
        if (mirrored != null) {
            // 旧値（legacy 値）がミラーに残っているケースも normalize で解決する
            val resolved = normalize(mirrored, context)
            if (resolved != mirrored) {
                prefs.edit().putString(MIRROR_KEY, resolved).apply()
            }
            return resolved
        }
        // 初回のみ DataStore をブロッキング読み取りしてミラーへ書き戻す。
        val tag = runBlocking { context.dataStore.data.first()[SettingsKeys.APP_LANGUAGE] }
        val resolved = normalize(tag, context)
        prefs.edit().putString(MIRROR_KEY, resolved).apply()
        return resolved
    }
}
