package com.litegallery

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.litegallery.databinding.ItemFolderBinding

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
            if (folder.thumbnail != null) {
                Glide.with(binding.root.context)
                    .load(folder.thumbnail)
                    .centerCrop()
                    .placeholder(R.drawable.ic_folder)
                    .error(R.drawable.ic_folder)
                    .into(binding.thumbnailImageView)
            } else {
                binding.thumbnailImageView.setImageResource(R.drawable.ic_folder)
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