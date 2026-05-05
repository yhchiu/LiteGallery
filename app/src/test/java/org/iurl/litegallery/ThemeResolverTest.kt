package org.iurl.litegallery

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import org.iurl.litegallery.theme.CustomThemeStore
import org.iurl.litegallery.theme.Mode
import org.iurl.litegallery.theme.PackResolver
import org.iurl.litegallery.theme.ThemePack
import org.iurl.litegallery.theme.ThemeVariant
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
class ThemeResolverTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        clearState()
    }

    @After
    fun tearDown() {
        clearState()
    }

    @Test
    fun modePrefValueAndLookupRoundTripKnownValues() {
        assertEquals("auto", Mode.AUTO.prefValue)
        assertEquals("light", Mode.LIGHT.prefValue)
        assertEquals("dark", Mode.DARK.prefValue)
        assertEquals(Mode.LIGHT, Mode.fromPrefValue("light"))
        assertEquals(Mode.DARK, Mode.fromPrefValue("dark"))
        assertEquals(Mode.AUTO, Mode.fromPrefValue(null))
        assertEquals(Mode.AUTO, Mode.fromPrefValue("unknown"))
    }

    @Test
    fun themePackLookupAndBuiltInListUseStableDefaults() {
        assertEquals("warm_paper", ThemePack.DEFAULT_KEY)
        assertEquals(ThemePack.WARM_PAPER, ThemePack.fromKey(null))
        assertEquals(ThemePack.WARM_PAPER, ThemePack.fromKey("missing"))
        assertEquals(ThemePack.BRUTALIST, ThemePack.fromKey("brutalist"))
        assertTrue(ThemePack.all().contains(ThemePack.CUSTOM))
        assertFalse(ThemePack.builtIn().contains(ThemePack.CUSTOM))
    }

    @Test
    fun themePackModeFlagsReflectSupportedModes() {
        assertTrue(ThemePack.WARM_PAPER.isDualMode)
        assertFalse(ThemePack.WARM_PAPER.isSingleMode)
        assertTrue(ThemePack.EDITORIAL.isSingleMode)
        assertTrue(ThemePack.EDITORIAL.isDarkOnly)
        assertFalse(ThemePack.EDITORIAL.isLightOnly)
        assertTrue(ThemePack.CUSTOM.isCustom)
    }

    @Test
    fun customPackEffectiveModesFollowCustomThemeStoreMode() {
        assertEquals(listOf(Mode.LIGHT), ThemePack.CUSTOM.getEffectiveSupportedModes(context))

        CustomThemeStore.setMode(context, CustomThemeStore.MODE_DARK)

        assertEquals(listOf(Mode.DARK), ThemePack.CUSTOM.getEffectiveSupportedModes(context))
    }

    @Test
    fun resolveNightMode_forcesSingleModePacksAndHonorsMultiModePreference() {
        assertEquals(
            AppCompatDelegate.MODE_NIGHT_YES,
            PackResolver.resolveNightMode(ThemePack.EDITORIAL, "light")
        )
        assertEquals(
            AppCompatDelegate.MODE_NIGHT_NO,
            PackResolver.resolveNightMode(ThemePack.WARM_PAPER, "light")
        )
        assertEquals(
            AppCompatDelegate.MODE_NIGHT_YES,
            PackResolver.resolveNightMode(ThemePack.WARM_PAPER, "dark")
        )
        assertEquals(
            AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM,
            PackResolver.resolveNightMode(ThemePack.WARM_PAPER, "auto")
        )
    }

    @Test
    fun resolveNightMode_overloadUsesStoredCustomMode() {
        assertEquals(
            AppCompatDelegate.MODE_NIGHT_NO,
            PackResolver.resolveNightMode(ThemePack.CUSTOM, "dark", context)
        )

        CustomThemeStore.setMode(context, CustomThemeStore.MODE_DARK)

        assertEquals(
            AppCompatDelegate.MODE_NIGHT_YES,
            PackResolver.resolveNightMode(ThemePack.CUSTOM, "light", context)
        )
    }

    @Test
    fun resolveEffectiveMode_resolvesFollowSystemToRenderedMode() {
        assertEquals(Mode.DARK, PackResolver.resolveEffectiveMode(ThemePack.WARM_PAPER, "auto", true))
        assertEquals(Mode.LIGHT, PackResolver.resolveEffectiveMode(ThemePack.WARM_PAPER, "auto", false))
        assertEquals(Mode.DARK, PackResolver.resolveEffectiveMode(ThemePack.WARM_PAPER, "dark", false))
        assertEquals(Mode.LIGHT, PackResolver.resolveEffectiveMode(ThemePack.WARM_PAPER, "light", true))
    }

    @Test
    fun pickStyleMapsEveryPackAndVariantToExpectedStyle() {
        assertEquals(
            R.style.Theme_LiteGallery_Pack_WarmPaper_NoActionBar,
            PackResolver.pickStyle(ThemePack.WARM_PAPER, ThemeVariant.NoActionBar)
        )
        assertEquals(
            R.style.Theme_LiteGallery_Pack_WarmPaper_FullScreen,
            PackResolver.pickStyle(ThemePack.WARM_PAPER, ThemeVariant.FullScreen)
        )
        assertEquals(
            R.style.Theme_LiteGallery_Pack_Custom_NoActionBar,
            PackResolver.pickStyle(ThemePack.CUSTOM, ThemeVariant.NoActionBar)
        )
        assertEquals(
            R.style.Theme_LiteGallery_Pack_Custom_FullScreen,
            PackResolver.pickStyle(ThemePack.CUSTOM, ThemeVariant.FullScreen)
        )
    }

    private fun clearState() {
        PreferenceManager.getDefaultSharedPreferences(context)
            .edit()
            .clear()
            .commit()
        CustomThemeStore.resetToDefaults(context)
    }
}
