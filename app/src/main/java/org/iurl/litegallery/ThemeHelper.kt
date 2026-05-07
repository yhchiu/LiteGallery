package org.iurl.litegallery

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.PreferenceManager
import org.iurl.litegallery.theme.Mode
import org.iurl.litegallery.theme.CustomThemeApplier
import org.iurl.litegallery.theme.CustomThemeStore
import org.iurl.litegallery.theme.PackResolver
import org.iurl.litegallery.theme.ThemePack
import org.iurl.litegallery.theme.ThemeVariant

object ThemeHelper {

    private const val THEME_PREFERENCE_KEY = "theme_preference"
    const val THEME_PACK_PREFERENCE_KEY = "theme_pack_preference"

    const val THEME_AUTO = "auto"
    const val THEME_LIGHT = "light"
    const val THEME_DARK = "dark"

    /**
     * Intent extra key used to record the [CustomThemeStore] generation at
     * the time an activity was created. Pair [captureCustomThemeGeneration]
     * (in onCreate) with [checkAndRecreateForCustomThemeChange] (in onResume)
     * to recreate activities after the user edits the Custom theme.
     */
    private const val EXTRA_LAST_CUSTOM_GEN = "litegallery.last_custom_theme_gen"

    /**
     * One-time migration: existing users who had only `color_theme_preference`
     * land on the current default pack. Run from Application.onCreate before
     * applyTheme.
     */
    fun migrateLegacyIfNeeded(context: Context) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        if (!prefs.contains(THEME_PACK_PREFERENCE_KEY)) {
            prefs.edit()
                .putString(THEME_PACK_PREFERENCE_KEY, ThemePack.DEFAULT_KEY)
                .apply()
        }
    }

    fun getCurrentPack(context: Context): ThemePack {
        val key = PreferenceManager.getDefaultSharedPreferences(context)
            .getString(THEME_PACK_PREFERENCE_KEY, ThemePack.DEFAULT_KEY)
        return ThemePack.fromKey(key)
    }

    fun setPackPreference(context: Context, pack: ThemePack) {
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .putString(THEME_PACK_PREFERENCE_KEY, pack.key)
            .apply()
    }

    /**
     * Persist the new pack, push the night mode (in case it changed), and
     * always recreate the calling activity so it reflects the new pack.
     *
     * Why always-recreate: AppCompatDelegate.setDefaultNightMode only triggers
     * a recreate when the *rendered* Configuration changes. With pack-driven
     * night mode, the AppCompatDelegate value can change (FOLLOW_SYSTEM → YES)
     * without changing the rendered uiMode (e.g., system already in dark) — in
     * that case the framework skips the recreate, leaving the activity with the
     * old pack's fonts/colors. An explicit recreate guarantees the user sees
     * the new pack on the very tap they made.
     *
     * Other started activities pick up the change via their `onResume` —
     * they compare `currentPackKey` to the persisted pref and recreate if
     * the pack changed.
     */
    fun setPack(activity: Activity, newPack: ThemePack) {
        val themePref = getCurrentTheme(activity)
        val oldPack = getCurrentPack(activity)
        val oldNightMode = PackResolver.resolveNightMode(oldPack, themePref, activity)
        val newNightMode = PackResolver.resolveNightMode(newPack, themePref, activity)

        setPackPreference(activity, newPack)

        if (oldNightMode != newNightMode) {
            AppCompatDelegate.setDefaultNightMode(newNightMode)
        }
        activity.recreate()
    }

    /**
     * Set the user's mode preference for the current pack. Only meaningful
     * for dual-mode packs (V3/V5) — single-mode packs ignore this.
     */
    fun setModePreference(activity: Activity, mode: Mode) {
        val pack = getCurrentPack(activity)
        if (pack.isCustom) {
            if (mode == Mode.AUTO) return
            val oldNightMode = PackResolver.resolveNightMode(pack, getCurrentTheme(activity), activity)
            val customMode = when (mode) {
                Mode.DARK -> CustomThemeStore.MODE_DARK
                else -> CustomThemeStore.MODE_LIGHT
            }
            if (CustomThemeStore.getMode(activity) == customMode) return
            CustomThemeStore.setMode(activity, customMode)
            val newNightMode = PackResolver.resolveNightMode(pack, getCurrentTheme(activity), activity)
            if (oldNightMode != newNightMode) {
                AppCompatDelegate.setDefaultNightMode(newNightMode)
            }
            activity.recreate()
            return
        }
        if (pack.isSingleMode && !pack.isCustom) return
        val themePref = mode.prefValue
        PreferenceManager.getDefaultSharedPreferences(activity).edit()
            .putString(THEME_PREFERENCE_KEY, themePref)
            .apply()
        val newNightMode = PackResolver.resolveNightMode(pack, themePref, activity)
        AppCompatDelegate.setDefaultNightMode(newNightMode)
    }

    /**
     * Apply night mode based on current pack + theme preference. Call from
     * Application.onCreate and from each Activity.onCreate before super.
     */
    fun applyTheme(context: Context) {
        val pack = getCurrentPack(context)
        val themePref = getCurrentTheme(context)
        val nightMode = PackResolver.resolveNightMode(pack, themePref, context)
        AppCompatDelegate.setDefaultNightMode(nightMode)
    }

    /**
     * Apply the current pack's theme to the given activity. Must be called
     * from Activity.onCreate BEFORE super.onCreate() and BEFORE setContentView.
     *
     * `variant` selects between NoActionBar (most screens) and FullScreen
     * (the media viewer).
     */
    fun applyPackTheme(activity: Activity, variant: ThemeVariant) {
        val pack = getCurrentPack(activity)
        activity.setTheme(PackResolver.pickStyle(pack, variant))

        // Custom pack: XML supplies a stable Material base; arbitrary user
        // colours are applied after inflation by the runtime palette applier.
        if (pack.isCustom) {
            activity.theme.applyStyle(
                CustomThemeStore.getFontOverlayRes(activity), true
            )
            activity.theme.applyStyle(
                CustomThemeStore.getCornerOverlayRes(activity), true
            )
        }
    }

    fun applyRuntimeCustomColors(activity: Activity) {
        CustomThemeApplier.apply(activity)
    }

    fun applyRuntimeCustomColors(dialog: AlertDialog) {
        CustomThemeApplier.apply(dialog)
    }

    fun applyRuntimeCustomColors(dialog: androidx.appcompat.app.AlertDialog) {
        CustomThemeApplier.apply(dialog)
    }

    /**
     * Record the current [CustomThemeStore] generation in the activity's
     * intent so [checkAndRecreateForCustomThemeChange] can later detect
     * whether the user edited the Custom theme while the activity was paused.
     *
     * Call this from each Custom-theme-aware activity's onCreate (after super).
     */
    fun captureCustomThemeGeneration(activity: Activity) {
        activity.intent.putExtra(EXTRA_LAST_CUSTOM_GEN, CustomThemeStore.getGeneration())
    }

    /**
     * If the current pack is Custom and its generation has advanced since
     * this activity was last created, recreate the activity so colour
     * overrides and font/corner overlays (applied in [applyPackTheme]) are
     * picked up. Returns true when a
     * recreate was queued — callers should `return` immediately.
     */
    fun checkAndRecreateForCustomThemeChange(activity: Activity): Boolean {
        if (!getCurrentPack(activity).isCustom) return false
        val lastGen = activity.intent.getIntExtra(EXTRA_LAST_CUSTOM_GEN, -1)
        if (lastGen < 0) return false
        if (CustomThemeStore.getGeneration() == lastGen) return false
        activity.recreate()
        return true
    }

    fun getCurrentTheme(context: Context): String {
        return PreferenceManager.getDefaultSharedPreferences(context)
            .getString(THEME_PREFERENCE_KEY, THEME_AUTO) ?: THEME_AUTO
    }

    /**
     * Whether the given context is currently rendering in dark mode (system uiMode
     * has Configuration.UI_MODE_NIGHT_YES set).
     */
    fun isSystemDark(context: Context): Boolean {
        val uiMode = context.resources.configuration.uiMode
        return (uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    }

    fun getThemeDisplayName(context: Context, theme: String): String {
        return when (theme) {
            THEME_LIGHT -> context.getString(R.string.theme_light)
            THEME_DARK -> context.getString(R.string.theme_dark)
            else -> context.getString(R.string.theme_auto)
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Legacy stubs — kept so older code paths don't break during migration.
    // The pack system supersedes the 8-color picker; existing callers should
    // be updated to applyPackTheme over time.
    // ─────────────────────────────────────────────────────────────────────

    @Deprecated("Use applyPackTheme(activity, variant)", ReplaceWith("applyPackTheme(activity, ThemeVariant.NoActionBar)"))
    fun applyColorTheme(activity: Activity) {
        applyPackTheme(activity, ThemeVariant.NoActionBar)
    }

    /**
     * Returns the current pack's key as a stable string. Older callers that
     * tracked "currentColorTheme" can use this for change detection.
     */
    @Deprecated("Use getCurrentPack(context).key", ReplaceWith("getCurrentPack(context).key"))
    fun getCurrentColorTheme(context: Context): String = getCurrentPack(context).key
}
