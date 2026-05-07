package org.iurl.litegallery

import android.text.format.DateUtils
import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import org.iurl.litegallery.databinding.ItemFolderBinding
import java.text.NumberFormat

class FolderAdapter(private val onFolderClick: (MediaFolder) -> Unit) :
    ListAdapter<MediaFolder, FolderAdapter.FolderViewHolder>(FolderDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FolderViewHolder {
        val binding = ItemFolderBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return FolderViewHolder(binding)
    }

    override fun onBindViewHolder(holder: FolderViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class FolderViewHolder(private val binding: ItemFolderBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(folder: MediaFolder) {
            val context = binding.root.context

            binding.folderNameTextView.text = folder.name
            binding.countChip.text = formatCount(folder.itemCount)
            TonalCountChipStyler.apply(binding.countChip)
            binding.folderMetaTextView.text = formatMeta(folder)
            binding.folderMetaTextView.visibility =
                if (binding.folderMetaTextView.text.isNullOrEmpty()) View.GONE else View.VISIBLE

            // Load thumbnail
            val thumb = folder.thumbnail
            if (thumb != null && !(SmbPath.isSmb(thumb) && !SmbModelLoader.isSmbImage(thumb))) {
                Glide.with(context)
                    .load(thumb)
                    .centerCrop()
                    .placeholder(R.drawable.ic_folder)
                    .error(R.drawable.ic_folder)
                    .into(binding.thumbnailImageView)
            } else {
                val iconRes = if (SmbPath.isSmb(folder.path)) {
                    R.drawable.ic_network
                } else {
                    R.drawable.ic_folder
                }
                binding.thumbnailImageView.setImageResource(iconRes)
            }

            binding.root.setOnClickListener {
                onFolderClick(folder)
            }
        }

        private fun formatCount(count: Int): String {
            return when {
                count < 1000 -> NumberFormat.getNumberInstance().format(count)
                count < 10_000 -> String.format("%.1fk", count / 1000.0)
                count < 1_000_000 -> "${count / 1000}k"
                else -> String.format("%.1fM", count / 1_000_000.0)
            }
        }

        private fun formatMeta(folder: MediaFolder): String {
            // SMB virtual folder has no meaningful date/size info — leave meta empty.
            if (SmbPath.isSmb(folder.path)) return ""

            val context = binding.root.context
            val datePart = if (folder.latestDateModifiedMs > 0L) {
                DateUtils.formatDateTime(
                    context,
                    folder.latestDateModifiedMs,
                    DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_MONTH or DateUtils.FORMAT_NO_YEAR
                )
            } else null

            val sizePart = if (folder.totalSizeBytes > 0L) {
                Formatter.formatShortFileSize(context, folder.totalSizeBytes)
            } else null

            return when {
                datePart != null && sizePart != null ->
                    context.getString(R.string.folder_meta_format, datePart, sizePart)
                datePart != null -> datePart
                sizePart != null -> sizePart
                else -> ""
            }
        }
    }

    private class FolderDiffCallback : DiffUtil.ItemCallback<MediaFolder>() {
        override fun areItemsTheSame(oldItem: MediaFolder, newItem: MediaFolder): Boolean {
            return oldItem.path == newItem.path
        }

        override fun areContentsTheSame(oldItem: MediaFolder, newItem: MediaFolder): Boolean {
            return oldItem == newItem
        }
    }
}
