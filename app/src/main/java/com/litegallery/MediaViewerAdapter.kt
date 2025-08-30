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
    
    private var onVideoDoubleClick: (() -> Unit)? = null
    private var onZoomChange: ((Float) -> Unit)? = null
    
    fun setVideoDoubleClickListener(listener: () -> Unit) {
        onVideoDoubleClick = listener
    }
    
    fun setZoomChangeListener(listener: (Float) -> Unit) {
        onZoomChange = listener
    }

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
    
    fun getCurrentVideoHolder(): VideoViewHolder? {
        return currentVideoHolder?.videoViewHolder
    }

    inner class MediaViewHolder(private val binding: ItemMediaViewerBinding) :
        RecyclerView.ViewHolder(binding.root) {

        var videoViewHolder: VideoViewHolder? = null
            private set

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
                
                // Set up gesture detection for videos
                setupVideoGestures()
                    
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

                // Set click listener for photos using ZoomImageView's method
                binding.photoImageView.setOnImageClickListener {
                    onMediaClick()
                }
                
                // Set zoom change listener for photos
                binding.photoImageView.setOnZoomChangeListener { zoomLevel ->
                    onZoomChange?.invoke(zoomLevel)
                }
                
                // Remove any touch listeners that might interfere
                binding.root.setOnTouchListener(null)
            }
        }
        
        private fun setupVideoGestures() {
            // Set up gesture detection for videos without interfering with ViewPager2
            val gestureDetector = android.view.GestureDetector(binding.root.context, object : android.view.GestureDetector.SimpleOnGestureListener() {
                override fun onSingleTapConfirmed(e: android.view.MotionEvent): Boolean {
                    onMediaClick()
                    return true
                }
                
                override fun onDoubleTap(e: android.view.MotionEvent): Boolean {
                    onVideoDoubleClick?.invoke()
                    return true
                }
            })
            
            // Set up gesture listeners on ZoomablePlayerView
            (binding.playerView as? com.litegallery.ZoomablePlayerView)?.let { zoomablePlayerView ->
                zoomablePlayerView.setOnVideoClickListener {
                    onMediaClick()
                }
                
                zoomablePlayerView.setOnVideoDoubleClickListener {
                    onVideoDoubleClick?.invoke()
                }
                
                zoomablePlayerView.setOnZoomChangeListener { zoomLevel ->
                    onZoomChange?.invoke(zoomLevel)
                }
            }
            
            // Also add gesture detection to video thumbnail for when player isn't ready
            binding.videoThumbnail?.setOnTouchListener { _, event ->
                gestureDetector.onTouchEvent(event)
                true // Consume the event for video-specific gestures
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
        
        fun getZoomImageView(): com.litegallery.ZoomImageView? {
            return binding.photoImageView as? com.litegallery.ZoomImageView
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