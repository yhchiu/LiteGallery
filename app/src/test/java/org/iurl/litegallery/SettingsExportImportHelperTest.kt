package org.iurl.litegallery

import android.content.Context
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.iurl.litegallery.theme.CustomThemeStore
import org.iurl.litegallery.theme.ThemePack
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
    fun exportSettings_includesRememberFolderSortOrderValues() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        prefs.edit()
            .putBoolean("remember_folder_sort_order", true)
            .putString("last_folder_sort_order", "size_desc")
            .commit()

        val outputStream = ByteArrayOutputStream()
        val exported = helper.exportSettings(outputStream)

        assertTrue(exported)
        val root = JSONObject(outputStream.toString(Charsets.UTF_8.name()))
        val preferences = root.getJSONObject("preferences")
        assertTrue(preferences.getBoolean("remember_folder_sort_order"))
        assertEquals("size_desc", preferences.getString("last_folder_sort_order"))
    }

    @Test
    fun exportSettings_includesHomeFolderSortOrderValue() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        prefs.edit()
            .putString("home_folder_sort_order", "name_desc")
            .commit()

        val outputStream = ByteArrayOutputStream()
        val exported = helper.exportSettings(outputStream)

        assertTrue(exported)
        val root = JSONObject(outputStream.toString(Charsets.UTF_8.name()))
        val preferences = root.getJSONObject("preferences")
        assertEquals("name_desc", preferences.getString("home_folder_sort_order"))
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
    fun exportSettings_includesThemePackPreferences() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        prefs.edit()
            .putString("theme_pack_preference", ThemePack.BRUTALIST.key)
            .putString("theme_preference", "dark")
            .commit()

        val outputStream = ByteArrayOutputStream()
        val exported = helper.exportSettings(outputStream)

        assertTrue(exported)
        val root = JSONObject(outputStream.toString(Charsets.UTF_8.name()))
        val preferences = root.getJSONObject("preferences")
        assertEquals(ThemePack.BRUTALIST.key, preferences.getString("theme_pack_preference"))
        assertEquals("dark", preferences.getString("theme_preference"))
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
    fun exportSettings_includesTypedCustomThemePreferences() {
        val customBg = 0xff102030.toInt()
        val customAccent = 0xff405060.toInt()
        CustomThemeStore.setColor(context, CustomThemeStore.KEY_BG, customBg)
        CustomThemeStore.setColor(context, CustomThemeStore.KEY_ACCENT, customAccent)
        CustomThemeStore.setFont(context, CustomThemeStore.FONT_CORMORANT)
        CustomThemeStore.setCorner(context, CustomThemeStore.CORNER_LARGE)
        CustomThemeStore.setMode(context, CustomThemeStore.MODE_DARK)
        CustomThemeStore.setGradient(context, 0x40112233, 0x40445566, 135)
        context.getSharedPreferences(CustomThemeStore.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(CustomThemeStore.KEY_INITIALIZED, true)
            .putString("custom_unknown", "skip_me")
            .commit()

        val outputStream = ByteArrayOutputStream()
        val exported = helper.exportSettings(outputStream)

        assertTrue(exported)
        val root = JSONObject(outputStream.toString(Charsets.UTF_8.name()))
        val preferences = root.getJSONObject("custom_theme_preferences")
        assertEquals(customBg, preferences.getInt(CustomThemeStore.KEY_BG))
        assertEquals(customAccent, preferences.getInt(CustomThemeStore.KEY_ACCENT))
        assertEquals(CustomThemeStore.FONT_CORMORANT, preferences.getString(CustomThemeStore.KEY_FONT))
        assertEquals(CustomThemeStore.CORNER_LARGE, preferences.getString(CustomThemeStore.KEY_CORNER))
        assertEquals(CustomThemeStore.MODE_DARK, preferences.getString(CustomThemeStore.KEY_MODE))
        assertEquals(0xff112233.toInt(), preferences.getInt(CustomThemeStore.KEY_GRADIENT_START))
        assertEquals(0xff445566.toInt(), preferences.getInt(CustomThemeStore.KEY_GRADIENT_END))
        assertEquals(135, preferences.getInt(CustomThemeStore.KEY_GRADIENT_ANGLE))
        assertTrue(preferences.getBoolean(CustomThemeStore.KEY_INITIALIZED))
        assertFalse(preferences.has("custom_unknown"))
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
    fun importSettings_restoresRememberFolderSortOrderValues() {
        val settingsJson = JSONObject().apply {
            put("app_name", "LiteGallery")
            put("export_version", 2)
            put("export_timestamp", System.currentTimeMillis())
            put("preferences", JSONObject().apply {
                put("remember_folder_sort_order", true)
                put("last_folder_sort_order", "size_asc")
            })
        }

        val inputStream = ByteArrayInputStream(settingsJson.toString().toByteArray(Charsets.UTF_8))
        val (importedCount, skippedCount) = helper.importSettings(inputStream)

        assertEquals(2, importedCount)
        assertEquals(0, skippedCount)

        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        assertTrue(prefs.getBoolean("remember_folder_sort_order", false))
        assertEquals("size_asc", prefs.getString("last_folder_sort_order", null))
    }

    @Test
    fun importSettings_restoresHomeFolderSortOrderValue() {
        val settingsJson = JSONObject().apply {
            put("app_name", "LiteGallery")
            put("export_version", 2)
            put("export_timestamp", System.currentTimeMillis())
            put("preferences", JSONObject().apply {
                put("home_folder_sort_order", "size_desc")
            })
        }

        val inputStream = ByteArrayInputStream(settingsJson.toString().toByteArray(Charsets.UTF_8))
        val (importedCount, skippedCount) = helper.importSettings(inputStream)

        assertEquals(1, importedCount)
        assertEquals(0, skippedCount)

        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        assertEquals("size_desc", prefs.getString("home_folder_sort_order", null))
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
    fun importSettings_restoresThemePackPreferences() {
        val settingsJson = JSONObject().apply {
            put("app_name", "LiteGallery")
            put("export_version", 2)
            put("export_timestamp", System.currentTimeMillis())
            put("preferences", JSONObject().apply {
                put("theme_pack_preference", ThemePack.CUSTOM.key)
                put("theme_preference", "light")
            })
        }

        val inputStream = ByteArrayInputStream(settingsJson.toString().toByteArray(Charsets.UTF_8))
        val (importedCount, skippedCount) = helper.importSettings(inputStream)

        assertEquals(2, importedCount)
        assertEquals(0, skippedCount)

        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        assertEquals(ThemePack.CUSTOM.key, prefs.getString("theme_pack_preference", null))
        assertEquals("light", prefs.getString("theme_preference", null))
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
    fun importSettings_restoresTypedCustomThemePreferencesAndBumpsGeneration() {
        val customBg = 0xff223344.toInt()
        val customAccent = 0xff556677.toInt()
        val customOnAccent = 0xfff0f1f2.toInt()
        val generationBefore = CustomThemeStore.getGeneration()
        val settingsJson = JSONObject().apply {
            put("app_name", "LiteGallery")
            put("export_version", 2)
            put("export_timestamp", System.currentTimeMillis())
            put("preferences", JSONObject().apply {
                put("theme_pack_preference", ThemePack.CUSTOM.key)
            })
            put("custom_theme_preferences", JSONObject().apply {
                put(CustomThemeStore.KEY_BG, customBg)
                put(CustomThemeStore.KEY_ACCENT, customAccent)
                put(CustomThemeStore.KEY_ON_ACCENT, customOnAccent)
                put(CustomThemeStore.KEY_GRADIENT_START, 0x40112233)
                put(CustomThemeStore.KEY_GRADIENT_END, 0x40445566)
                put(CustomThemeStore.KEY_GRADIENT_ANGLE, 135)
                put(CustomThemeStore.KEY_FONT, CustomThemeStore.FONT_ARCHIVO_BLACK)
                put(CustomThemeStore.KEY_CORNER, CustomThemeStore.CORNER_NONE)
                put(CustomThemeStore.KEY_MODE, CustomThemeStore.MODE_DARK)
                put(CustomThemeStore.KEY_INITIALIZED, true)
                put("custom_unknown", "skip_me")
            })
        }

        val inputStream = ByteArrayInputStream(settingsJson.toString().toByteArray(Charsets.UTF_8))
        val (importedCount, skippedCount) = helper.importSettings(inputStream)

        assertEquals(11, importedCount)
        assertEquals(1, skippedCount)

        val defaultPrefs = PreferenceManager.getDefaultSharedPreferences(context)
        assertEquals(ThemePack.CUSTOM.key, defaultPrefs.getString("theme_pack_preference", null))
        assertEquals(customBg, CustomThemeStore.getColor(context, CustomThemeStore.KEY_BG))
        assertEquals(customAccent, CustomThemeStore.getColor(context, CustomThemeStore.KEY_ACCENT))
        assertEquals(customOnAccent, CustomThemeStore.getColor(context, CustomThemeStore.KEY_ON_ACCENT))
        assertEquals(CustomThemeStore.FONT_ARCHIVO_BLACK, CustomThemeStore.getFont(context))
        assertEquals(CustomThemeStore.CORNER_NONE, CustomThemeStore.getCorner(context))
        assertEquals(CustomThemeStore.MODE_DARK, CustomThemeStore.getMode(context))
        assertEquals(0xff112233.toInt(), CustomThemeStore.getGradientStart(context))
        assertEquals(0xff445566.toInt(), CustomThemeStore.getGradientEnd(context))
        assertEquals(135, CustomThemeStore.getGradientAngle(context))
        assertTrue(CustomThemeStore.isInitialized(context))
        assertTrue(CustomThemeStore.getGeneration() > generationBefore)
        assertTrue(
            context.getSharedPreferences(CustomThemeStore.PREFS_NAME, Context.MODE_PRIVATE)
                .all[CustomThemeStore.KEY_BG] is Int
        )
    }

    @Test
    fun importSettings_normalizesEditableCustomColorsToOpaqueRgb() {
        val settingsJson = JSONObject().apply {
            put("app_name", "LiteGallery")
            put("export_version", 2)
            put("export_timestamp", System.currentTimeMillis())
            put("preferences", JSONObject())
            put("custom_theme_preferences", JSONObject().apply {
                put(CustomThemeStore.KEY_BG, 0x40112233)
                put(CustomThemeStore.KEY_LINE, 0x40112233)
            })
        }

        val inputStream = ByteArrayInputStream(settingsJson.toString().toByteArray(Charsets.UTF_8))
        val (importedCount, skippedCount) = helper.importSettings(inputStream)

        assertEquals(2, importedCount)
        assertEquals(0, skippedCount)
        assertEquals(0xff112233.toInt(), CustomThemeStore.getColor(context, CustomThemeStore.KEY_BG))
        assertEquals(0x40112233, CustomThemeStore.getColor(context, CustomThemeStore.KEY_LINE))
    }

    @Test
    fun importSettingsSanitizesIncompleteCustomGradientPreferences() {
        val settingsJson = JSONObject().apply {
            put("app_name", "LiteGallery")
            put("export_version", 2)
            put("export_timestamp", System.currentTimeMillis())
            put("preferences", JSONObject())
            put("custom_theme_preferences", JSONObject().apply {
                put(CustomThemeStore.KEY_GRADIENT_START, 0x40112233)
                put(CustomThemeStore.KEY_GRADIENT_ANGLE, 13)
            })
        }

        val inputStream = ByteArrayInputStream(settingsJson.toString().toByteArray(Charsets.UTF_8))
        val (importedCount, skippedCount) = helper.importSettings(inputStream)

        assertEquals(2, importedCount)
        assertEquals(0, skippedCount)
        assertFalse(CustomThemeStore.hasGradient(context))

        val prefs = context.getSharedPreferences(CustomThemeStore.PREFS_NAME, Context.MODE_PRIVATE)
        assertFalse(prefs.contains(CustomThemeStore.KEY_GRADIENT_START))
        assertFalse(prefs.contains(CustomThemeStore.KEY_GRADIENT_ANGLE))
    }

    @Test
    fun importSettings_emptyCustomThemePreferencesClearsCustomStoreAndBumpsGeneration() {
        CustomThemeStore.setColor(context, CustomThemeStore.KEY_BG, 0xff010203.toInt())
        val generationBefore = CustomThemeStore.getGeneration()
        val settingsJson = JSONObject().apply {
            put("app_name", "LiteGallery")
            put("export_version", 2)
            put("export_timestamp", System.currentTimeMillis())
            put("preferences", JSONObject())
            put("custom_theme_preferences", JSONObject())
        }

        val inputStream = ByteArrayInputStream(settingsJson.toString().toByteArray(Charsets.UTF_8))
        val (importedCount, skippedCount) = helper.importSettings(inputStream)

        assertEquals(0, importedCount)
        assertEquals(0, skippedCount)
        assertFalse(
            context.getSharedPreferences(CustomThemeStore.PREFS_NAME, Context.MODE_PRIVATE)
                .contains(CustomThemeStore.KEY_BG)
        )
        assertTrue(CustomThemeStore.getGeneration() > generationBefore)
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
        context.getSharedPreferences(CustomThemeStore.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }
}
