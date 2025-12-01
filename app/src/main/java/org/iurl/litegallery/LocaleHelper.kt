package org.iurl.litegallery

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.preference.PreferenceManager

object LocaleHelper {

    private const val LANGUAGE_PREFERENCE_KEY = "app_language"

    const val LANGUAGE_SYSTEM = "system"
    const val LANGUAGE_ENGLISH = "en"
    const val LANGUAGE_TRADITIONAL_CHINESE = "zh_TW"
    const val LANGUAGE_SIMPLIFIED_CHINESE = "zh_CN"

    /**
     * Apply the saved locale preference to the app
     */
    fun applyLocale(context: Context) {
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        val language = preferences.getString(LANGUAGE_PREFERENCE_KEY, LANGUAGE_SYSTEM)
            ?: LANGUAGE_SYSTEM

        if (language == LANGUAGE_SYSTEM) {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
        } else {
            val locale = getLocaleFromLanguageCode(language)
            AppCompatDelegate.setApplicationLocales(locale)
        }
    }

    /**
     * Get the current language preference
     */
    fun getCurrentLanguage(context: Context): String {
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        return preferences.getString(LANGUAGE_PREFERENCE_KEY, LANGUAGE_SYSTEM)
            ?: LANGUAGE_SYSTEM
    }

    /**
     * Set a new language preference
     */
    fun setLanguage(context: Context, language: String) {
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        preferences.edit()
            .putString(LANGUAGE_PREFERENCE_KEY, language)
            .apply()
    }

    /**
     * Get display name for a language code
     */
    fun getLanguageDisplayName(context: Context, language: String): String {
        return when (language) {
            LANGUAGE_ENGLISH -> context.getString(R.string.language_english)
            LANGUAGE_TRADITIONAL_CHINESE -> context.getString(R.string.language_traditional_chinese)
            LANGUAGE_SIMPLIFIED_CHINESE -> context.getString(R.string.language_simplified_chinese)
            else -> context.getString(R.string.language_system_default)
        }
    }

    /**
     * Convert app language code to LocaleListCompat
     */
    private fun getLocaleFromLanguageCode(language: String): LocaleListCompat {
        return when (language) {
            LANGUAGE_ENGLISH -> LocaleListCompat.forLanguageTags("en")
            LANGUAGE_TRADITIONAL_CHINESE -> LocaleListCompat.forLanguageTags("zh-TW")
            LANGUAGE_SIMPLIFIED_CHINESE -> LocaleListCompat.forLanguageTags("zh-CN")
            else -> LocaleListCompat.getEmptyLocaleList()
        }
    }
}
