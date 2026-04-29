package org.iurl.litegallery.theme

import org.iurl.litegallery.R

/**
 * Light/Dark/Auto modes a Theme Pack may declare it supports.
 */
enum class Mode {
    AUTO, LIGHT, DARK;

    val prefValue: String
        get() = when (this) {
            AUTO -> "auto"
            LIGHT -> "light"
            DARK -> "dark"
        }

    companion object {
        fun fromPrefValue(value: String?): Mode = when (value) {
            "light" -> LIGHT
            "dark" -> DARK
            else -> AUTO
        }
    }
}

/**
 * The five Theme Packs shipping with LiteGallery. Each pack declares its
 * own typography, surfaces, accent and corner radii via the corresponding
 * `Theme.LiteGallery.Pack.<key>.{NoActionBar,FullScreen}` styles, plus
 * which Mode(s) it supports.
 *
 * Single-mode packs (V1/V2/V4 = Dark, future Light-only) override the user's
 * theme preference at runtime — see [PackResolver.resolveNightMode].
 */
enum class ThemePack(
    val key: String,
    val nameRes: Int,
    val subtitleRes: Int,
    val supportedModes: List<Mode>,
    val swatchBg: Int,
    val swatchText: Int,
    val swatchAccent: Int,
) {
    WARM_PAPER(
        key = "warm_paper",
        nameRes = R.string.pack_warm_paper_name,
        subtitleRes = R.string.pack_warm_paper_sub,
        supportedModes = listOf(Mode.AUTO, Mode.LIGHT, Mode.DARK),
        swatchBg = R.color.pack_warm_paper_bg,
        swatchText = R.color.pack_warm_paper_text,
        swatchAccent = R.color.pack_warm_paper_accent,
    ),
    EDITORIAL(
        key = "editorial",
        nameRes = R.string.pack_editorial_name,
        subtitleRes = R.string.pack_editorial_sub,
        supportedModes = listOf(Mode.DARK),
        swatchBg = R.color.pack_editorial_bg,
        swatchText = R.color.pack_editorial_text,
        swatchAccent = R.color.pack_editorial_accent,
    ),
    MONOLITH(
        key = "monolith",
        nameRes = R.string.pack_monolith_name,
        subtitleRes = R.string.pack_monolith_sub,
        supportedModes = listOf(Mode.DARK),
        swatchBg = R.color.pack_monolith_bg,
        swatchText = R.color.pack_monolith_text,
        swatchAccent = R.color.pack_monolith_accent,
    ),
    PRISM(
        key = "prism",
        nameRes = R.string.pack_prism_name,
        subtitleRes = R.string.pack_prism_sub,
        supportedModes = listOf(Mode.DARK),
        swatchBg = R.color.pack_prism_bg,
        swatchText = R.color.pack_prism_text,
        swatchAccent = R.color.pack_prism_accent,
    ),
    BRUTALIST(
        key = "brutalist",
        nameRes = R.string.pack_brutalist_name,
        subtitleRes = R.string.pack_brutalist_sub,
        supportedModes = listOf(Mode.AUTO, Mode.LIGHT, Mode.DARK),
        swatchBg = R.color.pack_brutalist_bg,
        swatchText = R.color.pack_brutalist_text,
        swatchAccent = R.color.pack_brutalist_accent,
    );

    val isDualMode: Boolean
        get() = supportedModes.size > 1

    val isSingleMode: Boolean
        get() = supportedModes.size == 1

    /** True if this pack's only supported mode is Dark (V1/V2/V4). */
    val isDarkOnly: Boolean
        get() = isSingleMode && supportedModes[0] == Mode.DARK

    /** True if this pack's only supported mode is Light. */
    val isLightOnly: Boolean
        get() = isSingleMode && supportedModes[0] == Mode.LIGHT

    companion object {
        const val DEFAULT_KEY = "warm_paper"

        fun fromKey(key: String?): ThemePack =
            values().firstOrNull { it.key == key } ?: WARM_PAPER

        fun all(): List<ThemePack> = values().toList()
    }
}

/**
 * Two manifest theme variants — passed to [PackResolver.pickStyle] so the
 * activity gets the correct windowFullscreen/windowActionBar set.
 */
enum class ThemeVariant {
    NoActionBar,
    FullScreen,
}
