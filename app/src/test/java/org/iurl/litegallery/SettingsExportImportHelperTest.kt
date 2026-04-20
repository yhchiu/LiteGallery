package org.iurl.litegallery

import android.content.Context
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class SettingsExportImportHelperTest {

    private lateinit var context: Context
    private lateinit var helper: SettingsExportImportHelper

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        helper = SettingsExportImportHelper(context)
        clearPrefs()
    }

    @After
    fun tearDown() {
        clearPrefs()
    }

    @Test
    fun exportSettings_includesRememberFolderViewModeValues() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        prefs.edit()
            .putBoolean("remember_folder_view_mode", true)
            .putString("last_folder_view_mode", "detailed")
            .commit()

        val outputStream = ByteArrayOutputStream()
        val exported = helper.exportSettings(outputStream)

        assertTrue(exported)
        val root = JSONObject(outputStream.toString(Charsets.UTF_8.name()))
        val preferences = root.getJSONObject("preferences")
        assertTrue(preferences.getBoolean("remember_folder_view_mode"))
        assertEquals("detailed", preferences.getString("last_folder_view_mode"))
    }

    @Test
    fun exportSettings_includesLanguagePreference() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        prefs.edit()
            .putString("app_language", "zh_TW")
            .commit()

        val outputStream = ByteArrayOutputStream()
        val exported = helper.exportSettings(outputStream)

        assertTrue(exported)
        val root = JSONObject(outputStream.toString(Charsets.UTF_8.name()))
        val preferences = root.getJSONObject("preferences")
        assertEquals("zh_TW", preferences.getString("app_language"))
    }

    @Test
    fun exportSettings_includesCustomizedActionBarPreferences() {
        val prefs = context.getSharedPreferences(ActionBarPreferences.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(ActionBarPreferences.KEY_ORDER, "rename,delete,properties")
            .putString(ActionBarPreferences.KEY_VISIBLE, "delete,properties")
            .commit()

        val outputStream = ByteArrayOutputStream()
        val exported = helper.exportSettings(outputStream)

        assertTrue(exported)
        val root = JSONObject(outputStream.toString(Charsets.UTF_8.name()))
        val preferences = root.getJSONObject("action_bar_preferences")
        assertEquals("rename,delete,properties", preferences.getString(ActionBarPreferences.KEY_ORDER))
        assertEquals("delete,properties", preferences.getString(ActionBarPreferences.KEY_VISIBLE))
    }

    @Test
    fun importSettings_restoresRememberFolderViewModeValues() {
        val settingsJson = JSONObject().apply {
            put("app_name", "LiteGallery")
            put("export_version", 1)
            put("export_timestamp", System.currentTimeMillis())
            put("preferences", JSONObject().apply {
                put("remember_folder_view_mode", true)
                put("last_folder_view_mode", "list")
            })
        }

        val inputStream = ByteArrayInputStream(settingsJson.toString().toByteArray(Charsets.UTF_8))
        val (importedCount, skippedCount) = helper.importSettings(inputStream)

        assertEquals(2, importedCount)
        assertEquals(0, skippedCount)

        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        assertTrue(prefs.getBoolean("remember_folder_view_mode", false))
        assertEquals("list", prefs.getString("last_folder_view_mode", null))
    }

    @Test
    fun importSettings_restoresLanguagePreference() {
        val settingsJson = JSONObject().apply {
            put("app_name", "LiteGallery")
            put("export_version", 1)
            put("export_timestamp", System.currentTimeMillis())
            put("preferences", JSONObject().apply {
                put("app_language", "zh_TW")
            })
        }

        val inputStream = ByteArrayInputStream(settingsJson.toString().toByteArray(Charsets.UTF_8))
        val (importedCount, skippedCount) = helper.importSettings(inputStream)

        assertEquals(1, importedCount)
        assertEquals(0, skippedCount)

        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        assertEquals("zh_TW", prefs.getString("app_language", null))
    }

    @Test
    fun importSettings_restoresCustomizedActionBarPreferences() {
        val settingsJson = JSONObject().apply {
            put("app_name", "LiteGallery")
            put("export_version", 1)
            put("export_timestamp", System.currentTimeMillis())
            put("preferences", JSONObject())
            put("action_bar_preferences", JSONObject().apply {
                put(ActionBarPreferences.KEY_ORDER, "rename,delete,properties")
                put(ActionBarPreferences.KEY_VISIBLE, "delete,properties")
            })
        }

        val inputStream = ByteArrayInputStream(settingsJson.toString().toByteArray(Charsets.UTF_8))
        val (importedCount, skippedCount) = helper.importSettings(inputStream)

        assertEquals(2, importedCount)
        assertEquals(0, skippedCount)

        val prefs = context.getSharedPreferences(ActionBarPreferences.PREFS_NAME, Context.MODE_PRIVATE)
        assertEquals("rename,delete,properties", prefs.getString(ActionBarPreferences.KEY_ORDER, null))
        assertEquals("delete,properties", prefs.getString(ActionBarPreferences.KEY_VISIBLE, null))
    }

    @Test
    fun importSettings_coercesNumericLiteralIntoStringPreference() {
        val settingsJson = JSONObject().apply {
            put("app_name", "LiteGallery")
            put("export_version", 1)
            put("export_timestamp", System.currentTimeMillis())
            put("preferences", JSONObject().apply {
                put("zoom_max_scale", 3)
            })
        }

        val inputStream = ByteArrayInputStream(settingsJson.toString().toByteArray(Charsets.UTF_8))
        val (importedCount, skippedCount) = helper.importSettings(inputStream)

        assertEquals(1, importedCount)
        assertEquals(0, skippedCount)

        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        assertEquals("3", prefs.getString("zoom_max_scale", null))
    }

    @Test
    fun importSettings_restoresSavedVideoBrightnessAsFloatWhenJsonUsesIntegerLiteral() {
        val settingsJson = JSONObject().apply {
            put("app_name", "LiteGallery")
            put("export_version", 1)
            put("export_timestamp", System.currentTimeMillis())
            put("preferences", JSONObject().apply {
                put("remember_video_brightness", true)
                put("saved_video_brightness", 1)
            })
        }

        val inputStream = ByteArrayInputStream(settingsJson.toString().toByteArray(Charsets.UTF_8))
        val (importedCount, skippedCount) = helper.importSettings(inputStream)

        assertEquals(2, importedCount)
        assertEquals(0, skippedCount)

        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        assertTrue(prefs.getBoolean("remember_video_brightness", false))
        assertEquals(1f, prefs.getFloat("saved_video_brightness", -1f))
        assertTrue(prefs.all["saved_video_brightness"] is Float)
    }

    private fun clearPrefs() {
        PreferenceManager.getDefaultSharedPreferences(context)
            .edit()
            .clear()
            .commit()
        context.getSharedPreferences(ActionBarPreferences.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }
}
