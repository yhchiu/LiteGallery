package com.litegallery

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.litegallery.databinding.ItemMediaViewerBinding

class MediaViewerAdapter(
    private val onMediaClick: () -> Unit
) : ListAdapter<MediaItem, MediaViewerAdapter.MediaViewHolder>(MediaItemDiffCallback()) {

    private var currentVideoHolder: MediaViewHolder? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MediaViewHolder {
        val binding = ItemMediaViewerBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return MediaViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MediaViewHolder, position: Int) {
        val mediaItem = getItem(position)
        
        // Always pause and release any current video player first
        currentVideoHolder?.let { currentHolder ->
            if (currentHolder != holder) {
                android.util.Log.d("MediaViewerAdapter", "Releasing previous video player for large file switching")
                currentHolder.onPause()
                currentHolder.releasePlayer()
                currentVideoHolder = null
                
                // Check memory usage before proceeding
                val runtime = Runtime.getRuntime()
                val usedMemory = runtime.totalMemory() - runtime.freeMemory()
                val maxMemory = runtime.maxMemory()
                val memoryUsagePercent = (usedMemory.toFloat() / maxMemory * 100).toInt()
                
                android.util.Log.d("MediaViewerAdapter", "Memory usage after release: $memoryUsagePercent%")
                
                // Extra aggressive cleanup for large video files
                for (i in 0..2) {
                    System.gc()
                    System.runFinalization()
                    try {
                        Thread.sleep(50)
                    } catch (e: InterruptedException) {
                        Thread.currentThread().interrupt()
                        break
                    }
                }
                
                // Longer wait for surface buffer cleanup
                try {
                    Thread.sleep(250) // Extended wait for surface cleanup
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                }
                
                android.util.Log.d("MediaViewerAdapter", "Surface cleanup complete, proceeding with new video")
            }
        }
        
        // Bind content immediately for images, delay for videos
        if (mediaItem.isVideo) {
            // For large video files, add extended delay to ensure surface buffers are fully released
            holder.itemView.postDelayed({
                android.util.Log.d("MediaViewerAdapter", "Starting video binding after surface cleanup")
                holder.bind(mediaItem)
                currentVideoHolder = holder
            }, 400) // Increased delay for large video file surface buffer management
        } else {
            holder.bind(mediaItem)
        }
    }

    override fun onViewRecycled(holder: MediaViewHolder) {
        super.onViewRecycled(holder)
        holder.releasePlayer()
        if (currentVideoHolder == holder) {
            currentVideoHolder = null
        }
    }
    
    fun pauseAllVideos() {
        currentVideoHolder?.onPause()
    }
    
    fun releaseAllPlayers() {
        currentVideoHolder?.releasePlayer()
        currentVideoHolder = null
    }

    inner class MediaViewHolder(private val binding: ItemMediaViewerBinding) :
        RecyclerView.ViewHolder(binding.root) {

        private var videoViewHolder: VideoViewHolder? = null

        fun bind(mediaItem: MediaItem) {
            if (mediaItem.isVideo) {
                // Hide image view, show video container
                binding.photoImageView.visibility = View.GONE
                binding.videoContainer.visibility = View.VISIBLE
                
                // Load video thumbnail as fallback
                Glide.with(binding.root.context)
                    .load(mediaItem.path)
                    .centerCrop()
                    .into(binding.videoThumbnail)
                
                // Set up video player
                videoViewHolder = VideoViewHolder(binding, onMediaClick)
                videoViewHolder?.bind(mediaItem)
                    
            } else {
                // Show image, hide video container
                binding.photoImageView.visibility = View.VISIBLE
                binding.videoContainer.visibility = View.GONE
                
                // Release any video player
                releasePlayer()
                
                // Load image with Glide
                Glide.with(binding.root.context)
                    .load(mediaItem.path)
                    .fitCenter()
                    .into(binding.photoImageView)

                // Set click listener for photos
                binding.root.setOnClickListener {
                    onMediaClick()
                }
            }
        }

        fun releasePlayer() {
            videoViewHolder?.releasePlayer()
            videoViewHolder = null
        }

        fun onResume() {
            videoViewHolder?.onResume()
        }

        fun onPause() {
            videoViewHolder?.onPause()
        }
    }

    private class MediaItemDiffCallback : DiffUtil.ItemCallback<MediaItem>() {
        override fun areItemsTheSame(oldItem: MediaItem, newItem: MediaItem): Boolean {
            return oldItem.path == newItem.path
        }

        override fun areContentsTheSame(oldItem: MediaItem, newItem: MediaItem): Boolean {
            return oldItem == newItem
        }
    }
}