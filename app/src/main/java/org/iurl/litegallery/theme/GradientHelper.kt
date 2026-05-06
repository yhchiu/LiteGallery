package org.iurl.litegallery.theme

import android.content.Context
import android.graphics.drawable.GradientDrawable
import androidx.core.content.ContextCompat
import org.iurl.litegallery.ThemeHelper

object GradientHelper {

    fun angleToOrientation(angle: Int): GradientDrawable.Orientation = when (angle) {
        0 -> GradientDrawable.Orientation.LEFT_RIGHT
        45 -> GradientDrawable.Orientation.BL_TR
        90 -> GradientDrawable.Orientation.BOTTOM_TOP
        135 -> GradientDrawable.Orientation.BR_TL
        180 -> GradientDrawable.Orientation.RIGHT_LEFT
        225 -> GradientDrawable.Orientation.TR_BL
        270 -> GradientDrawable.Orientation.TOP_BOTTOM
        315 -> GradientDrawable.Orientation.TL_BR
        else -> GradientDrawable.Orientation.LEFT_RIGHT
    }

    fun createForPack(
        context: Context,
        pack: ThemePack,
        cornerRadius: Float = 0f,
    ): GradientDrawable? {
        if (!pack.hasGradient) return null

        val start = ContextCompat.getColor(context, pack.gradientStartRes!!)
        val end = ContextCompat.getColor(context, pack.gradientEndRes!!)
        return create(
            start = start,
            end = end,
            angle = pack.gradientAngle!!,
            cornerRadius = cornerRadius,
        )
    }

    fun createForCustom(context: Context, cornerRadius: Float = 0f): GradientDrawable? {
        val palette = CustomThemePalette.fromStore(context)
        if (!palette.hasGradient) return null

        return create(
            start = palette.gradientStart!!,
            end = palette.gradientEnd!!,
            angle = palette.gradientAngle!!,
            cornerRadius = cornerRadius,
        )
    }

    fun createForCurrentPack(context: Context, cornerRadius: Float = 0f): GradientDrawable? {
        val pack = ThemeHelper.getCurrentPack(context)
        return if (pack.isCustom) {
            createForCustom(context, cornerRadius)
        } else {
            createForPack(context, pack, cornerRadius)
        }
    }

    fun create(
        start: Int,
        end: Int,
        angle: Int,
        cornerRadius: Float = 0f,
    ): GradientDrawable {
        return GradientDrawable(angleToOrientation(angle), intArrayOf(start, end)).apply {
            shape = GradientDrawable.RECTANGLE
            this.cornerRadius = cornerRadius
        }
    }
}
