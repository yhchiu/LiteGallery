package com.litegallery

import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.PreferenceManager

object ThemeHelper {
    
    private const val THEME_PREFERENCE_KEY = "theme_preference"
    
    const val THEME_AUTO = "auto"
    const val THEME_LIGHT = "light"
    const val THEME_DARK = "dark"
    
    fun applyTheme(context: Context) {
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        val theme = preferences.getString(THEME_PREFERENCE_KEY, THEME_AUTO) ?: THEME_AUTO
        
        val nightMode = when (theme) {
            THEME_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            THEME_DARK -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        
        AppCompatDelegate.setDefaultNightMode(nightMode)
    }
    
    fun setTheme(context: Context, theme: String) {
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        preferences.edit()
            .putString(THEME_PREFERENCE_KEY, theme)
            .apply()
        
        applyTheme(context)
    }
    
    fun getCurrentTheme(context: Context): String {
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        return preferences.getString(THEME_PREFERENCE_KEY, THEME_AUTO) ?: THEME_AUTO
    }
    
    fun getThemeDisplayName(context: Context, theme: String): String {
        return when (theme) {
            THEME_LIGHT -> context.getString(R.string.theme_light)
            THEME_DARK -> context.getString(R.string.theme_dark)
            else -> context.getString(R.string.theme_auto)
        }
    }
}