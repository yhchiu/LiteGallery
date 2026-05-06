package org.iurl.litegallery.theme

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.util.TypedValue
import androidx.core.content.ContextCompat
import org.iurl.litegallery.R
import org.iurl.litegallery.ThemeHelper

data class CustomThemePalette(
    val background: Int,
    val surface: Int,
    val card: Int,
    val text: Int,
    val dim: Int,
    val faint: Int,
    val line: Int,
    val accent: Int,
    val onAccent: Int,
    val gradientStart: Int?,
    val gradientEnd: Int?,
    val gradientAngle: Int?,
) {
    val hasGradient: Boolean
        get() = gradientStart != null && gradientEnd != null && gradientAngle != null

    fun colorForAttr(attr: Int): Int? = when {
        attr.isAnyOf(
            android.R.attr.colorBackground,
            android.R.attr.windowBackground,
        ) -> background

        attr.isAnyOf(
            com.google.android.material.R.attr.colorSurfaceVariant,
            com.google.android.material.R.attr.colorSecondaryContainer,
        ) -> surface

        attr.isAnyOf(
            com.google.android.material.R.attr.colorSurface,
            com.google.android.material.R.attr.colorPrimaryContainer,
        ) -> card

        attr.isAnyOf(
            android.R.attr.textColorPrimary,
            androidx.appcompat.R.attr.actionMenuTextColor,
            com.google.android.material.R.attr.colorOnSurface,
            com.google.android.material.R.attr.colorOnPrimaryContainer,
            com.google.android.material.R.attr.colorOnSecondaryContainer,
        ) -> text

        attr.isAnyOf(
            android.R.attr.textColorSecondary,
            com.google.android.material.R.attr.colorOnSurfaceVariant,
        ) -> dim

        attr == com.google.android.material.R.attr.colorOutlineVariant -> faint
        attr == com.google.android.material.R.attr.colorOutline -> line

        attr.isAnyOf(
            android.R.attr.colorAccent,
            androidx.appcompat.R.attr.colorPrimary,
            androidx.appcompat.R.attr.colorAccent,
            androidx.appcompat.R.attr.colorControlActivated,
            com.google.android.material.R.attr.colorPrimary,
            com.google.android.material.R.attr.colorSecondary,
        ) -> accent

        attr.isAnyOf(
            com.google.android.material.R.attr.colorOnPrimary,
            com.google.android.material.R.attr.colorOnSecondary,
        ) -> onAccent

        else -> null
    }

    fun replacementFor(defaultColor: Int?, defaults: CustomThemePalette): Int? {
        if (defaultColor == null) return null
        return when (defaultColor) {
            defaults.background -> background
            defaults.surface -> surface
            defaults.card -> card
            defaults.text -> text
            defaults.dim -> dim
            defaults.faint -> faint
            defaults.line -> line
            defaults.accent -> accent
            defaults.onAccent -> onAccent
            else -> null
        }
    }

    companion object {
        fun fromStore(context: Context): CustomThemePalette = CustomThemePalette(
            background = CustomThemeStore.getColor(context, CustomThemeStore.KEY_BG),
            surface = CustomThemeStore.getColor(context, CustomThemeStore.KEY_SURFACE),
            card = CustomThemeStore.getColor(context, CustomThemeStore.KEY_CARD),
            text = CustomThemeStore.getColor(context, CustomThemeStore.KEY_TEXT),
            dim = CustomThemeStore.getColor(context, CustomThemeStore.KEY_DIM),
            faint = CustomThemeStore.getColor(context, CustomThemeStore.KEY_FAINT),
            line = CustomThemeStore.getColor(context, CustomThemeStore.KEY_LINE),
            accent = CustomThemeStore.getColor(context, CustomThemeStore.KEY_ACCENT),
            onAccent = CustomThemeStore.getColor(context, CustomThemeStore.KEY_ON_ACCENT),
            gradientStart = CustomThemeStore.getGradientStart(context),
            gradientEnd = CustomThemeStore.getGradientEnd(context),
            gradientAngle = CustomThemeStore.getGradientAngle(context),
        )

        fun resourcePlaceholders(context: Context): CustomThemePalette {
            val base = context.applicationContext.createConfigurationContext(
                context.resources.configuration,
            )
            return CustomThemePalette(
                background = base.color(R.color.custom_bg),
                surface = base.color(R.color.custom_surface),
                card = base.color(R.color.custom_card),
                text = base.color(R.color.custom_text),
                dim = base.color(R.color.custom_dim),
                faint = base.color(R.color.custom_faint),
                line = base.color(R.color.custom_line),
                accent = base.color(R.color.custom_accent),
                onAccent = base.color(R.color.custom_on_accent),
                gradientStart = null,
                gradientEnd = null,
                gradientAngle = null,
            )
        }

        private fun Context.color(resId: Int): Int = ContextCompat.getColor(this, resId)
    }
}

object ThemeColorResolver {
    fun customPaletteOrNull(context: Context): CustomThemePalette? {
        return if (ThemeHelper.getCurrentPack(context).isCustom) {
            CustomThemePalette.fromStore(context)
        } else {
            null
        }
    }

    fun resolveColor(
        context: Context,
        attr: Int,
        fallback: Int = Color.TRANSPARENT,
    ): Int {
        customPaletteOrNull(context)?.colorForAttr(attr)?.let { return it }
        return resolveThemeColor(context, attr, fallback)
    }

    fun resolveColorStateList(
        context: Context,
        attr: Int,
        fallback: Int = Color.TRANSPARENT,
    ): ColorStateList = ColorStateList.valueOf(resolveColor(context, attr, fallback))

    fun resolveThemeColor(
        context: Context,
        attr: Int,
        fallback: Int = Color.TRANSPARENT,
    ): Int {
        val typedValue = TypedValue()
        if (!context.theme.resolveAttribute(attr, typedValue, true)) return fallback
        return when {
            typedValue.resourceId != 0 -> ContextCompat.getColor(context, typedValue.resourceId)
            typedValue.type in TypedValue.TYPE_FIRST_COLOR_INT..TypedValue.TYPE_LAST_COLOR_INT -> typedValue.data
            typedValue.data != 0 -> typedValue.data
            else -> fallback
        }
    }
}

private fun Int.isAnyOf(vararg attrs: Int): Boolean = attrs.any { this == it }
