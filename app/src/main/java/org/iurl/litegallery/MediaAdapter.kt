package org.iurl.litegallery

import android.content.Context
import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.imageview.ShapeableImageView
import org.iurl.litegallery.databinding.ItemMediaBinding
import org.iurl.litegallery.databinding.ItemMediaListBinding
import org.iurl.litegallery.databinding.ItemMediaDetailedBinding
import org.iurl.litegallery.theme.ThemeColorResolver
import java.text.SimpleDateFormat
import java.util.*

class MediaAdapter(
    private val onMediaClick: (MediaItemSkeleton, Int) -> Unit,
    private val onMediaLongClick: ((MediaItemSkeleton, Int) -> Unit)? = null,
    private val isItemSelected: ((MediaItemSkeleton) -> Boolean)? = null,
    private val sourceBadgeLabelProvider: ((MediaItemSkeleton) -> String?)? = null,
    private val sourceBadgeContentDescriptionProvider: ((MediaItemSkeleton) -> String?)? = null,
    private val onDetailedMetadataNeeded: ((MediaItemSkeleton) -> Unit)? = null
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val items = mutableListOf<MediaItemSkeleton>()
    private val indexByPath = mutableMapOf<String, Int>()

    var viewMode: ViewMode = ViewMode.GRID
        set(value) {
            if (field == value) return
            field = value
            notifyItemRangeChanged(0, itemCount)
        }

    enum class ViewMode {
        GRID, LIST, DETAILED
    }

    companion object {
        private const val VIEW_TYPE_GRID = 0
        private const val VIEW_TYPE_LIST = 1
        private const val VIEW_TYPE_DETAILED = 2
        private const val PAYLOAD_SELECTION_STATE = "payload_selection_state"
        private const val PAYLOAD_NAME_CHANGED = "payload_name_changed"
        const val PAYLOAD_META_LOADED = "payload_meta_loaded"
        private const val SELECTION_STROKE_DP = 2f
    }

    @Suppress("UNUSED_PARAMETER")
    fun submitList(newItems: List<MediaItemSkeleton>, bypassDiff: Boolean = false, commitCallback: (() -> Unit)? = null) {
        items.clear()
        items.addAll(newItems)
        rebuildIndexByPath()
        notifyDataSetChanged()
        commitCallback?.invoke()
    }

    fun appendSkeletons(deltaItems: List<MediaItemSkeleton>) {
        if (deltaItems.isEmpty()) return
        val start = items.size
        items.addAll(deltaItems)
        deltaItems.forEachIndexed { offset, skeleton ->
            indexByPath[skeleton.path] = start + offset
        }
        notifyItemRangeInserted(start, deltaItems.size)
    }

    fun renameSkeleton(oldPath: String, updated: MediaItemSkeleton): Int {
        val index = indexByPath[oldPath] ?: return -1
        items[index] = updated
        indexByPath.remove(oldPath)
        indexByPath[updated.path] = index
        notifyItemChanged(index, PAYLOAD_NAME_CHANGED)
        return index
    }

    fun removeSkeletonPaths(paths: Set<String>): Boolean {
        if (paths.isEmpty()) return false
        var changed = false
        for (index in items.indices.reversed()) {
            if (items[index].path in paths) {
                items.removeAt(index)
                notifyItemRemoved(index)
                changed = true
            }
        }
        if (changed) {
            rebuildIndexByPath()
        }
        return changed
    }

    val currentList: List<MediaItemSkeleton>
        get() = items

    fun getItem(position: Int): MediaItemSkeleton = items[position]

    override fun getItemCount(): Int = items.size

    fun putCachedMetadata(item: MediaItem) {
        MediaMetadataCache.put(item)
    }

    fun getCachedMetadata(id: Long): MediaItem? {
        return MediaMetadataCache.get(id)
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
        val skeleton = getItem(position)
        val cachedMeta = MediaMetadataCache.get(skeleton)
        val mediaItemToBind = cachedMeta ?: skeleton.toBaselineMediaItem()

        when (holder) {
            is GridViewHolder -> holder.bind(skeleton, mediaItemToBind, position)
            is ListViewHolder -> holder.bind(skeleton, mediaItemToBind, position)
            is DetailedViewHolder -> holder.bind(skeleton, mediaItemToBind, position)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.contains(PAYLOAD_SELECTION_STATE)) {
            applySelectionState(holder.itemView, getItem(position))
            return
        }
        if (payloads.contains(PAYLOAD_META_LOADED) || payloads.contains(PAYLOAD_NAME_CHANGED)) {
            onBindViewHolder(holder, position)
            return
        }
        super.onBindViewHolder(holder, position, payloads)
    }

    fun notifySelectionChanged(changedPaths: Collection<String>) {
        if (changedPaths.isEmpty()) return

        changedPaths.forEach { path ->
            val index = indexByPath[path] ?: return@forEach
            notifyItemChanged(index, PAYLOAD_SELECTION_STATE)
        }
    }

    private fun rebuildIndexByPath() {
        indexByPath.clear()
        items.forEachIndexed { index, skeleton ->
            indexByPath[skeleton.path] = index
        }
    }

    // Grid View Holder
    inner class GridViewHolder(private val binding: ItemMediaBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(skeleton: MediaItemSkeleton, mediaItem: MediaItem, position: Int) {
            if (skeleton.isSmb && skeleton.isVideo) {
                binding.thumbnailImageView.setImageResource(R.drawable.ic_image_placeholder)
            } else {
                Glide.with(binding.root.context)
                    .load(skeleton.thumbnailModel())
                    .centerCrop()
                    .placeholder(R.drawable.ic_image_placeholder)
                    .error(R.drawable.ic_image_placeholder)
                    .into(binding.thumbnailImageView)
            }

            if (skeleton.isVideo) {
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

            val sourceBadgeLabel = sourceBadgeLabelProvider?.invoke(skeleton)
            if (sourceBadgeLabel.isNullOrBlank()) {
                binding.sourceBadgeTextView.visibility = android.view.View.GONE
                binding.sourceBadgeTextView.contentDescription = null
            } else {
                binding.sourceBadgeTextView.visibility = android.view.View.VISIBLE
                binding.sourceBadgeTextView.text = sourceBadgeLabel
                binding.sourceBadgeTextView.contentDescription =
                    sourceBadgeContentDescriptionProvider?.invoke(skeleton)
            }

            binding.root.setOnClickListener {
                onMediaClick(skeleton, position)
            }

            binding.root.setOnLongClickListener {
                if (onMediaLongClick != null) {
                    onMediaLongClick.invoke(skeleton, position)
                    true
                } else {
                    false
                }
            }

            applySelectionState(binding.root, skeleton)
        }
    }

    // List View Holder
    inner class ListViewHolder(private val binding: ItemMediaListBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(skeleton: MediaItemSkeleton, mediaItem: MediaItem, position: Int) {
            if (skeleton.isSmb && skeleton.isVideo) {
                binding.thumbnailImageView.setImageResource(R.drawable.ic_image_placeholder)
            } else {
                Glide.with(binding.root.context)
                    .load(skeleton.thumbnailModel())
                    .centerCrop()
                    .placeholder(R.drawable.ic_image_placeholder)
                    .error(R.drawable.ic_image_placeholder)
                    .into(binding.thumbnailImageView)
            }

            if (skeleton.isVideo) {
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

            binding.fileNameTextView.text = skeleton.name

            binding.root.setOnClickListener {
                onMediaClick(skeleton, position)
            }

            binding.root.setOnLongClickListener {
                if (onMediaLongClick != null) {
                    onMediaLongClick.invoke(skeleton, position)
                    true
                } else {
                    false
                }
            }

            applySelectionState(binding.root, skeleton)
        }
    }

    // Detailed View Holder
    inner class DetailedViewHolder(private val binding: ItemMediaDetailedBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(skeleton: MediaItemSkeleton, mediaItem: MediaItem, position: Int) {
            if (skeleton.isSmb && skeleton.isVideo) {
                binding.thumbnailImageView.setImageResource(R.drawable.ic_image_placeholder)
            } else {
                Glide.with(binding.root.context)
                    .load(skeleton.thumbnailModel())
                    .centerCrop()
                    .placeholder(R.drawable.ic_image_placeholder)
                    .error(R.drawable.ic_image_placeholder)
                    .into(binding.thumbnailImageView)
            }

            if (skeleton.isVideo) {
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

            binding.fileNameTextView.text = skeleton.name

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

            onDetailedMetadataNeeded?.invoke(skeleton)

            binding.root.setOnClickListener {
                onMediaClick(skeleton, position)
            }

            binding.root.setOnLongClickListener {
                if (onMediaLongClick != null) {
                    onMediaLongClick.invoke(skeleton, position)
                    true
                } else {
                    false
                }
            }

            applySelectionState(binding.root, skeleton)
        }
    }

    private fun applySelectionState(root: android.view.View, skeleton: MediaItemSkeleton) {
        val selected = isItemSelected?.invoke(skeleton) == true
        val thumbnail = root.findViewById<ShapeableImageView>(R.id.thumbnailImageView)

        root.isSelected = selected
        root.alpha = 1f
        if (selected) {
            thumbnail?.strokeColor = ColorStateList.valueOf(selectionStrokeColor(root.context))
            thumbnail?.strokeWidth = SELECTION_STROKE_DP * root.resources.displayMetrics.density
        } else {
            thumbnail?.strokeColor = null
            thumbnail?.strokeWidth = 0f
        }
    }

    private var cachedSelectionStrokeColor: Int? = null

    private fun selectionStrokeColor(context: Context): Int =
        cachedSelectionStrokeColor ?: ThemeColorResolver
            .resolveColor(context, com.google.android.material.R.attr.colorPrimary)
            .also { cachedSelectionStrokeColor = it }

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

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

    private fun formatDate(timestamp: Long): String {
        if (timestamp <= 0) return ""
        return dateFormat.format(Date(timestamp))
    }
}
