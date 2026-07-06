package com.textsocial.app.util

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * Simple per-app language switcher.
 *
 * "system" -> follow the phone's language (no override).
 * "in"     -> force Bahasa Indonesia.
 * "en"     -> force English.
 *
 * Stored in a small, non-encrypted SharedPreferences file (separate from
 * EncryptedPreferencesManager) so it can be read as early as attachBaseContext(),
 * before ServiceLocator / the encrypted keystore-backed prefs are ready.
 */
object LocaleManager {
    private const val PREFS_NAME = "app_language_prefs"
    private const val KEY_LANGUAGE = "selected_language_tag"

    const val LANG_SYSTEM = "system"
    const val LANG_INDONESIAN = "in"
    const val LANG_ENGLISH = "en"

    fun getSelectedLanguage(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_LANGUAGE, LANG_SYSTEM) ?: LANG_SYSTEM
    }

    fun setSelectedLanguage(context: Context, languageTag: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LANGUAGE, languageTag)
            .apply()
    }

    /** Wraps [context] with the saved locale applied, or returns it unchanged for "system". */
    fun applyLocale(context: Context): Context {
        val tag = getSelectedLanguage(context)
        if (tag == LANG_SYSTEM) return context

        val locale = Locale(tag)
        Locale.setDefault(locale)

        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)

        return context.createConfigurationContext(config)
    }
}