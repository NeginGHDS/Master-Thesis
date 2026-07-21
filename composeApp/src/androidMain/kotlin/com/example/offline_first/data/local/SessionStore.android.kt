package com.example.offline_first.data.local

import android.content.Context
import com.example.offline_first.androidAppContext
import com.russhwolf.settings.Settings
import com.russhwolf.settings.SharedPreferencesSettings

private const val PREFS_NAME = "session_prefs"

actual fun createSettings(): Settings =
    SharedPreferencesSettings(androidAppContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE))
