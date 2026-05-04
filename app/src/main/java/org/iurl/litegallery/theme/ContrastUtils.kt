package org.iurl.litegallery.theme

import androidx.core.graphics.ColorUtils

/**
 * Pure helpers around WCAG 2.1 contrast ratio used by the Custom theme editor
 * to surface low-contrast colour combinations to the user.
 *
 * Thresholds follow WCAG AA/AAA for normal-size text:
 *   AAA  ≥ 7.0
 *   AA   ≥ 4.5
 *   FAIL  < 4.5
 */
object ContrastUtils {

    enum class Grade { AAA, AA, FAIL }

    /**
     * Describes one of the colour keys whose pairing with the colour currently
     * being edited should be inspected for contrast.
     *
     * [opponentIsForeground] tells the caller which role to give each colour
     * when computing the ratio. A `true` value means the *opponent* is the
     * text and the edited colour is the background; `false` means the opposite.
     */
    data class Opponent(
        val key: String,
        val opponentIsForeground: Boolean,
    )

    fun ratio(fg: Int, bg: Int): Double =
        ColorUtils.calculateContrast(fg, CustomThemeStore.toOpaqueRgb(bg))

    fun grade(ratio: Double): Grade = when {
        ratio >= 7.0 -> Grade.AAA
        ratio >= 4.5 -> Grade.AA
        else -> Grade.FAIL
    }

    /**
     * For the colour key currently being edited, return the list of opponent
     * keys whose pairing should be checked for contrast.
     */
    fun opponentsFor(key: String): List<Opponent> = when (key) {
        CustomThemeStore.KEY_BG -> listOf(
            Opponent(CustomThemeStore.KEY_TEXT, opponentIsForeground = true),
            Opponent(CustomThemeStore.KEY_DIM, opponentIsForeground = true),
        )
        CustomThemeStore.KEY_SURFACE -> listOf(
            Opponent(CustomThemeStore.KEY_TEXT, opponentIsForeground = true),
        )
        CustomThemeStore.KEY_CARD -> listOf(
            Opponent(CustomThemeStore.KEY_TEXT, opponentIsForeground = true),
            Opponent(CustomThemeStore.KEY_DIM, opponentIsForeground = true),
        )
        CustomThemeStore.KEY_TEXT -> listOf(
            Opponent(CustomThemeStore.KEY_BG, opponentIsForeground = false),
            Opponent(CustomThemeStore.KEY_SURFACE, opponentIsForeground = false),
            Opponent(CustomThemeStore.KEY_CARD, opponentIsForeground = false),
        )
        CustomThemeStore.KEY_DIM -> listOf(
            Opponent(CustomThemeStore.KEY_BG, opponentIsForeground = false),
            Opponent(CustomThemeStore.KEY_CARD, opponentIsForeground = false),
        )
        CustomThemeStore.KEY_ACCENT -> listOf(
            Opponent(CustomThemeStore.KEY_ON_ACCENT, opponentIsForeground = true),
        )
        CustomThemeStore.KEY_ON_ACCENT -> listOf(
            Opponent(CustomThemeStore.KEY_ACCENT, opponentIsForeground = false),
        )
        else -> emptyList()
    }
}
