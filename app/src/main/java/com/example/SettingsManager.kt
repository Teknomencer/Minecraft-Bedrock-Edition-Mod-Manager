package com.example

import android.content.Context
import android.content.SharedPreferences

class SettingsManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("mod_manager_settings", Context.MODE_PRIVATE)

    var themeMode: String
        get() = prefs.getString("theme_mode", "SYSTEM") ?: "SYSTEM"
        set(value) = prefs.edit().putString("theme_mode", value).apply()

    var autoScan: Boolean
        get() = prefs.getBoolean("auto_scan", true)
        set(value) = prefs.edit().putBoolean("auto_scan", value).apply()

    var sortNewestFirst: Boolean
        get() = prefs.getBoolean("sort_newest_first", true)
        set(value) = prefs.edit().putBoolean("sort_newest_first", value).apply()
}
