package org.iurl.litegallery

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import org.iurl.litegallery.theme.CustomThemeStore
import org.json.JSONException
import org.json.JSONObject
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.*

class SettingsExportImportHelper(private val context: Context) {

    companion object {
        private const val SETTINGS_VERSION = 2
        private const val APP_NAME = "LiteGallery"
        private const val DEFAULT_PREFERENCES_JSON_KEY = "preferences"
        private const val ACTION_BAR_PREFERENCES_JSON_KEY = "action_bar_preferences"

        private enum class PreferenceType {
            STRING,
            BOOLEAN,
            INT,
            FLOAT,
            STRING_SET
        }

        // All known preference keys plus their expected storage types.
        private val DEFAULT_PREFERENCE_TYPES = mapOf(
            // Display Settings
            "default_sort_order" to PreferenceType.STRING,
            "default_view_mode" to PreferenceType.STRING,
            "remember_folder_view_mode" to PreferenceType.BOOLEAN,
            "last_folder_view_mode" to PreferenceType.STRING,
            "filename_max_lines" to PreferenceType.STRING,
            "zoom_max_scale" to PreferenceType.STRING,

            // Video Gesture Settings
            "video_single_tap_action" to PreferenceType.STRING,
            "video_double_tap_action" to PreferenceType.STRING,
            "video_left_swipe_up_action" to PreferenceType.STRING,
            "video_left_swipe_down_action" to PreferenceType.STRING,
            "video_right_swipe_up_action" to PreferenceType.STRING,
            "video_right_swipe_down_action" to PreferenceType.STRING,

            // Quick Rename Settings
            "rename_history_count" to PreferenceType.STRING,
            "rename_default_sort" to PreferenceType.STRING,

            // General Settings
            "app_language" to PreferenceType.STRING,
            "theme_preference" to PreferenceType.STRING,
            "theme_pack_preference" to PreferenceType.STRING,
            "restore_brightness" to PreferenceType.BOOLEAN,
            "remember_video_brightness" to PreferenceType.BOOLEAN,
            "saved_video_brightness" to PreferenceType.FLOAT,
            "restore_volume" to PreferenceType.BOOLEAN,
            "enable_playback_diagnostics" to PreferenceType.BOOLEAN,
            "external_folder_access_prompt_enabled" to PreferenceType.BOOLEAN,
            "advanced_full_storage_mode" to PreferenceType.BOOLEAN,
            "trash_retention_days" to PreferenceType.STRING
        )

        private val CUSTOM_THEME_PREFERENCES_JSON_KEY = "custom_theme_preferences"

        private val ACTION_BAR_PREFERENCE_TYPES = mapOf(
            ActionBarPreferences.KEY_ORDER to PreferenceType.STRING,
            ActionBarPreferences.KEY_VISIBLE to PreferenceType.STRING
        )

        private val CUSTOM_THEME_PREFERENCE_TYPES = mapOf(
            CustomThemeStore.KEY_BG to PreferenceType.INT,
            CustomThemeStore.KEY_SURFACE to PreferenceType.INT,
            CustomThemeStore.KEY_CARD to PreferenceType.INT,
            CustomThemeStore.KEY_TEXT to PreferenceType.INT,
            CustomThemeStore.KEY_DIM to PreferenceType.INT,
            CustomThemeStore.KEY_FAINT to PreferenceType.INT,
            CustomThemeStore.KEY_LINE to PreferenceType.INT,
            CustomThemeStore.KEY_ACCENT to PreferenceType.INT,
            CustomThemeStore.KEY_ON_ACCENT to PreferenceType.INT,
            CustomThemeStore.KEY_FONT to PreferenceType.STRING,
            CustomThemeStore.KEY_CORNER to PreferenceType.STRING,
            CustomThemeStore.KEY_MODE to PreferenceType.STRING,
            CustomThemeStore.KEY_INITIALIZED to PreferenceType.BOOLEAN
        )
    }

    private val sharedPreferences: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
    private val actionBarPreferences: SharedPreferences =
        context.getSharedPreferences(ActionBarPreferences.PREFS_NAME, Context.MODE_PRIVATE)
    private val customThemePreferences: SharedPreferences =
        context.getSharedPreferences(CustomThemeStore.PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Generate default export filename
     */
    fun generateDefaultFilename(): String {
        val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
        val timestamp = dateFormat.format(Date())
        return "${APP_NAME}_settings_$timestamp.json"
    }

    /**
     * Export all settings to JSON format
     */
    fun exportSettings(outputStream: OutputStream): Boolean {
        return try {
            val settingsJson = JSONObject().apply {
                put("app_name", APP_NAME)
                put("export_version", SETTINGS_VERSION)
                put("export_timestamp", System.currentTimeMillis())
                put(
                    DEFAULT_PREFERENCES_JSON_KEY,
                    exportPreferenceObject(sharedPreferences, DEFAULT_PREFERENCE_TYPES)
                )
                put(
                    ACTION_BAR_PREFERENCES_JSON_KEY,
                    exportPreferenceObject(actionBarPreferences, ACTION_BAR_PREFERENCE_TYPES)
                )
                put(
                    CUSTOM_THEME_PREFERENCES_JSON_KEY,
                    exportPreferenceObject(customThemePreferences, CUSTOM_THEME_PREFERENCE_TYPES)
                )
            }

            outputStream.write(settingsJson.toString(2).toByteArray())
            outputStream.flush()
            true
        } catch (e: Exception) {
            android.util.Log.e("SettingsExport", "Failed to export settings", e)
            false
        }
    }

    /**
     * Import settings from JSON format with version compatibility
     * Returns Pair<imported_count, skipped_count>
     */
    fun importSettings(inputStream: InputStream): Pair<Int, Int> {
        return try {
            val jsonContent = inputStream.bufferedReader().use { it.readText() }
            val settingsJson = JSONObject(jsonContent)

            // Validate file format
            if (!settingsJson.has("app_name") || settingsJson.getString("app_name") != APP_NAME) {
                throw IllegalArgumentException("Invalid settings file - not a $APP_NAME settings file")
            }

            val exportVersion = settingsJson.optInt("export_version", 1)
            if (exportVersion > SETTINGS_VERSION) {
                android.util.Log.w("SettingsImport", "Settings file is from a newer version ($exportVersion > $SETTINGS_VERSION)")
            }

            val (defaultImportedCount, defaultSkippedCount) = importPreferenceObject(
                settingsJson.optJSONObject(DEFAULT_PREFERENCES_JSON_KEY),
                sharedPreferences,
                DEFAULT_PREFERENCE_TYPES
            )
            val (actionBarImportedCount, actionBarSkippedCount) = importPreferenceObject(
                settingsJson.optJSONObject(ACTION_BAR_PREFERENCES_JSON_KEY),
                actionBarPreferences,
                ACTION_BAR_PREFERENCE_TYPES
            )

            val customThemeJson = settingsJson.optJSONObject(CUSTOM_THEME_PREFERENCES_JSON_KEY)
            val (customImportedCount, customSkippedCount) = importPreferenceObject(
                customThemeJson,
                customThemePreferences,
                CUSTOM_THEME_PREFERENCE_TYPES,
                clearBeforeImport = customThemeJson != null
            )
            if (customThemeJson != null) {
                CustomThemeStore.notifyExternalChange()
            }

            Pair(
                defaultImportedCount + actionBarImportedCount + customImportedCount,
                defaultSkippedCount + actionBarSkippedCount + customSkippedCount
            )

        } catch (e: JSONException) {
            android.util.Log.e("SettingsImport", "Invalid JSON format", e)
            throw IllegalArgumentException("Invalid settings file format")
        } catch (e: Exception) {
            android.util.Log.e("SettingsImport", "Failed to import settings", e)
            throw e
        }
    }

    private fun exportPreferenceObject(
        preferences: SharedPreferences,
        preferenceTypes: Map<String, PreferenceType>
    ): JSONObject {
        val preferencesJson = JSONObject()
        val allPrefs = preferences.all

        for ((key, value) in allPrefs) {
            if (!preferenceTypes.containsKey(key)) continue

            when (value) {
                is String -> preferencesJson.put(key, value)
                is Boolean -> preferencesJson.put(key, value)
                is Int -> preferencesJson.put(key, normalizeIntForStorage(key, value))
                is Long -> {
                    if (preferenceTypes[key] == PreferenceType.INT) {
                        preferencesJson.put(key, normalizeIntForStorage(key, value.toInt()))
                    } else {
                        preferencesJson.put(key, value)
                    }
                }
                is Float -> preferencesJson.put(key, value)
                is Set<*> -> {
                    val stringSet = value.filterIsInstance<String>()
                    preferencesJson.put(key, org.json.JSONArray(stringSet))
                }
            }
        }

        return preferencesJson
    }

    private fun importPreferenceObject(
        preferencesJson: JSONObject?,
        preferences: SharedPreferences,
        preferenceTypes: Map<String, PreferenceType>,
        clearBeforeImport: Boolean = false
    ): Pair<Int, Int> {
        if (preferencesJson == null) return Pair(0, 0)

        val editor = preferences.edit()
        if (clearBeforeImport) {
            editor.clear()
        }
        var importedCount = 0
        var skippedCount = 0

        val keys = preferencesJson.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val preferenceType = preferenceTypes[key]
            if (preferenceType != null) {
                try {
                    val value = preferencesJson.get(key)
                    val imported = importValue(editor, key, preferenceType, value)

                    if (imported) {
                        importedCount++
                    } else {
                        android.util.Log.w("SettingsImport", "Unsupported value type for setting: $key")
                        skippedCount++
                    }
                } catch (e: Exception) {
                    android.util.Log.w("SettingsImport", "Failed to import setting: $key", e)
                    skippedCount++
                }
            } else {
                android.util.Log.d("SettingsImport", "Skipping unknown preference: $key")
                skippedCount++
            }
        }

        editor.apply()
        return Pair(importedCount, skippedCount)
    }

    private fun importValue(
        editor: SharedPreferences.Editor,
        key: String,
        type: PreferenceType,
        value: Any
    ): Boolean {
        return when (type) {
            PreferenceType.STRING -> {
                val normalizedValue = when (value) {
                    is String -> value
                    is Number -> value.toString()
                    is Boolean -> value.toString()
                    else -> null
                } ?: return false
                editor.putString(key, normalizedValue)
                true
            }

            PreferenceType.BOOLEAN -> {
                val normalizedValue = when (value) {
                    is Boolean -> value
                    is Number -> value.toInt() != 0
                    is String -> value.toBooleanStrictOrNull()
                    else -> null
                } ?: return false
                editor.putBoolean(key, normalizedValue)
                true
            }

            PreferenceType.INT -> {
                val normalizedValue = when (value) {
                    is Number -> value.toInt()
                    is String -> value.toIntOrNull()
                    else -> null
                } ?: return false
                editor.putInt(key, normalizeIntForStorage(key, normalizedValue))
                true
            }

            PreferenceType.FLOAT -> {
                val normalizedValue = when (value) {
                    is Number -> value.toFloat()
                    is String -> value.toFloatOrNull()
                    else -> null
                } ?: return false
                editor.putFloat(key, normalizedValue)
                true
            }

            PreferenceType.STRING_SET -> {
                val array = value as? org.json.JSONArray ?: return false
                val stringSet = mutableSetOf<String>()
                for (i in 0 until array.length()) {
                    stringSet.add(array.getString(i))
                }
                editor.putStringSet(key, stringSet)
                true
            }
        }
    }

    private fun normalizeIntForStorage(key: String, value: Int): Int {
        return if (CUSTOM_THEME_PREFERENCE_TYPES[key] == PreferenceType.INT) {
            CustomThemeStore.normalizeColorForStorage(key, value)
        } else {
            value
        }
    }
}
