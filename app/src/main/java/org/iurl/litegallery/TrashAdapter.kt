package org.iurl.litegallery

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import org.iurl.litegallery.databinding.ItemTrashBinding

/**
 * Card-grid adapter for the Trash Bin (Phase 4).
 *
 * Each tile shows a desaturated cover, a source badge ("APP" / "SYS") and an
 * optional "Nd left" retention badge. Tapping the card delegates to
 * [onItemClick] (which may toggle selection or open the actions dialog),
 * long-pressing delegates to [onItemLongClick] (typically enters selection
 * mode), and the per-item Restore / Delete buttons fire their callbacks
 * directly without going through the card click handler.
 */
class TrashAdapter(
    private val onItemClick: (MediaItem) -> Unit,
    private val onItemLongClick: (MediaItem) -> Unit,
    private val onRestoreClick: (MediaItem) -> Unit,
    private val onPermanentDeleteClick: (MediaItem) -> Unit,
    private val isItemSelected: (MediaItem) -> Boolean,
    private val isInSelectionMode: () -> Boolean,
    private val getSourceBadgeLabel: (MediaItem) -> String?,
    private val getSourceBadgeContentDescription: (MediaItem) -> String?,
    private val getRemainDaysLabel: (MediaItem) -> String?,
    private val getFromLabel: (MediaItem) -> String?
) : ListAdapter<MediaItem, TrashAdapter.TrashViewHolder>(TrashDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TrashViewHolder {
        val binding = ItemTrashBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return TrashViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TrashViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    /**
     * Re-bind only the rows whose paths are in [changedPaths] so a selection
     * toggle does not invalidate the entire list.
     */
    fun notifySelectionChanged(changedPaths: Set<String>) {
        if (changedPaths.isEmpty()) return
        for (i in 0 until itemCount) {
            if (getItem(i).path in changedPaths) {
                notifyItemChanged(i)
            }
        }
    }

    inner class TrashViewHolder(private val binding: ItemTrashBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: MediaItem) {
            val context = binding.root.context

            // Cover — desaturated to convey "deleted" state.
            Glide.with(context)
                .load(item.path)
                .centerCrop()
                .placeholder(R.drawable.ic_folder)
                .error(R.drawable.ic_folder)
                .into(binding.thumbnailImageView)

            val matrix = ColorMatrix().apply { setSaturation(0.35f) }
            binding.thumbnailImageView.colorFilter = ColorMatrixColorFilter(matrix)

            // Source badge (APP / SYS)
            val sourceLabel = getSourceBadgeLabel(item)
            if (sourceLabel.isNullOrBlank()) {
                binding.sourceBadgeTextView.visibility = View.GONE
                binding.sourceBadgeTextView.contentDescription = null
            } else {
                binding.sourceBadgeTextView.visibility = View.VISIBLE
                binding.sourceBadgeTextView.text = sourceLabel
                binding.sourceBadgeTextView.contentDescription = getSourceBadgeContentDescription(item)
            }

            // "Nd left" retention badge — only for app-trash with active retention.
            val remainLabel = getRemainDaysLabel(item)
            if (remainLabel.isNullOrBlank()) {
                binding.remainBadgeTextView.visibility = View.GONE
            } else {
                binding.remainBadgeTextView.visibility = View.VISIBLE
                binding.remainBadgeTextView.text = remainLabel
            }

            // "from {folder}" hint
            val fromLabel = getFromLabel(item)
            if (fromLabel.isNullOrBlank()) {
                binding.fromLabelTextView.visibility = View.GONE
            } else {
                binding.fromLabelTextView.visibility = View.VISIBLE
                binding.fromLabelTextView.text = fromLabel
            }

            // Selection visual: thicker accent stroke when selected.
            val selected = isItemSelected(item)
            val density = context.resources.displayMetrics.density
            binding.root.strokeWidth = ((if (selected) 2f else 1f) * density).toInt()
            binding.root.strokeColor = resolveAttrColor(
                context,
                if (selected) {
                    com.google.android.material.R.attr.colorPrimary
                } else {
                    com.google.android.material.R.attr.colorOutlineVariant
                }
            )

            binding.root.setOnClickListener { onItemClick(item) }
            binding.root.setOnLongClickListener {
                onItemLongClick(item)
                true
            }

            // While bulk selection mode is active the whole card is treated
            // as one selection target — tapping Restore or Delete toggles the
            // item's selection instead of running the action, matching the
            // user's mental model that the entire card is the selection unit.
            binding.restoreButton.setOnClickListener {
                if (isInSelectionMode()) onItemClick(item) else onRestoreClick(item)
            }
            binding.restoreButton.setOnLongClickListener {
                onItemLongClick(item)
                true
            }
            binding.permanentDeleteButton.setOnClickListener {
                if (isInSelectionMode()) onItemClick(item) else onPermanentDeleteClick(item)
            }
            binding.permanentDeleteButton.setOnLongClickListener {
                onItemLongClick(item)
                true
            }
        }

        private fun resolveAttrColor(context: android.content.Context, attr: Int): Int {
            val tv = TypedValue()
            return if (context.theme.resolveAttribute(attr, tv, true)) {
                if (tv.resourceId != 0) ContextCompat.getColor(context, tv.resourceId) else tv.data
            } else 0
        }
    }

    private class TrashDiffCallback : DiffUtil.ItemCallback<MediaItem>() {
        override fun areItemsTheSame(oldItem: MediaItem, newItem: MediaItem): Boolean =
            oldItem.path == newItem.path

        override fun areContentsTheSame(oldItem: MediaItem, newItem: MediaItem): Boolean =
            oldItem == newItem
    }
}
