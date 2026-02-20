package org.iurl.litegallery

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import org.iurl.litegallery.databinding.ItemMediaBinding
import org.iurl.litegallery.databinding.ItemMediaListBinding
import org.iurl.litegallery.databinding.ItemMediaDetailedBinding
import java.text.SimpleDateFormat
import java.util.*

class MediaAdapter(
    private val onMediaClick: (MediaItem, Int) -> Unit,
    private val onMediaLongClick: ((MediaItem, Int) -> Unit)? = null,
    private val isItemSelected: ((MediaItem) -> Boolean)? = null
) :
    ListAdapter<MediaItem, RecyclerView.ViewHolder>(MediaDiffCallback()) {

    var viewMode: ViewMode = ViewMode.GRID
        set(value) {
            if (field == value) return
            field = value
            notifyDataSetChanged()
        }

    enum class ViewMode {
        GRID, LIST, DETAILED
    }

    companion object {
        private const val VIEW_TYPE_GRID = 0
        private const val VIEW_TYPE_LIST = 1
        private const val VIEW_TYPE_DETAILED = 2
        private const val PAYLOAD_SELECTION_STATE = "payload_selection_state"
    }

    override fun getItemViewType(position: Int): Int {
        return when (viewMode) {
            ViewMode.GRID -> VIEW_TYPE_GRID
            ViewMode.LIST -> VIEW_TYPE_LIST
            ViewMode.DETAILED -> VIEW_TYPE_DETAILED
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_GRID -> {
                val binding = ItemMediaBinding.inflate(LayoutInflater.from(parent.context), parent, false)
                GridViewHolder(binding)
            }
            VIEW_TYPE_LIST -> {
                val binding = ItemMediaListBinding.inflate(LayoutInflater.from(parent.context), parent, false)
                ListViewHolder(binding)
            }
            VIEW_TYPE_DETAILED -> {
                val binding = ItemMediaDetailedBinding.inflate(LayoutInflater.from(parent.context), parent, false)
                DetailedViewHolder(binding)
            }
            else -> throw IllegalArgumentException("Unknown view type: $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val mediaItem = getItem(position)
        when (holder) {
            is GridViewHolder -> holder.bind(mediaItem, position)
            is ListViewHolder -> holder.bind(mediaItem, position)
            is DetailedViewHolder -> holder.bind(mediaItem, position)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.contains(PAYLOAD_SELECTION_STATE)) {
            applySelectionState(holder.itemView, getItem(position))
            return
        }
        super.onBindViewHolder(holder, position, payloads)
    }

    fun notifySelectionChanged(changedPaths: Collection<String>) {
        if (changedPaths.isEmpty()) return

        val indexByPath = currentList
            .mapIndexed { index, mediaItem -> mediaItem.path to index }
            .toMap()

        changedPaths.forEach { path ->
            val index = indexByPath[path] ?: return@forEach
            notifyItemChanged(index, PAYLOAD_SELECTION_STATE)
        }
    }

    // Grid View Holder (Original)
    inner class GridViewHolder(private val binding: ItemMediaBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(mediaItem: MediaItem, position: Int) {
            // Load thumbnail
            Glide.with(binding.root.context)
                .load(mediaItem.path)
                .centerCrop()
                .placeholder(R.drawable.ic_image_placeholder)
                .error(R.drawable.ic_image_placeholder)
                .into(binding.thumbnailImageView)

            // Show video indicators
            if (mediaItem.isVideo) {
                binding.playIcon.visibility = android.view.View.VISIBLE
                val duration = mediaItem.getFormattedDuration()
                if (duration.isNotEmpty()) {
                    binding.durationTextView.visibility = android.view.View.VISIBLE
                    binding.durationTextView.text = duration
                } else {
                    binding.durationTextView.visibility = android.view.View.GONE
                }
            } else {
                binding.playIcon.visibility = android.view.View.GONE
                binding.durationTextView.visibility = android.view.View.GONE
            }

            binding.root.setOnClickListener {
                onMediaClick(mediaItem, position)
            }

            binding.root.setOnLongClickListener {
                if (onMediaLongClick != null) {
                    onMediaLongClick.invoke(mediaItem, position)
                    true
                } else {
                    false
                }
            }

            applySelectionState(binding.root, mediaItem)
        }
    }

    // List View Holder
    inner class ListViewHolder(private val binding: ItemMediaListBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(mediaItem: MediaItem, position: Int) {
            // Load thumbnail
            Glide.with(binding.root.context)
                .load(mediaItem.path)
                .centerCrop()
                .placeholder(R.drawable.ic_image_placeholder)
                .error(R.drawable.ic_image_placeholder)
                .into(binding.thumbnailImageView)

            // Show video indicators
            if (mediaItem.isVideo) {
                binding.playIcon.visibility = android.view.View.VISIBLE
                val duration = mediaItem.getFormattedDuration()
                if (duration.isNotEmpty()) {
                    binding.durationTextView.visibility = android.view.View.VISIBLE
                    binding.durationTextView.text = duration
                } else {
                    binding.durationTextView.visibility = android.view.View.GONE
                }
            } else {
                binding.playIcon.visibility = android.view.View.GONE
                binding.durationTextView.visibility = android.view.View.GONE
            }

            // File name only
            binding.fileNameTextView.text = mediaItem.name

            binding.root.setOnClickListener {
                onMediaClick(mediaItem, position)
            }

            binding.root.setOnLongClickListener {
                if (onMediaLongClick != null) {
                    onMediaLongClick.invoke(mediaItem, position)
                    true
                } else {
                    false
                }
            }

            applySelectionState(binding.root, mediaItem)
        }
    }

    // Detailed View Holder
    inner class DetailedViewHolder(private val binding: ItemMediaDetailedBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(mediaItem: MediaItem, position: Int) {
            // Load thumbnail
            Glide.with(binding.root.context)
                .load(mediaItem.path)
                .centerCrop()
                .placeholder(R.drawable.ic_image_placeholder)
                .error(R.drawable.ic_image_placeholder)
                .into(binding.thumbnailImageView)

            // Show video indicators
            if (mediaItem.isVideo) {
                binding.playIcon.visibility = android.view.View.VISIBLE
                val duration = mediaItem.getFormattedDuration()
                if (duration.isNotEmpty()) {
                    binding.durationTextView.visibility = android.view.View.VISIBLE
                    binding.durationTextView.text = duration
                } else {
                    binding.durationTextView.visibility = android.view.View.GONE
                }
            } else {
                binding.playIcon.visibility = android.view.View.GONE
                binding.durationTextView.visibility = android.view.View.GONE
            }

            // File name
            binding.fileNameTextView.text = mediaItem.name

            // Resolution
            if (mediaItem.width > 0 && mediaItem.height > 0) {
                binding.resolutionTextView.text = "${mediaItem.width} x ${mediaItem.height}"
                binding.resolutionTextView.visibility = android.view.View.VISIBLE
            } else {
                binding.resolutionTextView.visibility = android.view.View.GONE
            }

            // File size
            val sizeStr = formatFileSize(mediaItem.size)
            if (sizeStr.isNotEmpty()) {
                binding.fileSizeTextView.text = sizeStr
                binding.fileSizeTextView.visibility = android.view.View.VISIBLE
            } else {
                binding.fileSizeTextView.visibility = android.view.View.GONE
            }

            // Date
            val dateStr = formatDate(mediaItem.dateModified)
            if (dateStr.isNotEmpty()) {
                binding.dateTextView.text = dateStr
                binding.dateTextView.visibility = android.view.View.VISIBLE
            } else {
                binding.dateTextView.visibility = android.view.View.GONE
            }

            binding.root.setOnClickListener {
                onMediaClick(mediaItem, position)
            }

            binding.root.setOnLongClickListener {
                if (onMediaLongClick != null) {
                    onMediaLongClick.invoke(mediaItem, position)
                    true
                } else {
                    false
                }
            }

            applySelectionState(binding.root, mediaItem)
        }
    }

    private fun applySelectionState(root: android.view.View, mediaItem: MediaItem) {
        val selected = isItemSelected?.invoke(mediaItem) == true
        root.alpha = if (selected) 0.6f else 1f
    }

    private fun formatFileSize(bytes: Long): String {
        if (bytes <= 0) return ""
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0

        return when {
            gb >= 1 -> String.format("%.2f GB", gb)
            mb >= 1 -> String.format("%.2f MB", mb)
            kb >= 1 -> String.format("%.1f KB", kb)
            else -> "$bytes B"
        }
    }

    private fun formatDate(timestamp: Long): String {
        if (timestamp <= 0) return ""
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        return dateFormat.format(Date(timestamp))
    }

    private class MediaDiffCallback : DiffUtil.ItemCallback<MediaItem>() {
        override fun areItemsTheSame(oldItem: MediaItem, newItem: MediaItem): Boolean {
            return oldItem.path == newItem.path
        }

        override fun areContentsTheSame(oldItem: MediaItem, newItem: MediaItem): Boolean {
            return oldItem == newItem
        }
    }
}
