package com.monsivamon.golender.ui.common

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper

// Context階層を辿ってActivityを取得する。
fun Context.findActivity(): Activity? {
    var ctx: Context? = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}