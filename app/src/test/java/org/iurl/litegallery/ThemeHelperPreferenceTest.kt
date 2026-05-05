package org.iurl.litegallery

import android.content.Context
import android.content.res.Configuration
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import org.iurl.litegallery.theme.ThemePack
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class ThemeHelperPreferenceTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        clearPrefs()
    }

    @After
    fun tearDown() {
        clearPrefs()
    }

    @Test
    fun migrateLegacyIfNeeded_setsDefaultPackWhenMissing() {
        ThemeHelper.migrateLegacyIfNeeded(context)

        assertEquals(ThemePack.WARM_PAPER, ThemeHelper.getCurrentPack(context))
    }

    @Test
    fun migrateLegacyIfNeeded_preservesExistingPackPreference() {
        PreferenceManager.getDefaultSharedPreferences(context)
            .edit()
            .putString(ThemeHelper.THEME_PACK_PREFERENCE_KEY, ThemePack.BRUTALIST.key)
            .commit()

        ThemeHelper.migrateLegacyIfNeeded(context)

        assertEquals(ThemePack.BRUTALIST, ThemeHelper.getCurrentPack(context))
    }

    @Test
    fun setPackPreferenceAndDeprecatedColorThemeExposeCurrentPackKey() {
        ThemeHelper.setPackPreference(context, ThemePack.PRISM)

        assertEquals(ThemePack.PRISM, ThemeHelper.getCurrentPack(context))
        @Suppress("DEPRECATION")
        assertEquals(ThemePack.PRISM.key, ThemeHelper.getCurrentColorTheme(context))
    }

    @Test
    fun getCurrentPack_fallsBackToDefaultForUnknownKey() {
        PreferenceManager.getDefaultSharedPreferences(context)
            .edit()
            .putString(ThemeHelper.THEME_PACK_PREFERENCE_KEY, "missing")
            .commit()

        assertEquals(ThemePack.WARM_PAPER, ThemeHelper.getCurrentPack(context))
    }

    @Test
    fun getCurrentTheme_defaultsToAutoAndReturnsSavedPreference() {
        assertEquals(ThemeHelper.THEME_AUTO, ThemeHelper.getCurrentTheme(context))

        PreferenceManager.getDefaultSharedPreferences(context)
            .edit()
            .putString("theme_preference", ThemeHelper.THEME_DARK)
            .commit()

        assertEquals(ThemeHelper.THEME_DARK, ThemeHelper.getCurrentTheme(context))
    }

    @Test
    fun getThemeDisplayName_mapsKnownAndUnknownThemeValues() {
        assertEquals(context.getString(R.string.theme_light), ThemeHelper.getThemeDisplayName(context, "light"))
        assertEquals(context.getString(R.string.theme_dark), ThemeHelper.getThemeDisplayName(context, "dark"))
        assertEquals(context.getString(R.string.theme_auto), ThemeHelper.getThemeDisplayName(context, "auto"))
        assertEquals(context.getString(R.string.theme_auto), ThemeHelper.getThemeDisplayName(context, "unknown"))
    }

    @Test
    fun isSystemDark_reflectsConfigurationNightMask() {
        val darkConfig = Configuration(context.resources.configuration).apply {
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or Configuration.UI_MODE_NIGHT_YES
        }
        val lightConfig = Configuration(context.resources.configuration).apply {
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or Configuration.UI_MODE_NIGHT_NO
        }

        assertTrue(ThemeHelper.isSystemDark(context.createConfigurationContext(darkConfig)))
        assertFalse(ThemeHelper.isSystemDark(context.createConfigurationContext(lightConfig)))
    }

    private fun clearPrefs() {
        PreferenceManager.getDefaultSharedPreferences(context)
            .edit()
            .clear()
            .commit()
    }
}
