package org.iurl.litegallery

import android.graphics.Paint
import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import org.iurl.litegallery.theme.Mode
import org.iurl.litegallery.theme.PackResolver
import org.iurl.litegallery.theme.ThemePack
import org.iurl.litegallery.theme.ThemeVariant

/**
 * Theme Pack picker — entry point from Settings.
 *
 * Renders in the currently-active pack's style (typography, surfaces, accent
 * all from the pack theme). Lists the 5 packs with capability badges + mini
 * preview swatches. Above the list, a Mode segmented control: dual-mode packs
 * (V3/V5) make all three segments tappable; single-mode packs (V1/V2/V4) show
 * unsupported segments with strike-through + faint color and a caption like
 * "Dark-locked by design.".
 *
 * Picking a pack persists it via `ThemeHelper.setPack(...)`, which either lets
 * the system trigger recreate (if night mode changed) or recreates manually.
 */
class ThemePackActivity : AppCompatActivity() {

    private var currentPackKey: String = ThemePack.DEFAULT_KEY

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeHelper.applyTheme(this)
        ThemeHelper.applyPackTheme(this, ThemeVariant.NoActionBar)

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_theme_pack)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        findViewById<View>(android.R.id.content).rootView.setOnApplyWindowInsetsListener { view, insets ->
            val sys = insets.getInsets(android.view.WindowInsets.Type.systemBars())
            view.setPadding(0, sys.top, 0, sys.bottom)
            insets
        }

        currentPackKey = ThemeHelper.getCurrentPack(this).key

        setupToolbar()
        renderModeSegment()
        renderPackList()
    }

    override fun onResume() {
        super.onResume()
        // If user changed pack from somewhere else (unlikely here, but defensive)
        // rerender. The pack-change path inside this activity calls recreate()
        // explicitly via ThemeHelper.setPack so that's already handled.
        val active = ThemeHelper.getCurrentPack(this).key
        if (active != currentPackKey) {
            recreate()
        }
    }

    private fun setupToolbar() {
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener { finish() }
    }

    /**
     * Render the Auto/Light/Dark segmented control. Segments unsupported by the
     * current pack get faint color + strike-through; the active segment is filled
     * with the primary accent.
     */
    private fun renderModeSegment() {
        val pack = ThemeHelper.getCurrentPack(this)
        val themePref = ThemeHelper.getCurrentTheme(this)
        val effectiveMode = PackResolver.resolveEffectiveMode(pack, themePref, ThemeHelper.isSystemDark(this))

        val auto = findViewById<TextView>(R.id.mode_auto)
        val light = findViewById<TextView>(R.id.mode_light)
        val dark = findViewById<TextView>(R.id.mode_dark)
        val caption = findViewById<TextView>(R.id.mode_caption)

        val segments = listOf(Mode.AUTO to auto, Mode.LIGHT to light, Mode.DARK to dark)
        for ((mode, view) in segments) {
            configureSegment(view, mode, pack, themePref, effectiveMode)
        }

        caption.text = when {
            pack.isDarkOnly -> getString(R.string.pack_caption_dark_only)
            pack.isLightOnly -> getString(R.string.pack_caption_light_only)
            else -> getString(R.string.pack_caption_dual)
        }
    }

    private fun configureSegment(
        view: TextView,
        mode: Mode,
        pack: ThemePack,
        themePref: String,
        effectiveMode: Mode,
    ) {
        val supported = pack.supportedModes.contains(mode)
        val isSelected = supported && when (mode) {
            Mode.AUTO -> themePref == ThemeHelper.THEME_AUTO && pack.isDualMode
            Mode.LIGHT -> effectiveMode == Mode.LIGHT && (pack.isLightOnly || (pack.isDualMode && themePref == ThemeHelper.THEME_LIGHT))
            Mode.DARK -> effectiveMode == Mode.DARK && (pack.isDarkOnly || (pack.isDualMode && themePref == ThemeHelper.THEME_DARK))
        }

        // Reset paint flags first
        view.paintFlags = view.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()

        when {
            isSelected -> {
                view.background = ContextCompat.getDrawable(this, R.drawable.bg_mode_segment_active)
                view.setTextColor(getThemeColor(com.google.android.material.R.attr.colorOnPrimary))
                view.setTypeface(view.typeface, Typeface.BOLD)
                view.alpha = 1f
            }
            supported -> {
                view.background = null
                view.setTextColor(getThemeColor(android.R.attr.textColorSecondary))
                view.setTypeface(view.typeface, Typeface.NORMAL)
                view.alpha = 1f
            }
            else -> {
                view.background = null
                view.setTextColor(getThemeColor(android.R.attr.textColorSecondary))
                view.setTypeface(view.typeface, Typeface.NORMAL)
                view.alpha = 0.45f
                view.paintFlags = view.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
            }
        }

        view.setOnClickListener {
            if (!supported) return@setOnClickListener
            // Dual-mode packs: switching segment changes the user's mode preference.
            // Single-mode packs (Light-only or Dark-only): the only supported segment is
            // already selected — clicking it is a no-op.
            if (pack.isSingleMode) return@setOnClickListener
            ThemeHelper.setModePreference(this, mode)
            // setDefaultNightMode triggers recreate when the mode actually changes;
            // for the AUTO->LIGHT->DARK same-resolved-mode case we still want the
            // segment highlight to update.
            renderModeSegment()
        }
    }

    private fun renderPackList() {
        val recycler = findViewById<RecyclerView>(R.id.pack_list)
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = ThemePackAdapter(
            packs = ThemePack.all(),
            activeKey = currentPackKey,
            onPickPack = { pack -> onPickPack(pack) },
        )
    }

    private fun onPickPack(pack: ThemePack) {
        if (pack.key == currentPackKey) return
        ThemeHelper.setPack(this, pack)
    }

    /**
     * Resolve a theme attribute to a color int.
     */
    private fun getThemeColor(attr: Int): Int {
        val typedValue = android.util.TypedValue()
        theme.resolveAttribute(attr, typedValue, true)
        return if (typedValue.resourceId != 0) {
            ContextCompat.getColor(this, typedValue.resourceId)
        } else {
            typedValue.data
        }
    }
}
