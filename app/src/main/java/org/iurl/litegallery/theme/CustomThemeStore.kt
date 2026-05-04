package org.iurl.litegallery.theme

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import androidx.core.content.ContextCompat
import org.iurl.litegallery.R

/**
 * Persistence layer for user-customisable theme colours, font and corner-radius.
 *
 * Values are stored in a dedicated SharedPreferences file so they don't pollute
 * the default prefs namespace.  Each colour token is stored as an ARGB int;
 * font and corner-radius are stored as string keys that map to overlay style
 * resource IDs at apply-time.
 */
object CustomThemeStore {

    const val PREFS_NAME = "custom_theme_prefs"

    // ── colour keys ────────────────────────────────────────────────────
    const val KEY_BG        = "custom_bg"
    const val KEY_SURFACE   = "custom_surface"
    const val KEY_CARD      = "custom_card"
    const val KEY_TEXT       = "custom_text"
    const val KEY_DIM        = "custom_dim"
    const val KEY_FAINT      = "custom_faint"
    const val KEY_LINE       = "custom_line"
    const val KEY_ACCENT     = "custom_accent"
    const val KEY_ON_ACCENT  = "custom_on_accent"

    // ── non-colour keys ────────────────────────────────────────────────
    const val KEY_FONT          = "custom_font"
    const val KEY_CORNER        = "custom_corner"
    const val KEY_MODE          = "custom_mode"
    const val KEY_INITIALIZED   = "custom_initialized"

    // ── mode option keys ──────────────────────────────────────────────
    const val MODE_LIGHT = "light"
    const val MODE_DARK  = "dark"

    // ── font option keys ───────────────────────────────────────────────
    const val FONT_SANS_SERIF        = "sans_serif"
    const val FONT_FRAUNCES          = "fraunces"
    const val FONT_CORMORANT         = "cormorant_garamond"
    const val FONT_JETBRAINS_MONO    = "jetbrains_mono"
    const val FONT_ARCHIVO_BLACK     = "archivo_black"

    // ── corner option keys ─────────────────────────────────────────────
    const val CORNER_NONE   = "none"
    const val CORNER_SMALL  = "small"
    const val CORNER_MEDIUM = "medium"
    const val CORNER_LARGE  = "large"

    /** All editable colour token keys in display order. */
    val COLOR_KEYS = listOf(
        KEY_BG, KEY_SURFACE, KEY_CARD, KEY_TEXT,
        KEY_DIM, KEY_ACCENT, KEY_ON_ACCENT,
    )

    // ── generation counter ────────────────────────────────────────────
    //
    // Activities that render the Custom pack capture this value when they
    // are created and re-check it in onResume. A mismatch means the user
    // changed something in the editor while this activity was paused, so
    // the activity must recreate to re-run the runtime palette applier and
    // font/corner overlays.
    @Volatile
    private var generation: Int = 0

    fun getGeneration(): Int = generation

    private fun bumpGeneration() {
        generation++
    }

    fun notifyExternalChange() {
        bumpGeneration()
    }

    // ── helpers ────────────────────────────────────────────────────────

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isInitialized(context: Context): Boolean =
        prefs(context).getBoolean(KEY_INITIALIZED, false)

    // ── colour getters / setters ───────────────────────────────────────

    fun getColor(context: Context, key: String): Int {
        val value = prefs(context).all[key] ?: return defaultColorFor(context, key)
        return when (value) {
            is Int -> value
            is Number -> value.toInt()
            else -> defaultColorFor(context, key)
        }
    }

    fun setColor(context: Context, key: String, color: Int) {
        prefs(context).edit().putInt(key, normalizeColorForStorage(key, color)).apply()
        bumpGeneration()
    }

    fun normalizeColorForStorage(key: String, color: Int): Int {
        return if (COLOR_KEYS.contains(key)) toOpaqueRgb(color) else color
    }

    fun toOpaqueRgb(color: Int): Int = color or 0xFF000000.toInt()

    // ── font / corner / mode ───────────────────────────────────────────

    fun getFont(context: Context): String =
        prefs(context).getString(KEY_FONT, FONT_SANS_SERIF) ?: FONT_SANS_SERIF

    fun setFont(context: Context, fontKey: String) {
        prefs(context).edit().putString(KEY_FONT, fontKey).apply()
        bumpGeneration()
    }

    fun getCorner(context: Context): String =
        prefs(context).getString(KEY_CORNER, CORNER_MEDIUM) ?: CORNER_MEDIUM

    fun setCorner(context: Context, cornerKey: String) {
        prefs(context).edit().putString(KEY_CORNER, cornerKey).apply()
        bumpGeneration()
    }

    fun getMode(context: Context): String =
        prefs(context).getString(KEY_MODE, MODE_LIGHT) ?: MODE_LIGHT

    fun setMode(context: Context, modeKey: String) {
        prefs(context).edit().putString(KEY_MODE, modeKey).apply()
        bumpGeneration()
    }

    // ── font overlay style resource mapping ────────────────────────────

    fun getFontOverlayRes(context: Context): Int = when (getFont(context)) {
        FONT_FRAUNCES       -> R.style.ThemeOverlay_Custom_Font_Fraunces
        FONT_CORMORANT      -> R.style.ThemeOverlay_Custom_Font_Cormorant
        FONT_JETBRAINS_MONO -> R.style.ThemeOverlay_Custom_Font_JetBrainsMono
        FONT_ARCHIVO_BLACK  -> R.style.ThemeOverlay_Custom_Font_ArchivoBlack
        else                -> R.style.ThemeOverlay_Custom_Font_SansSerif
    }

    fun getCornerOverlayRes(context: Context): Int = when (getCorner(context)) {
        CORNER_NONE  -> R.style.ThemeOverlay_Custom_Corner_None
        CORNER_SMALL -> R.style.ThemeOverlay_Custom_Corner_Small
        CORNER_LARGE -> R.style.ThemeOverlay_Custom_Corner_Large
        else         -> R.style.ThemeOverlay_Custom_Corner_Medium
    }

    // ── initialisation from another pack ───────────────────────────────

    /**
     * Populate the custom store with the resolved colours / font / corner of
     * [sourcePack].  Called once when the user first switches to Custom.
     *
     * Colours are resolved via resource IDs so they reflect the correct
     * light / dark variant the user currently sees.
     */
    fun initializeFromPack(context: Context, sourcePack: ThemePack) {
        val colors = resolvePackColors(context, sourcePack)
        val editor = prefs(context).edit()
        for ((key, value) in colors) {
            editor.putInt(key, value)
        }
        editor.putString(KEY_FONT, packToFontKey(sourcePack))
        editor.putString(KEY_CORNER, packToCornerKey(sourcePack))
        editor.putString(KEY_MODE, packToModeKey(sourcePack, context))
        editor.putBoolean(KEY_INITIALIZED, true)
        editor.apply()
        bumpGeneration()
    }

    /**
     * Re-seed the custom store from a built-in pack + specific mode,
     * called from the editor's "Reset from built-in" feature.
     */
    fun resetFromPack(context: Context, sourcePack: ThemePack, mode: String) {
        val colors = resolvePackColors(context.forCustomThemeMode(mode), sourcePack)
        val editor = prefs(context).edit()
        editor.clear()
        for ((key, value) in colors) {
            editor.putInt(key, value)
        }
        editor.putString(KEY_FONT, packToFontKey(sourcePack))
        editor.putString(KEY_CORNER, packToCornerKey(sourcePack))
        editor.putString(KEY_MODE, mode)
        editor.putBoolean(KEY_INITIALIZED, true)
        editor.apply()
        bumpGeneration()
    }

    fun resetToDefaults(context: Context) {
        prefs(context).edit().clear().apply()
        bumpGeneration()
    }

    // ── internal helpers ───────────────────────────────────────────────

    private fun defaultColorFor(context: Context, key: String): Int = when (key) {
        KEY_BG        -> ContextCompat.getColor(context, R.color.pack_warm_paper_bg)
        KEY_SURFACE   -> ContextCompat.getColor(context, R.color.pack_warm_paper_surface)
        KEY_CARD      -> ContextCompat.getColor(context, R.color.pack_warm_paper_card)
        KEY_TEXT       -> ContextCompat.getColor(context, R.color.pack_warm_paper_text)
        KEY_DIM        -> ContextCompat.getColor(context, R.color.pack_warm_paper_dim)
        KEY_FAINT      -> ContextCompat.getColor(context, R.color.pack_warm_paper_faint)
        KEY_LINE       -> ContextCompat.getColor(context, R.color.pack_warm_paper_line)
        KEY_ACCENT     -> ContextCompat.getColor(context, R.color.pack_warm_paper_accent)
        KEY_ON_ACCENT  -> ContextCompat.getColor(context, R.color.pack_warm_paper_on_accent)
        else           -> Color.MAGENTA
    }

    @Suppress("CyclomaticComplexMethod")
    private fun resolvePackColors(context: Context, pack: ThemePack): Map<String, Int> {
        fun c(resId: Int) = ContextCompat.getColor(context, resId)
        return when (pack) {
            ThemePack.WARM_PAPER -> mapOf(
                KEY_BG to c(R.color.pack_warm_paper_bg), KEY_SURFACE to c(R.color.pack_warm_paper_surface),
                KEY_CARD to c(R.color.pack_warm_paper_card), KEY_TEXT to c(R.color.pack_warm_paper_text),
                KEY_DIM to c(R.color.pack_warm_paper_dim), KEY_FAINT to c(R.color.pack_warm_paper_faint),
                KEY_LINE to c(R.color.pack_warm_paper_line), KEY_ACCENT to c(R.color.pack_warm_paper_accent),
                KEY_ON_ACCENT to c(R.color.pack_warm_paper_on_accent),
            )
            ThemePack.EDITORIAL -> mapOf(
                KEY_BG to c(R.color.pack_editorial_bg), KEY_SURFACE to c(R.color.pack_editorial_surface),
                KEY_CARD to c(R.color.pack_editorial_card), KEY_TEXT to c(R.color.pack_editorial_text),
                KEY_DIM to c(R.color.pack_editorial_dim), KEY_FAINT to c(R.color.pack_editorial_faint),
                KEY_LINE to c(R.color.pack_editorial_line), KEY_ACCENT to c(R.color.pack_editorial_accent),
                KEY_ON_ACCENT to c(R.color.pack_editorial_on_accent),
            )
            ThemePack.MONOLITH -> mapOf(
                KEY_BG to c(R.color.pack_monolith_bg), KEY_SURFACE to c(R.color.pack_monolith_surface),
                KEY_CARD to c(R.color.pack_monolith_card), KEY_TEXT to c(R.color.pack_monolith_text),
                KEY_DIM to c(R.color.pack_monolith_dim), KEY_FAINT to c(R.color.pack_monolith_faint),
                KEY_LINE to c(R.color.pack_monolith_line), KEY_ACCENT to c(R.color.pack_monolith_accent),
                KEY_ON_ACCENT to c(R.color.pack_monolith_on_accent),
            )
            ThemePack.PRISM -> mapOf(
                KEY_BG to c(R.color.pack_prism_bg), KEY_SURFACE to c(R.color.pack_prism_surface),
                KEY_CARD to c(R.color.pack_prism_card), KEY_TEXT to c(R.color.pack_prism_text),
                KEY_DIM to c(R.color.pack_prism_dim), KEY_FAINT to c(R.color.pack_prism_faint),
                KEY_LINE to c(R.color.pack_prism_line), KEY_ACCENT to c(R.color.pack_prism_accent),
                KEY_ON_ACCENT to c(R.color.pack_prism_on_accent),
            )
            ThemePack.BRUTALIST -> mapOf(
                KEY_BG to c(R.color.pack_brutalist_bg), KEY_SURFACE to c(R.color.pack_brutalist_surface),
                KEY_CARD to c(R.color.pack_brutalist_card), KEY_TEXT to c(R.color.pack_brutalist_text),
                KEY_DIM to c(R.color.pack_brutalist_dim), KEY_FAINT to c(R.color.pack_brutalist_faint),
                KEY_LINE to c(R.color.pack_brutalist_line), KEY_ACCENT to c(R.color.pack_brutalist_accent),
                KEY_ON_ACCENT to c(R.color.pack_brutalist_on_accent),
            )
            ThemePack.CUSTOM -> mapOf(
                KEY_BG to getColor(context, KEY_BG), KEY_SURFACE to getColor(context, KEY_SURFACE),
                KEY_CARD to getColor(context, KEY_CARD), KEY_TEXT to getColor(context, KEY_TEXT),
                KEY_DIM to getColor(context, KEY_DIM), KEY_FAINT to getColor(context, KEY_FAINT),
                KEY_LINE to getColor(context, KEY_LINE), KEY_ACCENT to getColor(context, KEY_ACCENT),
                KEY_ON_ACCENT to getColor(context, KEY_ON_ACCENT),
            )
        }
    }

    private fun packToFontKey(pack: ThemePack): String = when (pack) {
        ThemePack.WARM_PAPER   -> FONT_FRAUNCES
        ThemePack.EDITORIAL    -> FONT_CORMORANT
        ThemePack.MONOLITH     -> FONT_JETBRAINS_MONO
        ThemePack.PRISM        -> FONT_SANS_SERIF
        ThemePack.BRUTALIST    -> FONT_JETBRAINS_MONO
        ThemePack.CUSTOM       -> FONT_SANS_SERIF
    }

    private fun packToCornerKey(pack: ThemePack): String = when (pack) {
        ThemePack.WARM_PAPER   -> CORNER_MEDIUM
        ThemePack.EDITORIAL    -> CORNER_SMALL
        ThemePack.MONOLITH     -> CORNER_SMALL
        ThemePack.PRISM        -> CORNER_LARGE
        ThemePack.BRUTALIST    -> CORNER_NONE
        ThemePack.CUSTOM       -> CORNER_MEDIUM
    }

    /**
     * Infer initial mode from the source pack.
     * Dark-only packs → dark, otherwise check if the system is currently dark.
     */
    private fun packToModeKey(pack: ThemePack, context: Context): String {
        if (pack.isDarkOnly) return MODE_DARK
        if (pack.isLightOnly) return MODE_LIGHT
        // For dual-mode packs, use the system's current mode
        val isDark = (context.resources.configuration.uiMode
                and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES
        return if (isDark) MODE_DARK else MODE_LIGHT
    }

    private fun Context.forCustomThemeMode(mode: String): Context {
        val nightMode = if (mode == MODE_DARK) {
            Configuration.UI_MODE_NIGHT_YES
        } else {
            Configuration.UI_MODE_NIGHT_NO
        }
        val config = Configuration(resources.configuration)
        config.uiMode = (config.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or nightMode
        return createConfigurationContext(config)
    }
}
