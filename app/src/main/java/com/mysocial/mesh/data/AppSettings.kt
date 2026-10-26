package com.mysocial.mesh.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages persisted user preferences and mesh operational settings.
 */
class AppSettings private constructor(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _syncIntervalSeconds = MutableStateFlow(
        prefs.getInt(KEY_SYNC_INTERVAL_SECONDS, DEFAULT_SYNC_INTERVAL_SECONDS)
    )
    val syncIntervalSeconds: StateFlow<Int> = _syncIntervalSeconds.asStateFlow()

    fun setSyncIntervalSeconds(seconds: Int) {
        val validSeconds = when (seconds) {
            5, 10, 30, 60, 300 -> seconds
            else -> DEFAULT_SYNC_INTERVAL_SECONDS
        }
        prefs.edit().putInt(KEY_SYNC_INTERVAL_SECONDS, validSeconds).apply()
        _syncIntervalSeconds.value = validSeconds
    }

    private val _isDarkMode = MutableStateFlow(
        prefs.getBoolean(KEY_DARK_MODE, false)
    )
    val isDarkMode: StateFlow<Boolean> = _isDarkMode.asStateFlow()

    fun setDarkMode(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DARK_MODE, enabled).apply()
        _isDarkMode.value = enabled
    }

    companion object {
        private const val PREFS_NAME = "mysocial_user_settings"
        const val KEY_SYNC_INTERVAL_SECONDS = "sync_interval_seconds"
        const val DEFAULT_SYNC_INTERVAL_SECONDS = 10
        const val KEY_DARK_MODE = "dark_mode"

        @Volatile
        private var instance: AppSettings? = null

        fun getInstance(context: Context): AppSettings {
            return instance ?: synchronized(this) {
                instance ?: AppSettings(context).also { instance = it }
            }
        }
    }
}
