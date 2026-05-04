package org.iurl.litegallery

import android.app.AlertDialog
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputFilter
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import org.iurl.litegallery.theme.ContrastUtils
import org.iurl.litegallery.theme.CustomThemeStore
import org.iurl.litegallery.theme.ThemeColorResolver
import org.iurl.litegallery.theme.ThemePack
import org.iurl.litegallery.theme.ThemeVariant

/**
 * Editor activity for configuring the Custom theme pack's colours, font, and
 * corner-radius. Changes are written to [CustomThemeStore] and take effect
 * after the activity finishes and the host activity recreates.
 */
class CustomThemeEditorActivity : AppCompatActivity() {

    private val dp get() = resources.displayMetrics.density

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeHelper.applyTheme(this)
        ThemeHelper.applyPackTheme(this, ThemeVariant.NoActionBar)

        super.onCreate(savedInstanceState)
        ThemeHelper.captureCustomThemeGeneration(this)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        val root = buildUi()
        setContentView(root)
        ThemeHelper.applyRuntimeCustomColors(this)

        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val sys = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(0, sys.top, 0, sys.bottom)
            insets
        }
    }

    override fun onResume() {
        super.onResume()
        if (ThemeHelper.checkAndRecreateForCustomThemeChange(this)) return
    }

    // ── UI construction ───────────────────────────────────────────────

    // ── Preview: BG (main screen) section ─────────────────────────────
    private lateinit var previewBgSection: LinearLayout
    private lateinit var previewBgHeadline: TextView
    private lateinit var previewBgBody: TextView
    private lateinit var previewBgWarning: TextView

    // ── Preview: Card section ─────────────────────────────────────────
    private lateinit var previewCard: MaterialCardView
    private lateinit var previewHeadline: TextView
    private lateinit var previewBody: TextView
    private lateinit var previewAccentStrip: View
    private lateinit var previewCardWarning: TextView

    // ── Preview: Accent button section ────────────────────────────────
    private lateinit var previewButton: TextView
    private lateinit var previewButtonWarning: TextView

    private val colorSwatches = mutableMapOf<String, View>()
    private lateinit var fontValueText: TextView
    private lateinit var cornerValueText: TextView

    private data class RgbSlider(val seekBar: SeekBar, val value: TextView)

    private fun buildUi(): View {
        val coordinator = androidx.coordinatorlayout.widget.CoordinatorLayout(this).apply {
            setBackgroundColor(getThemeAttrColor(android.R.attr.colorBackground))
        }

        // Toolbar
        val toolbar = MaterialToolbar(this).apply {
            setNavigationIcon(R.drawable.ic_arrow_back)
            setNavigationOnClickListener { finish() }
            title = getString(R.string.custom_theme_editor_title)
        }
        val appBar = com.google.android.material.appbar.AppBarLayout(this).apply {
            addView(toolbar)
            setBackgroundColor(Color.TRANSPARENT)
            elevation = 0f
        }
        coordinator.addView(appBar)

        // Scrollable content
        val scrollView = ScrollView(this).apply {
            isFillViewport = true
            layoutParams = androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ).apply {
                behavior = com.google.android.material.appbar.AppBarLayout.ScrollingViewBehavior()
            }
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, (32 * dp).toInt())
        }
        scrollView.addView(content)
        coordinator.addView(scrollView)

        // ── Preview ─────────────────────────────────────────────────
        addSectionHeader(content, R.string.custom_section_preview)
        buildPreviewSections(content)

        // ── Colors ──────────────────────────────────────────────────
        addSectionHeader(content, R.string.custom_section_colors)
        for (key in CustomThemeStore.COLOR_KEYS) {
            addColorRow(content, key)
        }

        // ── Font ────────────────────────────────────────────────────
        addSectionHeader(content, R.string.custom_section_typography)
        fontValueText = addPickerRow(content, R.string.custom_font_label, fontDisplayName(CustomThemeStore.getFont(this))) {
            showFontPicker()
        }

        // ── Corner ──────────────────────────────────────────────────
        addSectionHeader(content, R.string.custom_section_shape)
        cornerValueText = addPickerRow(content, R.string.custom_corner_label, cornerDisplayName(CustomThemeStore.getCorner(this))) {
            showCornerPicker()
        }

        // ── Reset from built-in ─────────────────────────────────────
        val resetFromBtn = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = getString(R.string.custom_reset_from_builtin)
            setOnClickListener { showResetFromBuiltIn() }
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
            lp.topMargin = (24 * dp).toInt()
            lp.marginStart = (16 * dp).toInt()
            lp.marginEnd = (16 * dp).toInt()
            layoutParams = lp
        }
        content.addView(resetFromBtn)

        // ── Reset to defaults ───────────────────────────────────────
        val resetBtn = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = getString(R.string.custom_reset)
            setOnClickListener { confirmReset() }
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
            lp.topMargin = (8 * dp).toInt()
            lp.marginStart = (16 * dp).toInt()
            lp.marginEnd = (16 * dp).toInt()
            layoutParams = lp
        }
        content.addView(resetBtn)

        refreshPreview()
        return coordinator
    }

    // ── Section header ──────────────────────────────────────────────

    private fun addSectionHeader(parent: LinearLayout, labelRes: Int) {
        val tv = TextView(this).apply {
            text = getString(labelRes)
            setTextColor(getThemeAttrColor(com.google.android.material.R.attr.colorPrimary))
            textSize = 12f
            isAllCaps = true
            letterSpacing = 0.1f
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
            lp.topMargin = (22 * dp).toInt()
            lp.marginStart = (20 * dp).toInt()
            lp.marginEnd = (20 * dp).toInt()
            layoutParams = lp
        }
        parent.addView(tv)
    }

    // ── Preview card ────────────────────────────────────────────────

    private fun buildPreviewSections(parent: LinearLayout) {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
            lp.topMargin = (6 * dp).toInt()
            lp.marginStart = (16 * dp).toInt()
            lp.marginEnd = (16 * dp).toInt()
            layoutParams = lp
        }

        // ── BG (main screen) section ─────────────────────────────────
        val bgFrame = FrameLayout(this)
        previewBgSection = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((16 * dp).toInt(), (16 * dp).toInt(), (16 * dp).toInt(), (16 * dp).toInt())
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 12 * dp
            }
        }
        val bgLabel = TextView(this).apply {
            text = getString(R.string.custom_preview_main_screen)
            textSize = 11f
            isAllCaps = true
            letterSpacing = 0.08f
            alpha = 0.6f
        }
        previewBgSection.addView(bgLabel)
        previewBgHeadline = TextView(this).apply {
            text = getString(R.string.custom_preview_headline)
            textSize = 20f
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
            lp.topMargin = (4 * dp).toInt()
            layoutParams = lp
        }
        previewBgSection.addView(previewBgHeadline)
        previewBgBody = TextView(this).apply {
            text = getString(R.string.custom_preview_body)
            textSize = 13f
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
            lp.topMargin = (4 * dp).toInt()
            layoutParams = lp
        }
        previewBgSection.addView(previewBgBody)
        bgFrame.addView(
            previewBgSection,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
            ),
        )
        previewBgWarning = makeWarningBadge()
        bgFrame.addView(
            previewBgWarning,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                gravity = Gravity.TOP or Gravity.END
                marginEnd = (12 * dp).toInt()
                topMargin = (12 * dp).toInt()
            },
        )
        container.addView(bgFrame)

        // ── Card section ─────────────────────────────────────────────
        val cardFrame = FrameLayout(this).apply {
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
            lp.topMargin = (10 * dp).toInt()
            layoutParams = lp
        }
        previewCard = MaterialCardView(this).apply {
            radius = 12 * dp
            cardElevation = 0f
            strokeWidth = (1 * dp).toInt()
        }
        val cardInner = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((16 * dp).toInt(), (16 * dp).toInt(), (16 * dp).toInt(), (16 * dp).toInt())
        }
        previewAccentStrip = View(this).apply {
            val stripLp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, (4 * dp).toInt(),
            )
            stripLp.bottomMargin = (12 * dp).toInt()
            layoutParams = stripLp
        }
        cardInner.addView(previewAccentStrip)
        val cardLabel = TextView(this).apply {
            text = getString(R.string.custom_preview_card_label)
            textSize = 11f
            isAllCaps = true
            letterSpacing = 0.08f
            alpha = 0.6f
        }
        cardInner.addView(cardLabel)
        previewHeadline = TextView(this).apply {
            text = getString(R.string.custom_preview_headline)
            textSize = 20f
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
            lp.topMargin = (4 * dp).toInt()
            layoutParams = lp
        }
        cardInner.addView(previewHeadline)
        previewBody = TextView(this).apply {
            text = getString(R.string.custom_preview_body)
            textSize = 13f
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
            lp.topMargin = (4 * dp).toInt()
            layoutParams = lp
        }
        cardInner.addView(previewBody)
        previewCard.addView(cardInner)
        cardFrame.addView(
            previewCard,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
            ),
        )
        previewCardWarning = makeWarningBadge()
        cardFrame.addView(
            previewCardWarning,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                gravity = Gravity.TOP or Gravity.END
                marginEnd = (12 * dp).toInt()
                topMargin = (12 * dp).toInt()
            },
        )
        container.addView(cardFrame)

        // ── Accent button section ────────────────────────────────────
        val btnFrame = FrameLayout(this).apply {
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
            lp.topMargin = (10 * dp).toInt()
            layoutParams = lp
        }
        previewButton = TextView(this).apply {
            text = getString(R.string.custom_preview_button)
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(0, (12 * dp).toInt(), 0, (12 * dp).toInt())
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 12 * dp
            }
        }
        btnFrame.addView(
            previewButton,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
            ),
        )
        previewButtonWarning = makeWarningBadge()
        btnFrame.addView(
            previewButtonWarning,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                gravity = Gravity.TOP or Gravity.END
                marginEnd = (8 * dp).toInt()
                topMargin = (8 * dp).toInt()
            },
        )
        container.addView(btnFrame)

        parent.addView(container)
    }

    private fun makeWarningBadge(): TextView = TextView(this).apply {
        text = getString(R.string.custom_contrast_warning)
        textSize = 10f
        isAllCaps = true
        letterSpacing = 0.05f
        setPadding((6 * dp).toInt(), (2 * dp).toInt(), (6 * dp).toInt(), (2 * dp).toInt())
        setTextColor(0xFFFFFFFF.toInt())
        background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 4 * dp
            setColor(0xFFD93025.toInt())
        }
        visibility = View.GONE
    }

    private fun refreshPreview() {
        val bg = CustomThemeStore.getColor(this, CustomThemeStore.KEY_BG)
        val card = CustomThemeStore.getColor(this, CustomThemeStore.KEY_CARD)
        val text = CustomThemeStore.getColor(this, CustomThemeStore.KEY_TEXT)
        val dim = CustomThemeStore.getColor(this, CustomThemeStore.KEY_DIM)
        val accent = CustomThemeStore.getColor(this, CustomThemeStore.KEY_ACCENT)
        val onAccent = CustomThemeStore.getColor(this, CustomThemeStore.KEY_ON_ACCENT)
        val line = CustomThemeStore.getColor(this, CustomThemeStore.KEY_LINE)

        // BG (main screen) section
        (previewBgSection.background as? GradientDrawable)?.apply {
            setColor(bg)
            setStroke((1 * dp).toInt(), line)
        }
        previewBgHeadline.setTextColor(text)
        previewBgBody.setTextColor(dim)
        previewBgWarning.visibility = warnVisibility(text, bg)

        // Card section
        previewCard.setCardBackgroundColor(card)
        previewCard.strokeColor = line
        previewHeadline.setTextColor(text)
        previewBody.setTextColor(dim)
        previewAccentStrip.setBackgroundColor(accent)
        previewCardWarning.visibility = warnVisibility(text, card)

        // Accent button section
        (previewButton.background as? GradientDrawable)?.setColor(accent)
        previewButton.setTextColor(onAccent)
        previewButtonWarning.visibility = warnVisibility(onAccent, accent)

        // update swatch colors
        for ((key, swatch) in colorSwatches) {
            val c = CustomThemeStore.getColor(this, key)
            (swatch.background as? GradientDrawable)?.setColor(c)
        }
    }

    private fun warnVisibility(fg: Int, bg: Int): Int =
        if (ContrastUtils.grade(ContrastUtils.ratio(fg, bg)) == ContrastUtils.Grade.FAIL) View.VISIBLE else View.GONE

    // ── Color row ───────────────────────────────────────────────────

    private fun addColorRow(parent: LinearLayout, key: String) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((20 * dp).toInt(), (10 * dp).toInt(), (20 * dp).toInt(), (10 * dp).toInt())
            isClickable = true
            isFocusable = true
            setBackgroundResource(android.R.drawable.list_selector_background)
            setOnClickListener { showColorDialog(key) }
        }

        val label = TextView(this).apply {
            text = colorKeyLabel(key)
            textSize = 15f
            setTextColor(getThemeAttrColor(android.R.attr.textColorPrimary))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        row.addView(label)

        val swatchSize = (32 * dp).toInt()
        val swatch = FrameLayout(this).apply {
            val shape = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 6 * dp
                setColor(CustomThemeStore.getColor(this@CustomThemeEditorActivity, key))
                setStroke((1 * dp).toInt(), getThemeAttrColor(com.google.android.material.R.attr.colorOutline))
            }
            background = shape
            layoutParams = LinearLayout.LayoutParams(swatchSize, swatchSize)
        }
        colorSwatches[key] = swatch
        row.addView(swatch)

        parent.addView(row)
    }

    private fun showColorDialog(key: String) {
        val currentColor = CustomThemeStore.toOpaqueRgb(CustomThemeStore.getColor(this, key))
        val hexStr = formatHexRgb(currentColor)
        var syncingInput = false
        var syncingSliders = false

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((24 * dp).toInt(), (16 * dp).toInt(), (24 * dp).toInt(), 0)
        }

        // Preview swatch
        val previewSize = (48 * dp).toInt()
        val preview = FrameLayout(this).apply {
            val shape = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 8 * dp
                setColor(currentColor)
                setStroke((1 * dp).toInt(), Color.GRAY)
            }
            background = shape
            layoutParams = LinearLayout.LayoutParams(previewSize, previewSize).apply {
                gravity = Gravity.CENTER_HORIZONTAL
            }
        }
        container.addView(preview)

        val input = EditText(this).apply {
            setText(hexStr)
            hint = getString(R.string.custom_color_dialog_hint)
            filters = arrayOf(InputFilter.LengthFilter(7))
            setSelection(text.length)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
            lp.topMargin = (12 * dp).toInt()
            layoutParams = lp
        }
        container.addView(input)

        val redSlider = addRgbSlider(container, "R", Color.red(currentColor))
        val greenSlider = addRgbSlider(container, "G", Color.green(currentColor))
        val blueSlider = addRgbSlider(container, "B", Color.blue(currentColor))

        // Contrast rows for each opponent (if any)
        val opponents = ContrastUtils.opponentsFor(key)
        val ratioTextViews = mutableListOf<TextView>()
        val gradeBadges = mutableListOf<TextView>()
        if (opponents.isNotEmpty()) {
            val divider = View(this).apply {
                setBackgroundColor(getThemeAttrColor(com.google.android.material.R.attr.colorOutlineVariant))
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, (1 * dp).toInt(),
                )
                lp.topMargin = (16 * dp).toInt()
                lp.bottomMargin = (8 * dp).toInt()
                layoutParams = lp
            }
            container.addView(divider)

            for (opp in opponents) {
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    val lp = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                    )
                    lp.topMargin = (4 * dp).toInt()
                    lp.bottomMargin = (4 * dp).toInt()
                    layoutParams = lp
                }
                val label = TextView(this).apply {
                    text = colorKeyLabel(opp.key)
                    textSize = 13f
                    setTextColor(getThemeAttrColor(android.R.attr.textColorSecondary))
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }
                row.addView(label)
                val ratioTv = TextView(this).apply {
                    textSize = 13f
                    setTextColor(getThemeAttrColor(android.R.attr.textColorPrimary))
                    val lp = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                    )
                    lp.marginEnd = (10 * dp).toInt()
                    layoutParams = lp
                }
                row.addView(ratioTv)
                ratioTextViews.add(ratioTv)
                val gradeTv = TextView(this).apply {
                    textSize = 11f
                    isAllCaps = true
                    letterSpacing = 0.05f
                    setPadding((6 * dp).toInt(), (2 * dp).toInt(), (6 * dp).toInt(), (2 * dp).toInt())
                    setTextColor(0xFFFFFFFF.toInt())
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        cornerRadius = 4 * dp
                    }
                }
                row.addView(gradeTv)
                gradeBadges.add(gradeTv)
                container.addView(row)
            }
        }

        fun applyContrast(c: Int) {
            val color = CustomThemeStore.toOpaqueRgb(c)
            for ((i, opp) in opponents.withIndex()) {
                val opponentColor = CustomThemeStore.getColor(this, opp.key)
                val fg = if (opp.opponentIsForeground) opponentColor else color
                val bg = if (opp.opponentIsForeground) color else opponentColor
                val r = ContrastUtils.ratio(fg, bg)
                val grade = ContrastUtils.grade(r)
                ratioTextViews[i].text = String.format("%.1f:1", r)
                paintGradeBadge(gradeBadges[i], grade)
            }
        }

        fun updateSliders(color: Int) {
            syncingSliders = true
            redSlider.seekBar.progress = Color.red(color)
            redSlider.value.text = Color.red(color).toString()
            greenSlider.seekBar.progress = Color.green(color)
            greenSlider.value.text = Color.green(color).toString()
            blueSlider.seekBar.progress = Color.blue(color)
            blueSlider.value.text = Color.blue(color).toString()
            syncingSliders = false
        }

        fun updateColor(color: Int, updateInput: Boolean) {
            val selectedColor = CustomThemeStore.toOpaqueRgb(color)
            (preview.background as? GradientDrawable)?.setColor(selectedColor)
            applyContrast(selectedColor)
            updateSliders(selectedColor)
            if (updateInput) {
                syncingInput = true
                input.setText(formatHexRgb(selectedColor))
                input.setSelection(input.text.length)
                syncingInput = false
            }
        }

        val sliderListener = object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                when (seekBar) {
                    redSlider.seekBar -> redSlider.value.text = progress.toString()
                    greenSlider.seekBar -> greenSlider.value.text = progress.toString()
                    blueSlider.seekBar -> blueSlider.value.text = progress.toString()
                }
                if (!fromUser || syncingSliders) return
                updateColor(
                    Color.rgb(
                        redSlider.seekBar.progress,
                        greenSlider.seekBar.progress,
                        blueSlider.seekBar.progress,
                    ),
                    updateInput = true,
                )
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        }
        redSlider.seekBar.setOnSeekBarChangeListener(sliderListener)
        greenSlider.seekBar.setOnSeekBarChangeListener(sliderListener)
        blueSlider.seekBar.setOnSeekBarChangeListener(sliderListener)

        input.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                if (syncingInput) return
                val hex = s?.toString()?.trim() ?: return
                parseHexRgb(hex)?.let { updateColor(it, updateInput = false) }
            }
        })
        updateColor(currentColor, updateInput = false)

        AlertDialog.Builder(this)
            .setTitle("${colorKeyLabel(key)} — ${getString(R.string.custom_color_dialog_title)}")
            .setView(container)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                parseHexRgb(input.text.toString().trim())?.let { color ->
                    CustomThemeStore.setColor(this, key, color)
                    recreate()
                } ?: run {
                    Toast.makeText(this, R.string.custom_color_invalid, Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .showThemed()
    }

    private fun paintGradeBadge(tv: TextView, grade: ContrastUtils.Grade) {
        val (bgColor, label) = when (grade) {
            ContrastUtils.Grade.AAA -> 0xFF2E7D32.toInt() to getString(R.string.custom_contrast_aaa)
            ContrastUtils.Grade.AA  -> 0xFF388E3C.toInt() to getString(R.string.custom_contrast_aa)
            ContrastUtils.Grade.FAIL -> 0xFFD93025.toInt() to getString(R.string.custom_contrast_low)
        }
        tv.text = label
        (tv.background as? GradientDrawable)?.setColor(bgColor)
    }

    // ── Picker row (font / corner / mode) ───────────────────────────

    private fun addPickerRow(parent: LinearLayout, labelRes: Int, currentValue: String, onClick: () -> Unit): TextView {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((20 * dp).toInt(), (14 * dp).toInt(), (20 * dp).toInt(), (14 * dp).toInt())
            isClickable = true
            isFocusable = true
            setBackgroundResource(android.R.drawable.list_selector_background)
            setOnClickListener { onClick() }
        }

        val label = TextView(this).apply {
            text = getString(labelRes)
            textSize = 15f
            setTextColor(getThemeAttrColor(android.R.attr.textColorPrimary))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        row.addView(label)

        val value = TextView(this).apply {
            text = currentValue
            textSize = 14f
            setTextColor(getThemeAttrColor(android.R.attr.textColorSecondary))
        }
        row.addView(value)
        parent.addView(row)
        return value
    }

    // ── Font picker ─────────────────────────────────────────────────

    private fun showFontPicker() {
        val keys = listOf(
            CustomThemeStore.FONT_SANS_SERIF,
            CustomThemeStore.FONT_FRAUNCES,
            CustomThemeStore.FONT_CORMORANT,
            CustomThemeStore.FONT_JETBRAINS_MONO,
            CustomThemeStore.FONT_ARCHIVO_BLACK,
        )
        val labels = keys.map { fontDisplayName(it) }.toTypedArray()
        val current = keys.indexOf(CustomThemeStore.getFont(this)).coerceAtLeast(0)

        AlertDialog.Builder(this)
            .setTitle(R.string.custom_font_label)
            .setSingleChoiceItems(labels, current) { dialog, which ->
                CustomThemeStore.setFont(this, keys[which])
                dialog.dismiss()
                recreate()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .showThemed()
    }

    // ── Corner picker ───────────────────────────────────────────────

    private fun showCornerPicker() {
        val keys = listOf(
            CustomThemeStore.CORNER_NONE,
            CustomThemeStore.CORNER_SMALL,
            CustomThemeStore.CORNER_MEDIUM,
            CustomThemeStore.CORNER_LARGE,
        )
        val labels = keys.map { cornerDisplayName(it) }.toTypedArray()
        val current = keys.indexOf(CustomThemeStore.getCorner(this)).coerceAtLeast(0)

        AlertDialog.Builder(this)
            .setTitle(R.string.custom_corner_label)
            .setSingleChoiceItems(labels, current) { dialog, which ->
                CustomThemeStore.setCorner(this, keys[which])
                dialog.dismiss()
                recreate()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .showThemed()
    }

    // ── Reset from built-in theme ───────────────────────────────────

    private fun showResetFromBuiltIn() {
        val builtInPacks = ThemePack.builtIn()
        val packLabels = builtInPacks.map { getString(it.nameRes) }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle(R.string.custom_reset_from_builtin)
            .setItems(packLabels) { _, which ->
                val selectedPack = builtInPacks[which]
                showModeForReset(selectedPack)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .showThemed()
    }

    private fun showModeForReset(pack: ThemePack) {
        // Build mode options based on what the pack supports
        val modeKeys = mutableListOf<String>()
        val modeLabels = mutableListOf<String>()

        if (pack.supportedModes.contains(org.iurl.litegallery.theme.Mode.LIGHT) || pack.isDualMode) {
            modeKeys.add(CustomThemeStore.MODE_LIGHT)
            modeLabels.add(getString(R.string.custom_mode_light))
        }
        if (pack.supportedModes.contains(org.iurl.litegallery.theme.Mode.DARK) || pack.isDualMode) {
            modeKeys.add(CustomThemeStore.MODE_DARK)
            modeLabels.add(getString(R.string.custom_mode_dark))
        }

        if (modeKeys.size == 1) {
            // Single mode pack — skip the dialog and reset directly
            confirmResetFromPack(pack, modeKeys[0])
            return
        }

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.custom_reset_from_mode_title, getString(pack.nameRes)))
            .setItems(modeLabels.toTypedArray()) { _, which ->
                confirmResetFromPack(pack, modeKeys[which])
            }
            .setNegativeButton(android.R.string.cancel, null)
            .showThemed()
    }

    private fun confirmResetFromPack(pack: ThemePack, mode: String) {
        val packName = getString(pack.nameRes)
        val modeName = modeDisplayName(mode)

        AlertDialog.Builder(this)
            .setMessage(getString(R.string.custom_reset_from_confirm, packName, modeName))
            .setPositiveButton(android.R.string.ok) { _, _ ->
                CustomThemeStore.resetFromPack(this, pack, mode)
                recreate()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .showThemed()
    }

    // ── Reset ────────────────────────────────────────────────────────

    private fun confirmReset() {
        AlertDialog.Builder(this)
            .setMessage(R.string.custom_reset_confirm)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                CustomThemeStore.resetToDefaults(this)
                recreate()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .showThemed()
    }

    // ── Helpers ──────────────────────────────────────────────────────

    private fun addRgbSlider(parent: LinearLayout, label: String, initial: Int): RgbSlider {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
            lp.topMargin = (8 * dp).toInt()
            layoutParams = lp
        }
        val labelView = TextView(this).apply {
            text = label
            textSize = 13f
            setTextColor(getThemeAttrColor(android.R.attr.textColorSecondary))
            layoutParams = LinearLayout.LayoutParams((24 * dp).toInt(), LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        row.addView(labelView)

        val seekBar = SeekBar(this).apply {
            max = 255
            progress = initial.coerceIn(0, 255)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        row.addView(seekBar)

        val value = TextView(this).apply {
            text = seekBar.progress.toString()
            textSize = 13f
            gravity = Gravity.END
            setTextColor(getThemeAttrColor(android.R.attr.textColorSecondary))
            layoutParams = LinearLayout.LayoutParams((42 * dp).toInt(), LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        row.addView(value)
        parent.addView(row)
        return RgbSlider(seekBar, value)
    }

    private fun formatHexRgb(color: Int): String =
        String.format("#%06X", color and 0xFFFFFF)

    private fun parseHexRgb(value: String): Int? {
        val raw = value.trim()
        val hex = if (raw.startsWith("#")) raw.drop(1) else raw
        if (!hex.matches(Regex("[0-9a-fA-F]{6}"))) return null
        return Color.rgb(
            hex.substring(0, 2).toInt(16),
            hex.substring(2, 4).toInt(16),
            hex.substring(4, 6).toInt(16),
        )
    }

    private fun AlertDialog.Builder.showThemed(): AlertDialog {
        val dialog = create()
        dialog.show()
        ThemeHelper.applyRuntimeCustomColors(dialog)
        return dialog
    }

    private fun colorKeyLabel(key: String): String = when (key) {
        CustomThemeStore.KEY_BG -> getString(R.string.custom_color_bg)
        CustomThemeStore.KEY_SURFACE -> getString(R.string.custom_color_surface)
        CustomThemeStore.KEY_CARD -> getString(R.string.custom_color_card)
        CustomThemeStore.KEY_TEXT -> getString(R.string.custom_color_text)
        CustomThemeStore.KEY_DIM -> getString(R.string.custom_color_dim)
        CustomThemeStore.KEY_ACCENT -> getString(R.string.custom_color_accent)
        CustomThemeStore.KEY_ON_ACCENT -> getString(R.string.custom_color_on_accent)
        else -> key
    }

    private fun fontDisplayName(key: String): String = when (key) {
        CustomThemeStore.FONT_SANS_SERIF -> getString(R.string.custom_font_sans_serif)
        CustomThemeStore.FONT_FRAUNCES -> getString(R.string.custom_font_fraunces)
        CustomThemeStore.FONT_CORMORANT -> getString(R.string.custom_font_cormorant)
        CustomThemeStore.FONT_JETBRAINS_MONO -> getString(R.string.custom_font_jetbrains)
        CustomThemeStore.FONT_ARCHIVO_BLACK -> getString(R.string.custom_font_archivo)
        else -> key
    }

    private fun cornerDisplayName(key: String): String = when (key) {
        CustomThemeStore.CORNER_NONE -> getString(R.string.custom_corner_none)
        CustomThemeStore.CORNER_SMALL -> getString(R.string.custom_corner_small)
        CustomThemeStore.CORNER_MEDIUM -> getString(R.string.custom_corner_medium)
        CustomThemeStore.CORNER_LARGE -> getString(R.string.custom_corner_large)
        else -> key
    }

    private fun modeDisplayName(key: String): String = when (key) {
        CustomThemeStore.MODE_DARK -> getString(R.string.custom_mode_dark)
        else -> getString(R.string.custom_mode_light)
    }

    private fun getThemeAttrColor(attr: Int): Int {
        return ThemeColorResolver.resolveColor(this, attr)
    }
}
