package org.iurl.litegallery

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import org.iurl.litegallery.databinding.ItemGroupHeaderBinding
import org.iurl.litegallery.databinding.ItemMediaBinding
import org.iurl.litegallery.databinding.ItemMediaDetailedBinding
import org.iurl.litegallery.databinding.ItemMediaListBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class GroupedMediaAdapter(
    private val onMediaClick: (MediaItem, Int) -> Unit
) : ListAdapter<FolderDisplayItem, RecyclerView.ViewHolder>(DisplayItemDiffCallback()) {

    var viewMode: MediaAdapter.ViewMode = MediaAdapter.ViewMode.GRID
        set(value) {
            if (field == value) return
            field = value
            notifyDataSetChanged()
        }

    companion object {
        private const val VIEW_TYPE_HEADER = 10
        private const val VIEW_TYPE_GRID = 11
        private const val VIEW_TYPE_LIST = 12
        private const val VIEW_TYPE_DETAILED = 13
    }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is FolderDisplayItem.Header -> VIEW_TYPE_HEADER
            is FolderDisplayItem.Media -> when (viewMode) {
                MediaAdapter.ViewMode.GRID -> VIEW_TYPE_GRID
                MediaAdapter.ViewMode.LIST -> VIEW_TYPE_LIST
                MediaAdapter.ViewMode.DETAILED -> VIEW_TYPE_DETAILED
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_HEADER -> HeaderViewHolder(ItemGroupHeaderBinding.inflate(inflater, parent, false))
            VIEW_TYPE_GRID -> GridViewHolder(ItemMediaBinding.inflate(inflater, parent, false))
            VIEW_TYPE_LIST -> ListViewHolder(ItemMediaListBinding.inflate(inflater, parent, false))
            VIEW_TYPE_DETAILED -> DetailedViewHolder(ItemMediaDetailedBinding.inflate(inflater, parent, false))
            else -> throw IllegalArgumentException("Unknown view type: $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is FolderDisplayItem.Header -> (holder as HeaderViewHolder).bind(item)
            is FolderDisplayItem.Media -> when (holder) {
                is GridViewHolder -> holder.bind(item)
                is ListViewHolder -> holder.bind(item)
                is DetailedViewHolder -> holder.bind(item)
            }
        }
    }

    fun isHeaderPosition(position: Int): Boolean {
        return currentList.getOrNull(position) is FolderDisplayItem.Header
    }

    inner class HeaderViewHolder(private val binding: ItemGroupHeaderBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(header: FolderDisplayItem.Header) {
            binding.groupHeaderTextView.text = if (header.count > 0) {
                "${header.title} (${header.count})"
            } else {
                header.title
            }
        }
    }

    inner class GridViewHolder(private val binding: ItemMediaBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(displayItem: FolderDisplayItem.Media) {
            val mediaItem = displayItem.item
            bindThumbnail(binding, mediaItem)

            binding.sourceBadgeTextView.visibility = android.view.View.GONE
            binding.sourceBadgeTextView.contentDescription = null

            binding.root.setOnClickListener {
                onMediaClick(mediaItem, displayItem.mediaIndex)
            }
        }
    }

    inner class ListViewHolder(private val binding: ItemMediaListBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(displayItem: FolderDisplayItem.Media) {
            val mediaItem = displayItem.item
            bindThumbnail(binding, mediaItem)
            binding.fileNameTextView.text = mediaItem.name
            binding.root.setOnClickListener {
                onMediaClick(mediaItem, displayItem.mediaIndex)
            }
        }
    }

    inner class DetailedViewHolder(private val binding: ItemMediaDetailedBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(displayItem: FolderDisplayItem.Media) {
            val mediaItem = displayItem.item
            bindThumbnail(binding, mediaItem)
            binding.fileNameTextView.text = mediaItem.name

            if (mediaItem.width > 0 && mediaItem.height > 0) {
                binding.resolutionTextView.text = "${mediaItem.width} x ${mediaItem.height}"
                binding.resolutionTextView.visibility = android.view.View.VISIBLE
            } else {
                binding.resolutionTextView.visibility = android.view.View.GONE
            }

            val sizeStr = formatFileSize(mediaItem.size)
            if (sizeStr.isNotEmpty()) {
                binding.fileSizeTextView.text = sizeStr
                binding.fileSizeTextView.visibility = android.view.View.VISIBLE
            } else {
                binding.fileSizeTextView.visibility = android.view.View.GONE
            }

            val dateStr = formatDate(mediaItem.dateModified)
            if (dateStr.isNotEmpty()) {
                binding.dateTextView.text = dateStr
                binding.dateTextView.visibility = android.view.View.VISIBLE
            } else {
                binding.dateTextView.visibility = android.view.View.GONE
            }

            binding.root.setOnClickListener {
                onMediaClick(mediaItem, displayItem.mediaIndex)
            }
        }
    }

    private fun bindThumbnail(binding: ItemMediaBinding, mediaItem: MediaItem) {
        if (mediaItem.isSmb && mediaItem.isVideo) {
            binding.thumbnailImageView.setImageResource(R.drawable.ic_image_placeholder)
        } else {
            Glide.with(binding.root.context)
                .load(mediaItem.path)
                .centerCrop()
                .placeholder(R.drawable.ic_image_placeholder)
                .error(R.drawable.ic_image_placeholder)
                .into(binding.thumbnailImageView)
        }

        bindVideoOverlay(
            isVideo = mediaItem.isVideo,
            duration = mediaItem.getFormattedDuration(),
            playIcon = binding.playIcon,
            durationView = binding.durationTextView
        )
    }

    private fun bindThumbnail(binding: ItemMediaListBinding, mediaItem: MediaItem) {
        if (mediaItem.isSmb && mediaItem.isVideo) {
            binding.thumbnailImageView.setImageResource(R.drawable.ic_image_placeholder)
        } else {
            Glide.with(binding.root.context)
                .load(mediaItem.path)
                .centerCrop()
                .placeholder(R.drawable.ic_image_placeholder)
                .error(R.drawable.ic_image_placeholder)
                .into(binding.thumbnailImageView)
        }

        bindVideoOverlay(
            isVideo = mediaItem.isVideo,
            duration = mediaItem.getFormattedDuration(),
            playIcon = binding.playIcon,
            durationView = binding.durationTextView
        )
    }

    private fun bindThumbnail(binding: ItemMediaDetailedBinding, mediaItem: MediaItem) {
        if (mediaItem.isSmb && mediaItem.isVideo) {
            binding.thumbnailImageView.setImageResource(R.drawable.ic_image_placeholder)
        } else {
            Glide.with(binding.root.context)
                .load(mediaItem.path)
                .centerCrop()
                .placeholder(R.drawable.ic_image_placeholder)
                .error(R.drawable.ic_image_placeholder)
                .into(binding.thumbnailImageView)
        }

        bindVideoOverlay(
            isVideo = mediaItem.isVideo,
            duration = mediaItem.getFormattedDuration(),
            playIcon = binding.playIcon,
            durationView = binding.durationTextView
        )
    }

    private fun bindVideoOverlay(
        isVideo: Boolean,
        duration: String,
        playIcon: android.view.View,
        durationView: android.widget.TextView
    ) {
        if (isVideo) {
            playIcon.visibility = android.view.View.VISIBLE
            if (duration.isNotEmpty()) {
                durationView.visibility = android.view.View.VISIBLE
                durationView.text = duration
            } else {
                durationView.visibility = android.view.View.GONE
            }
        } else {
            playIcon.visibility = android.view.View.GONE
            durationView.visibility = android.view.View.GONE
        }
    }

    private fun formatFileSize(bytes: Long): String {
        if (bytes <= 0) return ""
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0

        return when {
            gb >= 1 -> String.format(Locale.getDefault(), "%.2f GB", gb)
            mb >= 1 -> String.format(Locale.getDefault(), "%.2f MB", mb)
            kb >= 1 -> String.format(Locale.getDefault(), "%.1f KB", kb)
            else -> "$bytes B"
        }
    }

    private fun formatDate(timestamp: Long): String {
        if (timestamp <= 0) return ""
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        return dateFormat.format(Date(timestamp))
    }

    private class DisplayItemDiffCallback : DiffUtil.ItemCallback<FolderDisplayItem>() {
        override fun areItemsTheSame(oldItem: FolderDisplayItem, newItem: FolderDisplayItem): Boolean {
            return when {
                oldItem is FolderDisplayItem.Header && newItem is FolderDisplayItem.Header ->
                    oldItem.key == newItem.key
                oldItem is FolderDisplayItem.Media && newItem is FolderDisplayItem.Media ->
                    oldItem.item.path == newItem.item.path
                else -> false
            }
        }

        override fun areContentsTheSame(oldItem: FolderDisplayItem, newItem: FolderDisplayItem): Boolean {
            return oldItem == newItem
        }
    }
}
