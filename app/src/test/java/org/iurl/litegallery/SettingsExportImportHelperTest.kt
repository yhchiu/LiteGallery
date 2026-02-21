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
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

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

    private fun clearPrefs() {
        PreferenceManager.getDefaultSharedPreferences(context)
            .edit()
            .clear()
            .commit()
    }
}
