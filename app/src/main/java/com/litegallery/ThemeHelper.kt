package com.litegallery

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.PreferenceManager

object ThemeHelper {
    
    private const val THEME_PREFERENCE_KEY = "theme_preference"
    private const val COLOR_THEME_PREFERENCE_KEY = "color_theme_preference"
    
    const val THEME_AUTO = "auto"
    const val THEME_LIGHT = "light"
    const val THEME_DARK = "dark"
    
    const val COLOR_BLUE = "blue"
    const val COLOR_GREEN = "green"
    const val COLOR_PURPLE = "purple"
    const val COLOR_ORANGE = "orange"
    const val COLOR_RED = "red"
    const val COLOR_TEAL = "teal"
    const val COLOR_INDIGO = "indigo"
    const val COLOR_PINK = "pink"
    
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
    
    fun getThemeResourceId(context: Context): Int {
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        val colorTheme = preferences.getString(COLOR_THEME_PREFERENCE_KEY, COLOR_BLUE) ?: COLOR_BLUE
        
        return when (colorTheme) {
            COLOR_GREEN -> R.style.Theme_LiteGallery_Green
            COLOR_PURPLE -> R.style.Theme_LiteGallery_Purple
            COLOR_ORANGE -> R.style.Theme_LiteGallery_Orange
            COLOR_RED -> R.style.Theme_LiteGallery_Red
            COLOR_TEAL -> R.style.Theme_LiteGallery_Teal
            COLOR_INDIGO -> R.style.Theme_LiteGallery_Indigo
            COLOR_PINK -> R.style.Theme_LiteGallery_Pink
            else -> R.style.Theme_LiteGallery_Blue
        }
    }
    
    fun applyColorTheme(activity: Activity) {
        val themeId = getThemeResourceId(activity)
        activity.setTheme(themeId)
    }
    
    fun setTheme(context: Context, theme: String) {
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        preferences.edit()
            .putString(THEME_PREFERENCE_KEY, theme)
            .apply()
        
        applyTheme(context)
    }
    
    fun setColorTheme(context: Context, colorTheme: String) {
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        preferences.edit()
            .putString(COLOR_THEME_PREFERENCE_KEY, colorTheme)
            .apply()
    }
    
    fun getCurrentTheme(context: Context): String {
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        return preferences.getString(THEME_PREFERENCE_KEY, THEME_AUTO) ?: THEME_AUTO
    }
    
    fun getCurrentColorTheme(context: Context): String {
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        return preferences.getString(COLOR_THEME_PREFERENCE_KEY, COLOR_BLUE) ?: COLOR_BLUE
    }
    
    fun getThemeDisplayName(context: Context, theme: String): String {
        return when (theme) {
            THEME_LIGHT -> context.getString(R.string.theme_light)
            THEME_DARK -> context.getString(R.string.theme_dark)
            else -> context.getString(R.string.theme_auto)
        }
    }
    
    fun getColorThemeDisplayName(context: Context, colorTheme: String): String {
        return when (colorTheme) {
            COLOR_GREEN -> context.getString(R.string.color_theme_green)
            COLOR_PURPLE -> context.getString(R.string.color_theme_purple)
            COLOR_ORANGE -> context.getString(R.string.color_theme_orange)
            COLOR_RED -> context.getString(R.string.color_theme_red)
            COLOR_TEAL -> context.getString(R.string.color_theme_teal)
            COLOR_INDIGO -> context.getString(R.string.color_theme_indigo)
            COLOR_PINK -> context.getString(R.string.color_theme_pink)
            else -> context.getString(R.string.color_theme_blue)
        }
    }
    
    fun getColorThemePreviewColor(context: Context, colorTheme: String): Int {
        return when (colorTheme) {
            COLOR_GREEN -> context.getColor(R.color.green_primary)
            COLOR_PURPLE -> context.getColor(R.color.purple_primary)
            COLOR_ORANGE -> context.getColor(R.color.orange_primary)
            COLOR_RED -> context.getColor(R.color.red_primary)
            COLOR_TEAL -> context.getColor(R.color.teal_primary)
            COLOR_INDIGO -> context.getColor(R.color.indigo_primary)
            COLOR_PINK -> context.getColor(R.color.pink_primary)
            else -> context.getColor(R.color.blue_primary)
        }
    }
}