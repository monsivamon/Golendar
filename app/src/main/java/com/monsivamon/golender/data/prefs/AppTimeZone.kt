package com.monsivamon.golender.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.edit
import com.monsivamon.golender.data.dataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

// 表示タイムゾーン固定の中核。
// DataStore に選択値（固定 ON/OFF・IANA ゾーン ID・通知固定 ON/OFF）を保存し、
// プロセス内キャッシュと SharedPreferences ミラーを保持することで
// ui / loader / scheduler / widget など全経路から同一の「表示基準ゾーン」を
// 同期参照できるようにする（LanguageSetting と同じ同期方式）。
// 固定 OFF のときは effective() が端末のシステムゾーンを返すため、
// v1.1.8 以前とまったく同じ表示・通知動作になる。
object AppTimeZone {
    // ミラー用 prefs ファイル名とキー
    private const val PREFS_NAME = "app_timezone_mirror"
    private const val MIRROR_PINNED = "pinned"
    private const val MIRROR_ZONE = "zone_id"
    private const val MIRROR_NOTIFY_PIN = "notify_pinned"

    // プロセス内キャッシュ（init/load/save で更新される）
    @Volatile private var loaded = false
    @Volatile private var pinned = false
    @Volatile private var zoneId: String? = null
    @Volatile private var notifyPinned = false

    private fun mirror(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // Application.onCreate から呼ぶ初期化。
    // ミラーがあれば O(1) で確定し、無い（v1.1.8 からの更新・プロセス初回）場合は
    // DataStore を 1 度だけブロッキング読み取りしてミラーへ書き戻す。
    fun init(context: Context) {
        if (loaded) return
        val prefs = mirror(context)
        if (prefs.contains(MIRROR_ZONE) || prefs.contains(MIRROR_PINNED)) {
            pinned = prefs.getBoolean(MIRROR_PINNED, false)
            zoneId = prefs.getString(MIRROR_ZONE, null)
            notifyPinned = prefs.getBoolean(MIRROR_NOTIFY_PIN, false)
            loaded = true
            return
        }
        // 初回のみ DataStore をブロッキング読み取り
        runBlocking { load(context) }
    }

    // DataStore から設定を読み出してキャッシュとミラーに反映する
    suspend fun load(context: Context) {
        try {
            val prefs = context.dataStore.data.first()
            pinned = prefs[SettingsKeys.TIMEZONE_PIN] ?: false
            zoneId = prefs[SettingsKeys.TIMEZONE_ID]
            notifyPinned = prefs[SettingsKeys.TIMEZONE_NOTIFY_PIN] ?: false
            // ミラーへ書き戻す（次回プロセス起動時の同期参照を O(1) 化する）
            mirror(context).edit()
                .putBoolean(MIRROR_PINNED, pinned)
                .putString(MIRROR_ZONE, zoneId)
                .putBoolean(MIRROR_NOTIFY_PIN, notifyPinned)
                .apply()
        } catch (_: Exception) {
            // 読み取り失敗時は未固定（システムゾーン）のままにする
            pinned = false; zoneId = null; notifyPinned = false
        }
        loaded = true
    }

    // 3 値（固定 ON/OFF・ゾーン ID・通知固定 ON/OFF）を保存する（DataStore + ミラー + キャッシュ）
    suspend fun save(context: Context, newPinned: Boolean, newZoneId: String, newNotifyPinned: Boolean) {
        val id = newZoneId.ifBlank { ZoneId.systemDefault().id }
        pinned = newPinned
        zoneId = id
        notifyPinned = newNotifyPinned
        try {
            context.dataStore.edit { prefs ->
                prefs[SettingsKeys.TIMEZONE_PIN] = pinned
                prefs[SettingsKeys.TIMEZONE_ID] = id
                prefs[SettingsKeys.TIMEZONE_NOTIFY_PIN] = notifyPinned
            }
            mirror(context).edit()
                .putBoolean(MIRROR_PINNED, pinned)
                .putString(MIRROR_ZONE, id)
                .putBoolean(MIRROR_NOTIFY_PIN, notifyPinned)
                .apply()
        } catch (_: Exception) {
            // 永続化失敗でもキャッシュは更新済みなので、今回の表示は反映される
        }
        loaded = true
    }

    // 表示タイムゾーンが固定されているか（キャッシュ未初期化時は false）
    fun isPinned(): Boolean = loaded && pinned

    // 表示基準ゾーン。未固定なら端末のシステムゾーンを返す。
    // 全ての表示・日付境界・新規入力の既定にこの値を使うことで一貫性を保つ。
    fun effective(): ZoneId {
        if (!loaded || !pinned) return ZoneId.systemDefault()
        return try { ZoneId.of(zoneId ?: return ZoneId.systemDefault()) }
        catch (_: Exception) { ZoneId.systemDefault() }
    }

    // 通知時刻も現地の時計に合わせるモードか（表示固定が ON のときのみ有効）
    fun isNotifyPinned(): Boolean = loaded && pinned && notifyPinned

    // 通知発火の基準インスタントを求める。
    // isNotifyPinned() = false のときは予定の実時刻（絶対時刻）から offset を引くだけ。
    // true のときは「固定表示ゾーンの壁時計時刻」を「端末の現地ゾーン」で解釈し直した時刻に
    // 置き換えてから offset を引く。海外滞在時に現地の時計が予定の時刻（10分前など）に
    // なった段階で通知する挙動になる。
    // 現地ゾーンと表示ゾーンが同一のケースでは何も変わらない。
    fun notifyTriggerMillis(startMillis: Long, offsetMillis: Long): Long {
        val base = if (isNotifyPinned()) {
            try {
                val wall = Instant.ofEpochMilli(startMillis).atZone(effective()).toLocalDateTime()
                ZonedDateTime.ofLocal(wall, ZoneId.systemDefault(), null).toInstant().toEpochMilli()
            } catch (_: Exception) { startMillis }
        } else startMillis
        return base - offsetMillis
    }

    // "Asia/Tokyo（UTC+09:00）" 形式のラベルを現在時点のオフセットで返す
    fun zoneLabel(id: String): String {
        val zone = try { ZoneId.of(id) } catch (_: Exception) { return id }
        val offset = ZonedDateTime.now(zone).offset
        // ZoneOffset.id は 0 時が "Z"、それ以外は "+09:00" 形式
        val text = if (offset.id == "Z") "+00:00" else offset.id
        return "$id（UTC$text）"
    }

    // 検証可能な IANA ゾーン ID かどうか
    fun isValidZone(id: String): Boolean = try { ZoneId.of(id); true } catch (_: Exception) { false }

    // 選択ダイアログ用: 世界中のゾーン ID を「現在の UTC オフセット順 → ID 順」で返す。
    // 重複オフセットのつらら切り（Etc/ 配下など）はそのまま列挙し、検索で絞り込んでもらう。
    fun allZoneIdsSorted(): List<String> {
        val now = ZonedDateTime.now()
        val zones = ZoneId.getAvailableZoneIds().mapNotNull { id ->
            runCatching {
                val off = now.withZoneSameInstant(ZoneId.of(id)).offset
                id to off.totalSeconds
            }.getOrNull()
        }
        return zones.sortedWith(compareBy({ it.second }, { it.first })).map { it.first }
    }
}
