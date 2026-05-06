package org.iurl.litegallery

import android.content.Intent
import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import org.iurl.litegallery.theme.CustomThemeApplier
import org.iurl.litegallery.theme.CustomThemePalette
import org.iurl.litegallery.theme.CustomThemeStore
import org.iurl.litegallery.theme.GradientHelper
import org.iurl.litegallery.theme.ThemePack
import org.iurl.litegallery.theme.ThemeColorResolver

/**
 * RecyclerView adapter for the Theme Pack picker. Renders each pack as a
 * MaterialCardView with a 3-stripe swatch preview (bg / text / accent), the
 * pack name and subtitle, a capability badge ("DARK ONLY" / "BOTH"), and an
 * active-state checkmark.
 *
 * When the Custom pack is selected, a "Customize Theme…" button row is shown
 * directly below the Custom card.
 */
class ThemePackAdapter(
    private val packs: List<ThemePack>,
    private val activeKey: String,
    private val onPickPack: (ThemePack) -> Unit,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_PACK = 0
        private const val TYPE_CUSTOMIZE_BUTTON = 1
    }

    /** Flat item list: packs + optional customize-button after Custom card. */
    private data class Item(val type: Int, val pack: ThemePack? = null)

    private val items: List<Item> = buildList {
        for (pack in packs) {
            add(Item(TYPE_PACK, pack))
            // Insert the button row right after the Custom card when it's active
            if (pack.isCustom && pack.key == activeKey) {
                add(Item(TYPE_CUSTOMIZE_BUTTON))
            }
        }
    }

    /** Position of the active pack inside [items]. */
    fun activePosition(): Int = items.indexOfFirst { it.type == TYPE_PACK && it.pack?.key == activeKey }

    // ── ViewHolders ─────────────────────────────────────────────────

    class PackVH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val card: MaterialCardView = itemView.findViewById(R.id.card_root)
        val swatchBg: View = itemView.findViewById(R.id.swatch_bg)
        val swatchText: View = itemView.findViewById(R.id.swatch_text)
        val swatchAccent: View = itemView.findViewById(R.id.swatch_accent)
        val name: TextView = itemView.findViewById(R.id.pack_name)
        val subtitle: TextView = itemView.findViewById(R.id.pack_subtitle)
        val badge: TextView = itemView.findViewById(R.id.pack_badge)
        val activeChip: TextView = itemView.findViewById(R.id.active_chip)
        val checkEmpty: View = itemView.findViewById(R.id.check_empty)
        val checkFilled: ImageView = itemView.findViewById(R.id.check_filled)

        init {
            swatchBg.setTag(R.id.tag_custom_theme_skip_self, true)
            swatchText.setTag(R.id.tag_custom_theme_skip_self, true)
            swatchAccent.setTag(R.id.tag_custom_theme_skip_self, true)
        }
    }

    class ButtonVH(itemView: View) : RecyclerView.ViewHolder(itemView)

    // ── Adapter overrides ───────────────────────────────────────────

    override fun getItemViewType(position: Int): Int = items[position].type

    override fun getItemCount(): Int = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            TYPE_CUSTOMIZE_BUTTON -> {
                val ctx = parent.context
                val dp = ctx.resources.displayMetrics.density
                val button = MaterialButton(ctx).apply {
                    text = ctx.getString(R.string.custom_theme_customize)
                    setOnClickListener {
                        ctx.startActivity(Intent(ctx, CustomThemeEditorActivity::class.java))
                    }
                    if (activeKey == ThemePack.CUSTOM.key) {
                        CustomThemeApplier.applyPrimaryAction(this)
                    }
                    val lp = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                    )
                    lp.topMargin = (4 * dp).toInt()
                    lp.bottomMargin = (4 * dp).toInt()
                    lp.marginStart = (16 * dp).toInt()
                    lp.marginEnd = (16 * dp).toInt()
                    layoutParams = lp
                }
                val wrapper = LinearLayout(ctx).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(button)
                }
                ButtonVH(wrapper)
            }
            else -> {
                val view = LayoutInflater.from(parent.context).inflate(R.layout.item_theme_pack, parent, false)
                PackVH(view)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = items[position]
        if (item.type == TYPE_PACK && holder is PackVH) {
            bindPack(holder, item.pack!!)
        }
        // ButtonVH needs no binding — it's self-contained
    }

    // ── Pack binding ────────────────────────────────────────────────

    private fun bindPack(holder: PackVH, pack: ThemePack) {
        val ctx = holder.itemView.context
        val isActive = pack.key == activeKey

        // Swatches: tint the existing rectangle backgrounds with each pack's color tokens
        // For Custom pack, read from CustomThemeStore to show actual user colours.
        val palette = if (pack.isCustom && CustomThemeStore.isInitialized(ctx)) {
            CustomThemePalette.fromStore(ctx)
        } else {
            null
        }
        val bg = palette?.background ?: ContextCompat.getColor(ctx, pack.swatchBg)
        val text = palette?.text ?: ContextCompat.getColor(ctx, pack.swatchText)
        val accent = palette?.accent ?: ContextCompat.getColor(ctx, pack.swatchAccent)
        holder.swatchBg.setBackgroundColor(bg)
        holder.swatchText.setBackgroundColor(text)
        val swatchRadius = 2f * ctx.resources.displayMetrics.density
        val gradient = when {
            pack.isCustom && palette?.hasGradient == true -> GradientHelper.create(
                start = palette.gradientStart!!,
                end = palette.gradientEnd!!,
                angle = palette.gradientAngle!!,
                cornerRadius = swatchRadius,
            )
            !pack.isCustom -> GradientHelper.createForPack(ctx, pack, swatchRadius)
            else -> null
        }
        if (gradient != null) {
            holder.swatchAccent.background = gradient
        } else {
            holder.swatchAccent.setBackgroundColor(accent)
        }

        // Name + subtitle
        holder.name.text = ctx.getString(pack.nameRes)
        holder.subtitle.text = ctx.getString(pack.subtitleRes)

        // Capability badge
        if (pack.isCustom) {
            val mode = CustomThemeStore.getMode(ctx)
            holder.badge.text = if (mode == CustomThemeStore.MODE_DARK)
                ctx.getString(R.string.pack_badge_dark_only)
            else
                ctx.getString(R.string.pack_badge_light_only)
        } else {
            holder.badge.text = when {
                pack.isDarkOnly -> ctx.getString(R.string.pack_badge_dark_only)
                pack.isLightOnly -> ctx.getString(R.string.pack_badge_light_only)
                else -> ctx.getString(R.string.pack_badge_dual)
            }
        }

        // Active-state visuals
        if (isActive) {
            val activePalette = if (pack.isCustom) CustomThemePalette.fromStore(ctx) else null
            val accentColor = activePalette?.accent
                ?: ThemeColorResolver.resolveColor(ctx, com.google.android.material.R.attr.colorPrimary)
            val onAccentColor = activePalette?.onAccent
                ?: ThemeColorResolver.resolveColor(ctx, com.google.android.material.R.attr.colorOnPrimary)
            holder.card.strokeColor = accentColor
            holder.card.strokeWidth = (ctx.resources.displayMetrics.density * 2f).toInt()
            holder.activeChip.setTextColor(accentColor)
            holder.activeChip.visibility = View.VISIBLE
            holder.checkEmpty.visibility = View.GONE
            holder.checkFilled.visibility = View.VISIBLE
            holder.checkFilled.backgroundTintList = ColorStateList.valueOf(accentColor)
            holder.checkFilled.imageTintList = ColorStateList.valueOf(onAccentColor)
        } else {
            val outlineColor = ThemeColorResolver.resolveColor(
                ctx,
                com.google.android.material.R.attr.colorOutlineVariant,
            )
            holder.card.strokeColor = outlineColor
            holder.card.strokeWidth = (ctx.resources.displayMetrics.density * 1f).toInt()
            holder.activeChip.visibility = View.GONE
            holder.checkEmpty.visibility = View.VISIBLE
            holder.checkFilled.visibility = View.GONE
        }

        holder.card.setOnClickListener { onPickPack(pack) }
    }
}
