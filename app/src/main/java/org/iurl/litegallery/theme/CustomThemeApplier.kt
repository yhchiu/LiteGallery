package org.iurl.litegallery.theme

import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.content.DialogInterface
import android.content.res.ColorStateList
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckedTextView
import android.widget.CompoundButton
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.appcompat.widget.Toolbar
import androidx.core.graphics.drawable.DrawableCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.progressindicator.CircularProgressIndicator
import org.iurl.litegallery.R

object CustomThemeApplier {

    fun apply(activity: Activity) {
        if (ThemeColorResolver.customPaletteOrNull(activity) == null) return

        val content = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
        val palette = CustomThemePalette.fromStore(activity)
        val defaults = CustomThemePalette.resourcePlaceholders(activity)
        val generation = CustomThemeStore.getGeneration()

        applyToView(content, palette, defaults, generation)
        content.post { applyToView(content, palette, defaults, generation) }
    }

    fun apply(dialog: AlertDialog) {
        applyDialog(dialog) { which -> dialog.getButton(which) }
    }

    fun apply(dialog: androidx.appcompat.app.AlertDialog) {
        applyDialog(dialog) { which -> dialog.getButton(which) }
    }

    private fun applyDialog(dialog: Dialog, buttonFor: (Int) -> Button?) {
        val palette = ThemeColorResolver.customPaletteOrNull(dialog.context) ?: return
        val defaults = CustomThemePalette.resourcePlaceholders(dialog.context)
        val generation = CustomThemeStore.getGeneration()

        val radius = 8f * dialog.context.resources.displayMetrics.density
        dialog.window?.setBackgroundDrawable(
            GradientDrawable().apply {
                setColor(palette.card)
                cornerRadius = radius
            },
        )
        buttonFor(DialogInterface.BUTTON_POSITIVE)?.setTextColor(palette.accent)
        buttonFor(DialogInterface.BUTTON_NEGATIVE)?.setTextColor(palette.accent)
        buttonFor(DialogInterface.BUTTON_NEUTRAL)?.setTextColor(palette.accent)

        val decor = dialog.window?.decorView ?: return
        applyToDialogView(decor, palette, defaults, generation)
        decor.post { applyToDialogView(decor, palette, defaults, generation) }
    }

    fun applyPrimaryAction(button: MaterialButton) {
        val palette = ThemeColorResolver.customPaletteOrNull(button.context) ?: return
        button.backgroundTintList = ColorStateList.valueOf(palette.accent)
        button.setTextColor(palette.onAccent)
        button.iconTint = ColorStateList.valueOf(palette.onAccent)
        button.rippleColor = ColorStateList.valueOf(palette.onAccent.withAlpha(0x33))
    }

    private fun applyToView(
        view: View,
        palette: CustomThemePalette,
        defaults: CustomThemePalette,
        generation: Int,
    ) {
        val skipSelf = view.getTag(R.id.tag_custom_theme_skip_self) == true
        val alreadyApplied = view.getTag(R.id.tag_custom_theme_applied_generation) == generation
        if (!skipSelf && !alreadyApplied) {
            applyBackground(view, palette, defaults)

            when (view) {
                is AppBarLayout -> view.setBackgroundColor(palette.background)
                is Toolbar -> applyToolbar(view, palette)
                is MaterialCardView -> applyCard(view, palette, defaults)
                is MaterialButton -> applyButton(view, palette, defaults)
                is Chip -> applyChip(view, palette, defaults)
                is CircularProgressIndicator -> view.setIndicatorColor(palette.accent)
                is SwipeRefreshLayout -> {
                    view.setColorSchemeColors(palette.accent)
                    view.setProgressBackgroundColorSchemeColor(palette.card)
                }
                is SwitchCompat -> applySwitch(view, palette, defaults)
                is CompoundButton -> applyCompoundButton(view, palette, defaults)
                is ImageView -> applyImageTint(view, palette, defaults)
                is TextView -> applyText(view, palette, defaults)
            }
            view.setTag(R.id.tag_custom_theme_applied_generation, generation)
        }

        if (view is RecyclerView) {
            installRecyclerListener(view, palette, defaults, generation)
        }

        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                applyToView(view.getChildAt(i), palette, defaults, generation)
            }
        }
    }

    private fun applyToDialogView(
        view: View,
        palette: CustomThemePalette,
        defaults: CustomThemePalette,
        generation: Int,
    ) {
        val skipSelf = view.getTag(R.id.tag_custom_theme_skip_self) == true
        val alreadyApplied = view.getTag(R.id.tag_custom_theme_applied_generation) == generation
        if (!skipSelf && !alreadyApplied) {
            applyBackground(view, palette, defaults)
            when (view) {
                is MaterialButton -> applyButton(view, palette, defaults)
                is Button -> view.setTextColor(palette.accent)
                is CheckedTextView -> {
                    view.checkMarkTintList = controlTint(palette)
                    view.compoundDrawableTintList = controlTint(palette)
                    view.setTextColor(palette.text)
                }
                is SwitchCompat -> applySwitch(view, palette, defaults)
                is CompoundButton -> {
                    applyCompoundButton(view, palette, defaults)
                    view.setTextColor(palette.text)
                }
                is TextView -> view.setTextColor(palette.text)
                is ImageView -> applyImageTint(view, palette, defaults)
                is MaterialCardView -> applyCard(view, palette, defaults)
            }
            view.setTag(R.id.tag_custom_theme_applied_generation, generation)
        }

        if (view is RecyclerView) {
            installRecyclerListener(view, palette, defaults, generation)
        }

        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                applyToDialogView(view.getChildAt(i), palette, defaults, generation)
            }
        }
    }

    private fun installRecyclerListener(
        recyclerView: RecyclerView,
        palette: CustomThemePalette,
        defaults: CustomThemePalette,
        generation: Int,
    ) {
        if (recyclerView.getTag(R.id.tag_custom_theme_child_listener) == true) return

        recyclerView.addOnChildAttachStateChangeListener(
            object : RecyclerView.OnChildAttachStateChangeListener {
                override fun onChildViewAttachedToWindow(view: View) {
                    applyToView(view, palette, defaults, generation)
                }

                override fun onChildViewDetachedFromWindow(view: View) = Unit
            },
        )
        recyclerView.setTag(R.id.tag_custom_theme_child_listener, true)
    }

    private fun applyBackground(view: View, palette: CustomThemePalette, defaults: CustomThemePalette) {
        when (val background = view.background) {
            is ColorDrawable -> {
                mapSurfaceColor(background.color, defaults, palette)
                    ?.let { view.setBackgroundColor(it) }
            }
            is GradientDrawable -> {
                val color = background.color?.defaultColor
                mapSurfaceColor(color, defaults, palette)
                    ?.let { background.setColor(it) }
            }
        }
        mapSurfaceColor(view.backgroundTintList?.defaultColor, defaults, palette)
            ?.let { view.backgroundTintList = ColorStateList.valueOf(it) }
    }

    private fun applyToolbar(toolbar: Toolbar, palette: CustomThemePalette) {
        toolbar.setTitleTextColor(palette.text)
        toolbar.setSubtitleTextColor(palette.dim)
        tintDrawable(toolbar.navigationIcon, palette.text)
        tintDrawable(toolbar.overflowIcon, palette.text)
        toolbar.setBackgroundColor(palette.background)

        for (i in 0 until toolbar.menu.size()) {
            tintDrawable(toolbar.menu.getItem(i).icon, palette.text)
        }
    }

    private fun applyCard(card: MaterialCardView, palette: CustomThemePalette, defaults: CustomThemePalette) {
        mapSurfaceColor(card.cardBackgroundColor.defaultColor, defaults, palette)
            ?.let { card.setCardBackgroundColor(it) }
        mapColor(card.strokeColor, defaults, palette)
            ?.let { card.strokeColor = it }
    }

    private fun applyButton(button: MaterialButton, palette: CustomThemePalette, defaults: CustomThemePalette) {
        val mappedBackground = mapSurfaceColor(button.backgroundTintList?.defaultColor, defaults, palette)
        if (mappedBackground != null) {
            button.backgroundTintList = ColorStateList.valueOf(mappedBackground)
            if (mappedBackground == palette.accent) {
                button.setTextColor(palette.onAccent)
                button.iconTint = ColorStateList.valueOf(palette.onAccent)
                return
            }
        }

        mapTextColor(button.currentTextColor, defaults, palette)?.let { button.setTextColor(it) }
        mapIconColor(button.iconTint?.defaultColor, defaults, palette)
            ?.let { button.iconTint = ColorStateList.valueOf(it) }
    }

    private fun applyCompoundButton(
        button: CompoundButton,
        palette: CustomThemePalette,
        defaults: CustomThemePalette,
    ) {
        button.buttonTintList = controlTint(palette)
        applyText(button, palette, defaults)
    }

    private fun applySwitch(
        switch: SwitchCompat,
        palette: CustomThemePalette,
        defaults: CustomThemePalette,
    ) {
        switch.thumbTintList = controlTint(palette)
        switch.trackTintList = switchTrackTint(palette)
        applyText(switch, palette, defaults)
    }

    private fun applyChip(chip: Chip, palette: CustomThemePalette, defaults: CustomThemePalette) {
        mapSurfaceColor(chip.chipBackgroundColor?.defaultColor, defaults, palette)
            ?.let { chip.chipBackgroundColor = ColorStateList.valueOf(it) }
        mapColor(chip.chipStrokeColor?.defaultColor, defaults, palette)
            ?.let { chip.chipStrokeColor = ColorStateList.valueOf(it) }
        mapTextColor(chip.currentTextColor, defaults, palette)?.let { chip.setTextColor(it) }
    }

    private fun applyImageTint(
        imageView: ImageView,
        palette: CustomThemePalette,
        defaults: CustomThemePalette,
    ) {
        mapIconColor(imageView.imageTintList?.defaultColor, defaults, palette)
            ?.let { imageView.imageTintList = ColorStateList.valueOf(it) }
    }

    private fun applyText(textView: TextView, palette: CustomThemePalette, defaults: CustomThemePalette) {
        mapTextColor(textView.currentTextColor, defaults, palette)?.let {
            textView.setTextColor(it)
        }
        mapIconColor(textView.compoundDrawableTintList?.defaultColor, defaults, palette)?.let {
            textView.compoundDrawableTintList = ColorStateList.valueOf(it)
        }
    }

    private fun tintDrawable(drawable: android.graphics.drawable.Drawable?, color: Int) {
        if (drawable == null) return
        DrawableCompat.setTint(drawable.mutate(), color)
    }

    private fun mapTextColor(
        color: Int,
        defaults: CustomThemePalette,
        palette: CustomThemePalette,
    ): Int? = when (color) {
        defaults.text -> palette.text
        defaults.dim -> palette.dim
        defaults.accent -> palette.accent
        defaults.onAccent -> palette.onAccent
        else -> null
    }

    private fun mapIconColor(
        color: Int?,
        defaults: CustomThemePalette,
        palette: CustomThemePalette,
    ): Int? {
        if (color == null) return null
        return when (color) {
            defaults.text -> palette.text
            defaults.dim -> palette.dim
            defaults.accent -> palette.accent
            defaults.onAccent -> palette.onAccent
            else -> null
        }
    }

    private fun mapSurfaceColor(
        color: Int?,
        defaults: CustomThemePalette,
        palette: CustomThemePalette,
    ): Int? {
        if (color == null) return null
        return when (color) {
            defaults.background -> palette.background
            defaults.surface -> palette.surface
            defaults.card -> palette.card
            defaults.accent -> palette.accent
            else -> mapColor(color, defaults, palette)
        }
    }

    private fun mapColor(
        color: Int?,
        defaults: CustomThemePalette,
        palette: CustomThemePalette,
    ): Int? = palette.replacementFor(color, defaults)

    private fun controlTint(palette: CustomThemePalette): ColorStateList = ColorStateList(
        arrayOf(
            intArrayOf(android.R.attr.state_checked),
            intArrayOf(-android.R.attr.state_enabled),
            intArrayOf(),
        ),
        intArrayOf(
            palette.accent,
            palette.faint,
            palette.dim,
        ),
    )

    private fun switchTrackTint(palette: CustomThemePalette): ColorStateList = ColorStateList(
        arrayOf(
            intArrayOf(android.R.attr.state_checked),
            intArrayOf(-android.R.attr.state_enabled),
            intArrayOf(),
        ),
        intArrayOf(
            palette.accent.withAlpha(0x66),
            palette.faint,
            palette.dim.withAlpha(0x44),
        ),
    )

    private fun Int.withAlpha(alpha: Int): Int =
        (this and 0x00FFFFFF) or (alpha.coerceIn(0, 255) shl 24)
}
