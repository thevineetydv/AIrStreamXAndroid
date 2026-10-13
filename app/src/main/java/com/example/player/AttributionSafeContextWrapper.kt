package com.example.player

import android.content.Context
import android.content.ContextWrapper

class AttributionSafeContextWrapper(base: Context) : ContextWrapper(base) {
    override fun getAttributionTag(): String? {
        return "play_audio"
    }

    override fun getApplicationContext(): Context {
        val appCtx = super.getApplicationContext()
        return AttributionSafeContextWrapper(appCtx)
    }
}
