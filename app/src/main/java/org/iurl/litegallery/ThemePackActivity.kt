package org.iurl.litegallery

import android.content.Intent
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.NestedScrollView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import org.iurl.litegallery.theme.CustomThemeStore
import org.iurl.litegallery.theme.Mode
import org.iurl.litegallery.theme.ThemeColorResolver
import org.iurl.litegallery.theme.ThemePack
import org.iurl.litegallery.theme.ThemeVariant

/**
 * Theme Pack picker — entry point from Settings.
 *
 * Renders in the currently-active pack's style (typography, surfaces, accent
 * all from the pack theme). Lists the 6 packs with capability badges + mini
 * preview swatches. Above the list, a Mode segmented control: dual-mode packs
 * make all three segments tappable; single-mode packs show unsupported segments
 * with strike-through + faint color and a caption. The Custom pack shows only
 * Light/Dark (no Auto).
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
        ThemeHelper.captureCustomThemeGeneration(this)
        setContentView(R.layout.activity_theme_pack)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById<View>(android.R.id.content).rootView) { view, insets ->
            val sys = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(0, sys.top, 0, sys.bottom)
            insets
        }

        currentPackKey = ThemeHelper.getCurrentPack(this).key

        setupToolbar()
        renderModeSegment()
        renderPackList()
        ThemeHelper.applyRuntimeCustomColors(this)
    }

    override fun onResume() {
        super.onResume()
        val active = ThemeHelper.getCurrentPack(this).key
        if (active != currentPackKey) {
            recreate()
            return
        }
        if (ThemeHelper.checkAndRecreateForCustomThemeChange(this)) return
    }

    private fun setupToolbar() {
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener { finish() }
    }

    /**
     * Render the Auto/Light/Dark segmented control. For the Custom pack,
     * Auto is always disabled since it only supports explicit Light/Dark.
     */
    private fun renderModeSegment() {
        val pack = ThemeHelper.getCurrentPack(this)
        val themePref = ThemeHelper.getCurrentTheme(this)
        val selectedMode = selectedModeFor(pack, themePref)

        val auto = findViewById<TextView>(R.id.mode_auto)
        val light = findViewById<TextView>(R.id.mode_light)
        val dark = findViewById<TextView>(R.id.mode_dark)
        val caption = findViewById<TextView>(R.id.mode_caption)

        val supportedModes = if (pack.isCustom) {
            listOf(Mode.LIGHT, Mode.DARK)
        } else {
            pack.supportedModes
        }

        val segments = listOf(Mode.AUTO to auto, Mode.LIGHT to light, Mode.DARK to dark)
        for ((mode, view) in segments) {
            configureSegment(view, mode, pack, selectedMode, supportedModes)
        }

        if (pack.isCustom) {
            val customMode = CustomThemeStore.getMode(this)
            caption.text = if (customMode == CustomThemeStore.MODE_DARK)
                getString(R.string.pack_caption_dark_only)
            else
                getString(R.string.pack_caption_light_only)
        } else {
            caption.text = when {
                pack.isDarkOnly -> getString(R.string.pack_caption_dark_only)
                pack.isLightOnly -> getString(R.string.pack_caption_light_only)
                else -> getString(R.string.pack_caption_dual)
            }
        }
    }

    private fun configureSegment(
        view: TextView,
        mode: Mode,
        pack: ThemePack,
        selectedMode: Mode,
        supportedModes: List<Mode>,
    ) {
        val supported = supportedModes.contains(mode)
        val isSelected = supported && selectedMode == mode

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
            if (!pack.isCustom && pack.isSingleMode) return@setOnClickListener
            ThemeHelper.setModePreference(this, mode)
            renderModeSegment()
            ThemeHelper.applyRuntimeCustomColors(this)
        }
    }

    private fun renderPackList() {
        val scrollView = findViewById<NestedScrollView>(R.id.theme_pack_scroll)
        val recycler = findViewById<RecyclerView>(R.id.pack_list)
        recycler.layoutManager = LinearLayoutManager(this)
        val adapter = ThemePackAdapter(
            packs = ThemePack.all(),
            activeKey = currentPackKey,
            onPickPack = { pack -> onPickPack(pack) },
        )
        recycler.adapter = adapter

        // Auto-scroll to the active pack after layout and state restoration
        val activePos = adapter.activePosition()
        if (activePos >= 0) {
            recycler.viewTreeObserver.addOnGlobalLayoutListener(object : android.view.ViewTreeObserver.OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    recycler.viewTreeObserver.removeOnGlobalLayoutListener(this)
                    scrollView.post { scrollToActivePack(scrollView, recycler, activePos) }
                }
            })
        }
    }

    private fun scrollToActivePack(
        scrollView: NestedScrollView,
        recycler: RecyclerView,
        activePos: Int,
    ) {
        val targetView = recycler.layoutManager?.findViewByPosition(activePos) ?: return
        val offset = (48 * resources.displayMetrics.density).toInt()
        val targetTop = recycler.top + targetView.top - offset
        scrollView.scrollTo(0, targetTop.coerceAtLeast(0))
    }

    private fun onPickPack(pack: ThemePack) {
        if (pack.key == currentPackKey) return

        // First time selecting Custom: seed the store with the CURRENT pack's
        // resolved colours, font and corner radius so the user starts from
        // something familiar rather than a blank slate.
        if (pack.isCustom && !CustomThemeStore.isInitialized(this)) {
            val currentPack = ThemeHelper.getCurrentPack(this)
            CustomThemeStore.initializeFromPack(this, currentPack)
        }

        ThemeHelper.setPack(this, pack)
    }

    /**
     * Resolve a theme attribute to a color int.
     */
    private fun getThemeColor(attr: Int): Int {
        return ThemeColorResolver.resolveColor(this, attr)
    }

    private fun selectedModeFor(pack: ThemePack, themePref: String): Mode {
        if (pack.isCustom) {
            return when (CustomThemeStore.getMode(this)) {
                CustomThemeStore.MODE_DARK -> Mode.DARK
                else -> Mode.LIGHT
            }
        }
        if (pack.isSingleMode) return pack.supportedModes.first()
        return Mode.fromPrefValue(themePref)
    }
}
