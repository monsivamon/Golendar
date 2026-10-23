package com.monsivamon.golender.data.holiday

import java.util.Locale

// 祝日表示の対象国・地域（G7 + BRICS 原加盟国 + 韓国・台湾・香港）
// 並び順は設定画面の表示順を兼ねる
enum class HolidayCountry(val iso: String) {
    JP("JP"),
    US("US"),
    CA("CA"),
    GB("GB"),
    DE("DE"),
    FR("FR"),
    IT("IT"),
    BR("BR"),
    RU("RU"),
    IN("IN"),
    CN("CN"),
    ZA("ZA"),
    KR("KR"),
    TW("TW"),
    HK("HK");

    // 国旗絵文字（ISO 3166-1 alpha-2 の各文字を地域指示子へ変換する）
    val flag: String
        get() = iso.map { c ->
            val codePoint = 0x1F1E6 + (c - 'A')
            String(Character.toChars(codePoint))
        }.joinToString("")

    // アプリ表示言語に合わせた国名を返す（空なら ISO コードへフォールバック）
    fun displayName(locale: Locale): String = try {
        val region = Locale.Builder().setRegion(iso).build()
        region.getDisplayCountry(locale).ifBlank { iso }
    } catch (_: Exception) {
        iso
    }

    companion object {
        // ISO コードから対象国を解決する（対象外・不正値は null）
        fun fromIso(iso: String?): HolidayCountry? =
            iso?.let { code -> entries.firstOrNull { it.iso == code.uppercase() } }

        // ISO コードのリストを対象国のリストへ変換する（順序は定義順に正規化）
        fun fromIsoList(isos: List<String>): List<HolidayCountry> =
            isos.mapNotNull { fromIso(it) }.distinct().sortedBy { it.ordinal }

        // 任意の ISO コードから国旗絵文字を作る（対象外でも表示だけは可能にする）
        fun flagOf(iso: String): String =
            fromIso(iso)?.flag ?: ""
    }
}
