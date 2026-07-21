package com.example.offline_first.data.local

import com.russhwolf.settings.Settings
import com.russhwolf.settings.set

/** Creates the platform key-value store backing [SessionStore] (Android: SharedPreferences, iOS: NSUserDefaults). */
expect fun createSettings(): Settings

/**
 * Replaces the original `SharedPreferences`-based session bookkeeping in `MainActivity`
 * (`PREFS_NAME`/`KEY_LOGIN_TIME`/`KEY_LOCAL_AUTH_EMAIL`) with `multiplatform-settings`, keeping
 * the same keys, the same fields, and the same "elapsed > SESSION_DURATION_MS -> sign out" rule.
 */
class SessionStore(private val settings: Settings = createSettings()) {

    var loginTimeMillis: Long
        get() = settings.getLong(KEY_LOGIN_TIME, 0L)
        set(value) { settings[KEY_LOGIN_TIME] = value }

    var localAuthEmail: String?
        get() = settings.getStringOrNull(KEY_LOCAL_AUTH_EMAIL)
        set(value) {
            if (value != null) settings[KEY_LOCAL_AUTH_EMAIL] = value else settings.remove(KEY_LOCAL_AUTH_EMAIL)
        }

    fun clear() {
        settings.remove(KEY_LOGIN_TIME)
        settings.remove(KEY_LOCAL_AUTH_EMAIL)
    }

    companion object {
        const val SESSION_DURATION_MS = 60 * 60 * 1000L
        private const val KEY_LOGIN_TIME = "login_time"
        private const val KEY_LOCAL_AUTH_EMAIL = "local_auth_email"
    }
}
