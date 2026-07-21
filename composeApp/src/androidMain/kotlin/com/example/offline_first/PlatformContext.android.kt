package com.example.offline_first

import android.content.Context

/**
 * Application [Context], captured once in [OfflineFirstApp.onCreate]. Several Android `actual`
 * implementations (Room, connectivity, settings/session storage) need a `Context` that the
 * shared `commonMain` contracts intentionally don't expose — this is the single seam for it.
 */
lateinit var androidAppContext: Context
    private set

internal fun initAndroidAppContext(context: Context) {
    androidAppContext = context.applicationContext
}
