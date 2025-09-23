package org.iurl.litegallery

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import org.iurl.litegallery.databinding.ItemMediaBinding

class MediaAdapter(private val onMediaClick: (MediaItem, Int) -> Unit) :
    ListAdapter<MediaItem, MediaAdapter.MediaViewHolder>(MediaDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MediaViewHolder {
        val binding = ItemMediaBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return MediaViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MediaViewHolder, position: Int) {
        holder.bind(getItem(position), position)
    }

    inner class MediaViewHolder(private val binding: ItemMediaBinding) :
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
        }

        private fun formatDuration(durationMs: Long): String {
            val seconds = durationMs / 1000
            val minutes = seconds / 60
            val hours = minutes / 60

            return if (hours > 0) {
                String.format("%d:%02d:%02d", hours, minutes % 60, seconds % 60)
            } else {
                String.format("%d:%02d", minutes, seconds % 60)
            }
        }
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