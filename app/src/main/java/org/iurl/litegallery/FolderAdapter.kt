package org.iurl.litegallery

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import org.iurl.litegallery.databinding.ItemFolderBinding

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
            binding.folderNameTextView.text = folder.name
            binding.itemCountTextView.text = binding.root.context.getString(
                R.string.items_count,
                folder.itemCount
            )

            // Load thumbnail
            val thumb = folder.thumbnail
            if (thumb != null && !(SmbPath.isSmb(thumb) && !SmbModelLoader.isSmbImage(thumb))) {
                // Load via Glide: works for local files and SMB images
                Glide.with(binding.root.context)
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