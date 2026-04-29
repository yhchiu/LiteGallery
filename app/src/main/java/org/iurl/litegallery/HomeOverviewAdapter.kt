package org.iurl.litegallery

import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.NumberFormat

/**
 * Single-item adapter for the Home screen hero overview (Phase 1).
 *
 * Renders the masthead overview card and the "Folders" section header above the
 * folder grid. Always displays exactly one viewholder, regardless of stat
 * values; an empty state simply shows zeros.
 */
class HomeOverviewAdapter : RecyclerView.Adapter<HomeOverviewAdapter.HeaderViewHolder>() {

    private var stats: OverviewStats = OverviewStats.EMPTY

    fun submitStats(newStats: OverviewStats) {
        if (stats == newStats) return
        stats = newStats
        notifyItemChanged(0)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HeaderViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.header_home_overview, parent, false)
        return HeaderViewHolder(view)
    }

    override fun onBindViewHolder(holder: HeaderViewHolder, position: Int) {
        holder.bind(stats)
    }

    override fun getItemCount(): Int = 1

    override fun getItemViewType(position: Int): Int = HEADER_VIEW_TYPE

    class HeaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val totalView: TextView = itemView.findViewById(R.id.overviewTotal)
        private val photosView: TextView = itemView.findViewById(R.id.statPhotos)
        private val videosView: TextView = itemView.findViewById(R.id.statVideos)
        private val foldersView: TextView = itemView.findViewById(R.id.statFolders)
        private val sizeView: TextView = itemView.findViewById(R.id.statSize)

        fun bind(stats: OverviewStats) {
            val nf = NumberFormat.getNumberInstance()
            totalView.text = nf.format(stats.totalItems)
            photosView.text = nf.format(stats.totalPhotos)
            videosView.text = nf.format(stats.totalVideos)
            foldersView.text = nf.format(stats.totalFolders)
            sizeView.text = Formatter.formatShortFileSize(itemView.context, stats.totalSizeBytes)
        }
    }

    companion object {
        const val HEADER_VIEW_TYPE = 1
    }
}
