package org.iurl.litegallery

import android.graphics.Color
import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import java.text.NumberFormat
import org.iurl.litegallery.theme.GradientHelper
import org.iurl.litegallery.theme.ThemeColorResolver

/**
 * Single-item adapter for the Home screen hero overview (Phase 1).
 *
 * Renders the masthead overview card and the "Folders" section header above the
 * folder grid. Always displays exactly one viewholder, regardless of stat
 * values; an empty state simply shows zeros.
 */
class HomeOverviewAdapter(
    private val onSortClick: () -> Unit
) : RecyclerView.Adapter<HomeOverviewAdapter.HeaderViewHolder>() {

    private var stats: OverviewStats = OverviewStats.EMPTY
    private var sortLabel: String? = null
    private var sortContentDescription: String? = null

    fun submitStats(newStats: OverviewStats) {
        if (stats == newStats) return
        stats = newStats
        notifyItemChanged(0)
    }

    fun submitSortIndicator(label: String, contentDescription: String) {
        if (sortLabel == label && sortContentDescription == contentDescription) return
        sortLabel = label
        sortContentDescription = contentDescription
        notifyItemChanged(0)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HeaderViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.header_home_overview, parent, false)
        return HeaderViewHolder(view)
    }

    override fun onBindViewHolder(holder: HeaderViewHolder, position: Int) {
        holder.bind(stats, sortLabel, sortContentDescription, onSortClick)
    }

    override fun getItemCount(): Int = 1

    override fun getItemViewType(position: Int): Int = HEADER_VIEW_TYPE

    class HeaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val heroCard: MaterialCardView = itemView.findViewById(R.id.heroOverviewCard)
        private val heroInnerLayout: ViewGroup = itemView.findViewById(R.id.heroInnerLayout)
        private val divider: View = itemView.findViewById(R.id.overviewDivider)
        private val totalView: TextView = itemView.findViewById(R.id.overviewTotal)
        private val photosView: TextView = itemView.findViewById(R.id.statPhotos)
        private val videosView: TextView = itemView.findViewById(R.id.statVideos)
        private val foldersView: TextView = itemView.findViewById(R.id.statFolders)
        private val sizeView: TextView = itemView.findViewById(R.id.statSize)
        private val sortLabelView: TextView = itemView.findViewById(R.id.sortLabel)
        private val numberFormat: NumberFormat = NumberFormat.getNumberInstance()

        init {
            applyGradientIfAvailable()
        }

        fun bind(
            stats: OverviewStats,
            sortLabel: String?,
            sortContentDescription: String?,
            onSortClick: () -> Unit
        ) {
            totalView.text = numberFormat.format(stats.totalItems)
            photosView.text = numberFormat.format(stats.totalPhotos)
            videosView.text = numberFormat.format(stats.totalVideos)
            foldersView.text = numberFormat.format(stats.totalFolders)
            sizeView.text = Formatter.formatShortFileSize(itemView.context, stats.totalSizeBytes)
            val label = sortLabel ?: itemView.context.getString(R.string.folder_sort_chip_date_desc)
            sortLabelView.text = label
            sortLabelView.contentDescription = sortContentDescription
                ?: itemView.context.getString(R.string.folder_sort_content_description, label)
            sortLabelView.setOnClickListener { onSortClick() }
        }

        private fun applyGradientIfAvailable() {
            val context = itemView.context
            val gradient = GradientHelper.createForCurrentPack(context, heroCard.radius) ?: return
            val onGradient = ThemeColorResolver.resolveColor(
                context,
                com.google.android.material.R.attr.colorOnPrimary,
                Color.WHITE,
            )

            heroInnerLayout.background = gradient
            heroInnerLayout.setTag(R.id.tag_custom_theme_skip_subtree, true)
            heroCard.strokeWidth = 0
            heroCard.setCardBackgroundColor(Color.TRANSPARENT)
            divider.setBackgroundColor(onGradient.withAlpha(0x33))
            applyTextColor(heroInnerLayout, onGradient)
        }

        private fun applyTextColor(view: View, color: Int) {
            if (view is TextView) {
                view.setTextColor(color)
            }
            if (view is ViewGroup) {
                for (i in 0 until view.childCount) {
                    applyTextColor(view.getChildAt(i), color)
                }
            }
        }

        private fun Int.withAlpha(alpha: Int): Int =
            (this and 0x00FFFFFF) or (alpha.coerceIn(0, 255) shl 24)
    }

    companion object {
        const val HEADER_VIEW_TYPE = 1
    }
}
