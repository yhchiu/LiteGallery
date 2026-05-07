package org.iurl.litegallery

import android.graphics.drawable.GradientDrawable
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import org.iurl.litegallery.theme.ThemeColorResolver

object TonalCountChipStyler {
    private const val FILL_PRIMARY_BLEND = 0.14f
    private const val STROKE_ALPHA = 0x4A
    private const val MIN_TEXT_CONTRAST = 4.5
    private const val STROKE_WIDTH_DP = 1f
    private const val PILL_RADIUS_DP = 999f

    fun apply(chip: TextView) {
        val context = chip.context
        val primary = ThemeColorResolver.resolveColor(
            context,
            com.google.android.material.R.attr.colorPrimary,
        )
        val surface = ThemeColorResolver.resolveColor(
            context,
            com.google.android.material.R.attr.colorSurface,
        )
        val onSurface = ThemeColorResolver.resolveColor(
            context,
            com.google.android.material.R.attr.colorOnSurface,
        )
        val fill = ColorUtils.blendARGB(surface, primary, FILL_PRIMARY_BLEND)
        val density = context.resources.displayMetrics.density

        chip.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = PILL_RADIUS_DP * density
            setColor(fill)
            setStroke((STROKE_WIDTH_DP * density).toInt().coerceAtLeast(1), primary.withAlpha(STROKE_ALPHA))
        }
        chip.setTextColor(
            if (ColorUtils.calculateContrast(primary, fill) >= MIN_TEXT_CONTRAST) {
                primary
            } else {
                onSurface
            },
        )
    }

    private fun Int.withAlpha(alpha: Int): Int =
        (this and 0x00FFFFFF) or (alpha.coerceIn(0, 255) shl 24)
}
