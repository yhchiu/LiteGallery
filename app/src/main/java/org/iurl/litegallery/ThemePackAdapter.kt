package org.iurl.litegallery

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import org.iurl.litegallery.theme.ThemePack

/**
 * RecyclerView adapter for the Theme Pack picker. Renders each pack as a
 * MaterialCardView with a 3-stripe swatch preview (bg / text / accent), the
 * pack name and subtitle, a capability badge ("DARK ONLY" / "BOTH"), and an
 * active-state checkmark.
 */
class ThemePackAdapter(
    private val packs: List<ThemePack>,
    private val activeKey: String,
    private val onPickPack: (ThemePack) -> Unit,
) : RecyclerView.Adapter<ThemePackAdapter.VH>() {

    class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
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
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_theme_pack, parent, false)
        return VH(view)
    }

    override fun getItemCount(): Int = packs.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val pack = packs[position]
        val ctx = holder.itemView.context
        val isActive = pack.key == activeKey

        // Swatches: tint the existing rectangle backgrounds with each pack's color tokens
        val bg = ContextCompat.getColor(ctx, pack.swatchBg)
        val text = ContextCompat.getColor(ctx, pack.swatchText)
        val accent = ContextCompat.getColor(ctx, pack.swatchAccent)
        holder.swatchBg.setBackgroundColor(bg)
        holder.swatchText.setBackgroundColor(text)
        holder.swatchAccent.setBackgroundColor(accent)

        // Name + subtitle
        holder.name.text = ctx.getString(pack.nameRes)
        holder.subtitle.text = ctx.getString(pack.subtitleRes)

        // Capability badge
        holder.badge.text = when {
            pack.isDarkOnly -> ctx.getString(R.string.pack_badge_dark_only)
            pack.isLightOnly -> ctx.getString(R.string.pack_badge_light_only)
            else -> ctx.getString(R.string.pack_badge_dual)
        }

        // Active-state visuals
        if (isActive) {
            val accentColor = resolveThemeColor(ctx, com.google.android.material.R.attr.colorPrimary)
            holder.card.strokeColor = accentColor
            holder.card.strokeWidth = (ctx.resources.displayMetrics.density * 2f).toInt()
            holder.activeChip.visibility = View.VISIBLE
            holder.checkEmpty.visibility = View.GONE
            holder.checkFilled.visibility = View.VISIBLE
        } else {
            val outlineColor = resolveThemeColor(ctx, com.google.android.material.R.attr.colorOutlineVariant)
            holder.card.strokeColor = outlineColor
            holder.card.strokeWidth = (ctx.resources.displayMetrics.density * 1f).toInt()
            holder.activeChip.visibility = View.GONE
            holder.checkEmpty.visibility = View.VISIBLE
            holder.checkFilled.visibility = View.GONE
        }

        holder.card.setOnClickListener { onPickPack(pack) }
    }

    private fun resolveThemeColor(ctx: android.content.Context, attr: Int): Int {
        val typedValue = android.util.TypedValue()
        ctx.theme.resolveAttribute(attr, typedValue, true)
        return if (typedValue.resourceId != 0) {
            ContextCompat.getColor(ctx, typedValue.resourceId)
        } else {
            typedValue.data
        }
    }
}
