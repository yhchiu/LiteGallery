package org.iurl.litegallery

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import org.json.JSONException
import org.json.JSONObject
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.*

class SettingsExportImportHelper(private val context: Context) {

    companion object {
        private const val SETTINGS_VERSION = 1
        private const val APP_NAME = "LiteGallery"

        // All known preference keys for validation
        private val KNOWN_PREFERENCES = setOf(
            // Display Settings
            "default_sort_order",
            "default_view_mode",
            "filename_max_lines",
            "zoom_max_scale",

            // Video Gesture Settings
            "video_single_tap_action",
            "video_double_tap_action",
            "video_left_swipe_up_action",
            "video_left_swipe_down_action",
            "video_right_swipe_up_action",
            "video_right_swipe_down_action",

            // Quick Rename Settings
            "rename_history_count",
            "rename_default_sort",

            // General Settings
            "theme_preference",
            "color_theme_preference",
            "restore_brightness",
            "restore_volume"
        )
    }

    private val sharedPreferences: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)

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

                val preferencesJson = JSONObject()
                val allPrefs = sharedPreferences.all

                for ((key, value) in allPrefs) {
                    // Only export known preferences to avoid internal/temporary values
                    if (KNOWN_PREFERENCES.contains(key)) {
                        when (value) {
                            is String -> preferencesJson.put(key, value)
                            is Boolean -> preferencesJson.put(key, value)
                            is Int -> preferencesJson.put(key, value)
                            is Long -> preferencesJson.put(key, value)
                            is Float -> preferencesJson.put(key, value)
                            is Set<*> -> {
                                // Convert Set to JSON array for string sets
                                val stringSet = value.filterIsInstance<String>()
                                preferencesJson.put(key, org.json.JSONArray(stringSet))
                            }
                        }
                    }
                }

                put("preferences", preferencesJson)
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

            val preferencesJson = settingsJson.getJSONObject("preferences")
            val editor = sharedPreferences.edit()

            var importedCount = 0
            var skippedCount = 0

            val keys = preferencesJson.keys()
            while (keys.hasNext()) {
                val key = keys.next()

                // Only import known preferences for compatibility
                if (KNOWN_PREFERENCES.contains(key)) {
                    try {
                        val value = preferencesJson.get(key)
                        when (value) {
                            is String -> editor.putString(key, value)
                            is Boolean -> editor.putBoolean(key, value)
                            is Int -> editor.putInt(key, value)
                            is Long -> editor.putLong(key, value)
                            is Double -> editor.putFloat(key, value.toFloat())
                            is org.json.JSONArray -> {
                                // Convert JSON array back to string set
                                val stringSet = mutableSetOf<String>()
                                for (i in 0 until value.length()) {
                                    stringSet.add(value.getString(i))
                                }
                                editor.putStringSet(key, stringSet)
                            }
                        }
                        importedCount++
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
            Pair(importedCount, skippedCount)

        } catch (e: JSONException) {
            android.util.Log.e("SettingsImport", "Invalid JSON format", e)
            throw IllegalArgumentException("Invalid settings file format")
        } catch (e: Exception) {
            android.util.Log.e("SettingsImport", "Failed to import settings", e)
            throw e
        }
    }
}