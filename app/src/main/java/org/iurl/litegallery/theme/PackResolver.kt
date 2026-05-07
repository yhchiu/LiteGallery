package org.iurl.litegallery.theme

import androidx.appcompat.app.AppCompatDelegate
import org.iurl.litegallery.R

/**
 * Pure functions for translating (pack, theme preference, system uiMode) into
 * the concrete inputs `ThemeHelper` and `Activity.setTheme()` need.
 *
 * No Activity / Configuration / Context reads here - that keeps these
 * functions safe to call before super.onCreate() and trivially testable.
 */
object PackResolver {

    /**
     * Decide which AppCompatDelegate night-mode to apply, given the current
     * pack and the user's mode preference.
     *
     * Single-mode packs override the user preference: V1/V2/V4 force DARK;
     * a Light-only pack would force LIGHT. Multi-mode packs honor the user's
     * preference, falling back to FOLLOW_SYSTEM for "auto".
     */
    fun resolveNightMode(pack: ThemePack, themePref: String): Int {
        // Custom pack: mode is controlled by CustomThemeStore, not the global pref.
        // This is handled by the caller which passes the correct pref for Custom.
        if (pack.isSingleMode) {
            return when (pack.supportedModes[0]) {
                Mode.DARK -> AppCompatDelegate.MODE_NIGHT_YES
                Mode.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
                Mode.AUTO -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
        }
        return when (themePref) {
            "light" -> AppCompatDelegate.MODE_NIGHT_NO
            "dark" -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
    }

    /**
     * Overload that resolves Custom pack mode from [CustomThemeStore].
     */
    fun resolveNightMode(pack: ThemePack, themePref: String, context: android.content.Context): Int {
        if (pack.isCustom) {
            return when (CustomThemeStore.getMode(context)) {
                CustomThemeStore.MODE_DARK -> AppCompatDelegate.MODE_NIGHT_YES
                else -> AppCompatDelegate.MODE_NIGHT_NO
            }
        }
        return resolveNightMode(pack, themePref)
    }

    /**
     * Resolve the *effective* mode shown to the user (LIGHT or DARK), given
     * the pack, theme preference, and the system's current isDark state.
     * Used by the Theme Picker UI to highlight the correct segment of the
     * Auto/Light/Dark control.
     */
    fun resolveEffectiveMode(pack: ThemePack, themePref: String, systemIsDark: Boolean): Mode {
        return when (resolveNightMode(pack, themePref)) {
            AppCompatDelegate.MODE_NIGHT_YES -> Mode.DARK
            AppCompatDelegate.MODE_NIGHT_NO -> Mode.LIGHT
            else -> if (systemIsDark) Mode.DARK else Mode.LIGHT
        }
    }

    /**
     * Map (pack, variant) to the resource id of the corresponding style.
     * `values-night/themes_pack.xml` overrides the same style name when
     * the system is in dark mode, so we do NOT branch on isDark here.
     */
    fun pickStyle(pack: ThemePack, variant: ThemeVariant): Int = when (pack) {
        ThemePack.FIRST_LIGHT -> when (variant) {
            ThemeVariant.NoActionBar -> R.style.Theme_LiteGallery_Pack_FirstLight_NoActionBar
            ThemeVariant.FullScreen -> R.style.Theme_LiteGallery_Pack_FirstLight_FullScreen
        }
        ThemePack.WARM_PAPER -> when (variant) {
            ThemeVariant.NoActionBar -> R.style.Theme_LiteGallery_Pack_WarmPaper_NoActionBar
            ThemeVariant.FullScreen -> R.style.Theme_LiteGallery_Pack_WarmPaper_FullScreen
        }
        ThemePack.EDITORIAL -> when (variant) {
            ThemeVariant.NoActionBar -> R.style.Theme_LiteGallery_Pack_Editorial_NoActionBar
            ThemeVariant.FullScreen -> R.style.Theme_LiteGallery_Pack_Editorial_FullScreen
        }
        ThemePack.MONOLITH -> when (variant) {
            ThemeVariant.NoActionBar -> R.style.Theme_LiteGallery_Pack_Monolith_NoActionBar
            ThemeVariant.FullScreen -> R.style.Theme_LiteGallery_Pack_Monolith_FullScreen
        }
        ThemePack.PRISM -> when (variant) {
            ThemeVariant.NoActionBar -> R.style.Theme_LiteGallery_Pack_Prism_NoActionBar
            ThemeVariant.FullScreen -> R.style.Theme_LiteGallery_Pack_Prism_FullScreen
        }
        ThemePack.BRUTALIST -> when (variant) {
            ThemeVariant.NoActionBar -> R.style.Theme_LiteGallery_Pack_Brutalist_NoActionBar
            ThemeVariant.FullScreen -> R.style.Theme_LiteGallery_Pack_Brutalist_FullScreen
        }
        ThemePack.CUSTOM -> when (variant) {
            ThemeVariant.NoActionBar -> R.style.Theme_LiteGallery_Pack_Custom_NoActionBar
            ThemeVariant.FullScreen -> R.style.Theme_LiteGallery_Pack_Custom_FullScreen
        }
    }
}
