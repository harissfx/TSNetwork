package com.textsocial.app.util

import android.content.Context
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf

object ThemeManager {
    private const val PREFS_NAME = "app_theme_prefs"
    private const val KEY_THEME_MODE = "selected_theme_mode"

    const val MODE_SYSTEM = "system"
    const val MODE_LIGHT = "light"
    const val MODE_DARK = "dark"

    private val _themeMode = mutableStateOf(MODE_SYSTEM)
    val themeMode: State<String> get() = _themeMode

    fun init(context: Context) {
        _themeMode.value = getSelectedTheme(context)
    }

    fun getSelectedTheme(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_THEME_MODE, MODE_SYSTEM) ?: MODE_SYSTEM
    }

    fun setSelectedTheme(context: Context, mode: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_THEME_MODE, mode)
            .apply()
        _themeMode.value = mode
    }
}