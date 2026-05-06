package org.iurl.litegallery

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import org.iurl.litegallery.theme.ContrastUtils
import org.iurl.litegallery.theme.CustomThemeApplier
import org.iurl.litegallery.theme.CustomThemePalette
import org.iurl.litegallery.theme.CustomThemeStore
import org.iurl.litegallery.theme.Mode
import org.iurl.litegallery.theme.ThemeColorResolver
import org.iurl.litegallery.theme.ThemePack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class CustomThemeColorResolutionTest {

    private lateinit var app: Context

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        PreferenceManager.getDefaultSharedPreferences(app).edit().clear().commit()
        CustomThemeStore.resetToDefaults(app)
    }

    @Test
    fun customPackResolverUsesStoredSemanticColors() {
        PreferenceManager.getDefaultSharedPreferences(app).edit()
            .putString(ThemeHelper.THEME_PACK_PREFERENCE_KEY, ThemePack.CUSTOM.key)
            .commit()
        CustomThemeStore.setColor(app, CustomThemeStore.KEY_ACCENT, Color.RED)
        CustomThemeStore.setColor(app, CustomThemeStore.KEY_ON_ACCENT, Color.YELLOW)
        CustomThemeStore.setColor(app, CustomThemeStore.KEY_TEXT, Color.GREEN)

        assertEquals(
            Color.RED,
            ThemeColorResolver.resolveColor(app, com.google.android.material.R.attr.colorPrimary),
        )
        assertEquals(
            Color.YELLOW,
            ThemeColorResolver.resolveColor(app, com.google.android.material.R.attr.colorOnPrimary),
        )
        assertEquals(
            Color.GREEN,
            ThemeColorResolver.resolveColor(app, android.R.attr.textColorPrimary),
        )
    }

    @Test
    fun resetFromPackUsesRequestedDarkModeColors() {
        val expectedBg = app.withNightMode().getColorCompat(R.color.pack_warm_paper_bg)

        CustomThemeStore.resetFromPack(app, ThemePack.WARM_PAPER, CustomThemeStore.MODE_DARK)

        assertEquals(expectedBg, CustomThemeStore.getColor(app, CustomThemeStore.KEY_BG))
    }

    @Test
    fun runtimeApplierMapsPlaceholderTextColorToStoredCustomColor() {
        PreferenceManager.getDefaultSharedPreferences(app).edit()
            .putString(ThemeHelper.THEME_PACK_PREFERENCE_KEY, ThemePack.CUSTOM.key)
            .commit()
        CustomThemeStore.setColor(app, CustomThemeStore.KEY_TEXT, Color.GREEN)

        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val placeholderText = app.getColorCompat(R.color.custom_text)
        val textView = TextView(activity).apply {
            setTextColor(placeholderText)
        }
        val root = LinearLayout(activity).apply {
            addView(textView)
        }
        activity.setContentView(root)

        CustomThemeApplier.apply(activity)

        assertEquals(Color.GREEN, textView.currentTextColor)
    }

    @Test
    fun runtimeApplierMapsPlaceholderGradientBackgroundToStoredCustomColor() {
        PreferenceManager.getDefaultSharedPreferences(app).edit()
            .putString(ThemeHelper.THEME_PACK_PREFERENCE_KEY, ThemePack.CUSTOM.key)
            .commit()
        CustomThemeStore.setColor(app, CustomThemeStore.KEY_ACCENT, Color.CYAN)

        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val placeholderAccent = app.getColorCompat(R.color.custom_accent)
        val label = TextView(activity).apply {
            background = GradientDrawable().apply {
                setColor(placeholderAccent)
            }
        }
        activity.setContentView(LinearLayout(activity).apply { addView(label) })

        CustomThemeApplier.apply(activity)

        assertEquals(Color.CYAN, (label.background as GradientDrawable).color?.defaultColor)
    }

    @Test
    fun runtimeApplierDoesNotRemapAlreadyAppliedCustomColors() {
        PreferenceManager.getDefaultSharedPreferences(app).edit()
            .putString(ThemeHelper.THEME_PACK_PREFERENCE_KEY, ThemePack.CUSTOM.key)
            .commit()
        val placeholderBackground = app.getColorCompat(R.color.custom_bg)
        val placeholderAccent = app.getColorCompat(R.color.custom_accent)
        CustomThemeStore.setColor(app, CustomThemeStore.KEY_BG, placeholderAccent)
        CustomThemeStore.setColor(app, CustomThemeStore.KEY_ACCENT, Color.RED)

        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val label = TextView(activity).apply {
            background = GradientDrawable().apply {
                setColor(placeholderBackground)
            }
        }
        activity.setContentView(LinearLayout(activity).apply { addView(label) })

        CustomThemeApplier.apply(activity)
        CustomThemeApplier.apply(activity)

        assertEquals(placeholderAccent, (label.background as GradientDrawable).color?.defaultColor)
    }

    @Test
    fun customModePreferenceWritesCustomThemeStore() {
        PreferenceManager.getDefaultSharedPreferences(app).edit()
            .putString(ThemeHelper.THEME_PACK_PREFERENCE_KEY, ThemePack.CUSTOM.key)
            .commit()
        CustomThemeStore.setMode(app, CustomThemeStore.MODE_LIGHT)
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()

        ThemeHelper.setModePreference(activity, Mode.DARK)

        assertEquals(CustomThemeStore.MODE_DARK, CustomThemeStore.getMode(app))
    }

    @Test
    fun setColorStoresEditableColorsAsOpaqueRgb() {
        CustomThemeStore.setColor(app, CustomThemeStore.KEY_BG, 0x40112233)

        assertEquals(0xff112233.toInt(), CustomThemeStore.getColor(app, CustomThemeStore.KEY_BG))
    }

    @Test
    fun customGradientStoresOpaqueColorsAndClearsAsAUnit() {
        CustomThemeStore.setGradient(app, 0x40112233, 0x40445566, 135)

        assertTrue(CustomThemeStore.hasGradient(app))
        assertEquals(0xff112233.toInt(), CustomThemeStore.getGradientStart(app))
        assertEquals(0xff445566.toInt(), CustomThemeStore.getGradientEnd(app))
        assertEquals(135, CustomThemeStore.getGradientAngle(app))

        CustomThemeStore.clearGradient(app)

        assertFalse(CustomThemeStore.hasGradient(app))
        assertEquals(null, CustomThemeStore.getGradientStart(app))
        assertEquals(null, CustomThemeStore.getGradientEnd(app))
        assertEquals(null, CustomThemeStore.getGradientAngle(app))
    }

    @Test
    fun hasGradientDoesNotCleanDirtyPartialStateOrBumpGeneration() {
        val prefs = app.getSharedPreferences(CustomThemeStore.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putInt(CustomThemeStore.KEY_GRADIENT_START, 0xff112233.toInt())
            .commit()
        val generationBefore = CustomThemeStore.getGeneration()

        assertFalse(CustomThemeStore.hasGradient(app))
        assertTrue(prefs.contains(CustomThemeStore.KEY_GRADIENT_START))
        assertEquals(generationBefore, CustomThemeStore.getGeneration())
    }

    @Test
    fun sanitizeGradientCleansDirtyPartialStateAndBumpsGeneration() {
        val prefs = app.getSharedPreferences(CustomThemeStore.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putInt(CustomThemeStore.KEY_GRADIENT_START, 0xff112233.toInt())
            .commit()
        val generationBefore = CustomThemeStore.getGeneration()

        assertTrue(CustomThemeStore.sanitizeGradient(app))

        assertFalse(prefs.contains(CustomThemeStore.KEY_GRADIENT_START))
        assertTrue(CustomThemeStore.getGeneration() > generationBefore)
    }

    @Test
    fun initializeFromPrismCopiesGradientTokens() {
        CustomThemeStore.initializeFromPack(app, ThemePack.PRISM)

        assertTrue(CustomThemeStore.hasGradient(app))
        assertEquals(app.getColorCompat(R.color.pack_prism_gradient_start), CustomThemeStore.getGradientStart(app))
        assertEquals(app.getColorCompat(R.color.pack_prism_gradient_end), CustomThemeStore.getGradientEnd(app))
        assertEquals(135, CustomThemeStore.getGradientAngle(app))
    }

    @Test
    fun contrastRatioToleratesTransparentBackgroundInput() {
        val ratio = ContrastUtils.ratio(Color.BLACK, 0x00112233)

        assertEquals(
            ContrastUtils.ratio(Color.BLACK, 0xff112233.toInt()),
            ratio,
            0.0001,
        )
    }

    @Test
    fun themePackAdapterAppliesStoredCustomAccentToActiveControls() {
        PreferenceManager.getDefaultSharedPreferences(app).edit()
            .putString(ThemeHelper.THEME_PACK_PREFERENCE_KEY, ThemePack.CUSTOM.key)
            .commit()
        CustomThemeStore.setColor(app, CustomThemeStore.KEY_ACCENT, Color.CYAN)
        CustomThemeStore.setColor(app, CustomThemeStore.KEY_ON_ACCENT, Color.YELLOW)

        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        activity.setTheme(R.style.Theme_LiteGallery_Pack_Custom_NoActionBar)
        val parent = RecyclerView(activity).apply {
            layoutManager = LinearLayoutManager(activity)
        }
        val adapter = ThemePackAdapter(
            packs = listOf(ThemePack.CUSTOM),
            activeKey = ThemePack.CUSTOM.key,
            onPickPack = {},
        )

        val packHolder = adapter.onCreateViewHolder(parent, adapter.getItemViewType(0))
            as ThemePackAdapter.PackVH
        adapter.onBindViewHolder(packHolder, 0)

        assertEquals(Color.CYAN, packHolder.card.strokeColor)
        assertEquals(Color.CYAN, packHolder.checkFilled.backgroundTintList?.defaultColor)
        assertEquals(Color.YELLOW, packHolder.checkFilled.imageTintList?.defaultColor)

        val buttonHolder = adapter.onCreateViewHolder(parent, adapter.getItemViewType(1))
        val button = (buttonHolder.itemView as LinearLayout).getChildAt(0)
            as com.google.android.material.button.MaterialButton
        assertEquals(Color.CYAN, button.backgroundTintList?.defaultColor)
        assertEquals(Color.YELLOW, button.currentTextColor)
    }

    @Test
    fun themePackSwatchesAreNotRemappedByRuntimeApplier() {
        PreferenceManager.getDefaultSharedPreferences(app).edit()
            .putString(ThemeHelper.THEME_PACK_PREFERENCE_KEY, ThemePack.CUSTOM.key)
            .commit()
        CustomThemeStore.setColor(app, CustomThemeStore.KEY_ACCENT, Color.CYAN)

        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        activity.setTheme(R.style.Theme_LiteGallery_Pack_Custom_NoActionBar)
        val parent = RecyclerView(activity).apply {
            layoutManager = LinearLayoutManager(activity)
        }
        val adapter = ThemePackAdapter(
            packs = listOf(ThemePack.WARM_PAPER),
            activeKey = ThemePack.CUSTOM.key,
            onPickPack = {},
        )
        val holder = adapter.onCreateViewHolder(parent, adapter.getItemViewType(0))
            as ThemePackAdapter.PackVH
        adapter.onBindViewHolder(holder, 0)
        val expectedAccent = activity.getColorCompat(ThemePack.WARM_PAPER.swatchAccent)

        activity.setContentView(LinearLayout(activity).apply { addView(holder.itemView) })
        CustomThemeApplier.apply(activity)

        val actualAccent = (holder.swatchAccent.background as android.graphics.drawable.ColorDrawable).color
        assertEquals(expectedAccent, actualAccent)
    }

    @Test
    fun themePackAdapterShowsPrismGradientInAccentSwatch() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        activity.setTheme(R.style.Theme_LiteGallery_Pack_Prism_NoActionBar)
        val parent = RecyclerView(activity).apply {
            layoutManager = LinearLayoutManager(activity)
        }
        val adapter = ThemePackAdapter(
            packs = listOf(ThemePack.PRISM),
            activeKey = ThemePack.PRISM.key,
            onPickPack = {},
        )
        val holder = adapter.onCreateViewHolder(parent, adapter.getItemViewType(0))
            as ThemePackAdapter.PackVH

        adapter.onBindViewHolder(holder, 0)

        assertTrue(holder.swatchAccent.background is GradientDrawable)
    }

    @Test
    fun runtimeApplierSkipsTaggedSubtrees() {
        PreferenceManager.getDefaultSharedPreferences(app).edit()
            .putString(ThemeHelper.THEME_PACK_PREFERENCE_KEY, ThemePack.CUSTOM.key)
            .commit()
        CustomThemeStore.setColor(app, CustomThemeStore.KEY_TEXT, Color.GREEN)

        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val placeholderText = app.getColorCompat(R.color.custom_text)
        val textView = TextView(activity).apply {
            setTextColor(placeholderText)
        }
        val skippedContainer = LinearLayout(activity).apply {
            setTag(R.id.tag_custom_theme_skip_subtree, true)
            addView(textView)
        }
        activity.setContentView(skippedContainer)

        CustomThemeApplier.apply(activity)

        assertEquals(placeholderText, textView.currentTextColor)
    }

    @Test
    fun runtimeApplierAppliesCustomTintToCheckboxes() {
        PreferenceManager.getDefaultSharedPreferences(app).edit()
            .putString(ThemeHelper.THEME_PACK_PREFERENCE_KEY, ThemePack.CUSTOM.key)
            .commit()
        CustomThemeStore.setColor(app, CustomThemeStore.KEY_ACCENT, Color.CYAN)

        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val checkBox = CheckBox(activity)
        activity.setContentView(LinearLayout(activity).apply { addView(checkBox) })

        CustomThemeApplier.apply(activity)

        val checkedColor = checkBox.buttonTintList?.getColorForState(
            intArrayOf(android.R.attr.state_checked),
            Color.TRANSPARENT,
        )
        assertEquals(Color.CYAN, checkedColor)
    }

    @Test
    fun runtimeApplierAppliesCustomColorsToAlertDialogTextAndButtons() {
        PreferenceManager.getDefaultSharedPreferences(app).edit()
            .putString(ThemeHelper.THEME_PACK_PREFERENCE_KEY, ThemePack.CUSTOM.key)
            .commit()
        CustomThemeStore.setColor(app, CustomThemeStore.KEY_TEXT, Color.GREEN)
        CustomThemeStore.setColor(app, CustomThemeStore.KEY_ACCENT, Color.CYAN)

        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val dialog = android.app.AlertDialog.Builder(activity)
            .setTitle("Title")
            .setMessage("Message")
            .setPositiveButton("OK", null)
            .create()
        dialog.show()

        CustomThemeApplier.apply(dialog)

        val message = dialog.findViewById<TextView>(android.R.id.message)
        assertEquals(Color.GREEN, message?.currentTextColor)
        assertEquals(Color.CYAN, dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).currentTextColor)
    }

    @Test
    fun customPaletteMapsXmlPlaceholdersToStoredColors() {
        CustomThemeStore.setColor(app, CustomThemeStore.KEY_TEXT, Color.GREEN)
        val palette = CustomThemePalette.fromStore(app)
        val placeholders = CustomThemePalette.resourcePlaceholders(app)

        assertEquals(Color.GREEN, palette.replacementFor(placeholders.text, placeholders))
    }

    private fun Context.withNightMode(): Context {
        val config = Configuration(resources.configuration)
        config.uiMode = (config.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
            Configuration.UI_MODE_NIGHT_YES
        return createConfigurationContext(config)
    }

    private fun Context.getColorCompat(resId: Int): Int = ContextCompat.getColor(this, resId)
}
