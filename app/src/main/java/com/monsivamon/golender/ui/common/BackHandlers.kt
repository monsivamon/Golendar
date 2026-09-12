package com.monsivamon.golender.ui.common

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper

// ★Context から Activity を安全に取り出すユーティリティ
//   LocalContext.current が ContextWrapper で包まれている場合があるため
//   階層を順に辿って Activity を探す
fun Context.findActivity(): Activity? {
    var ctx: Context? = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}