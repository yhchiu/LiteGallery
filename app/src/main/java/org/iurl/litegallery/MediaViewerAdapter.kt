package org.iurl.litegallery

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import org.iurl.litegallery.databinding.ItemMediaViewerBinding

class MediaViewerAdapter(
    private val onMediaClick: () -> Unit
) : ListAdapter<MediaItem, MediaViewerAdapter.MediaViewHolder>(MediaItemDiffCallback()) {
    
    private var onVideoDoubleClick: (() -> Unit)? = null
    private var onZoomChange: ((Float) -> Unit)? = null
    private var onBrightnessChange: ((Float) -> Unit)? = null
    private var onVolumeChange: ((Float) -> Unit)? = null
    private var onValueDisplay: ((String, Float) -> Unit)? = null
    
    fun setVideoDoubleClickListener(listener: () -> Unit) {
        android.util.Log.d("MediaViewerAdapter", "Video double-click listener set")
        onVideoDoubleClick = listener
    }
    
    fun setZoomChangeListener(listener: (Float) -> Unit) {
        onZoomChange = listener
    }

    fun setBrightnessChangeListener(listener: (Float) -> Unit) {
        onBrightnessChange = listener
    }

    fun setVolumeChangeListener(listener: (Float) -> Unit) {
        onVolumeChange = listener
    }

    fun setValueDisplayListener(listener: (String, Float) -> Unit) {
        onValueDisplay = listener
    }

    private var currentVideoHolder: MediaViewHolder? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MediaViewHolder {
        val binding = ItemMediaViewerBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return MediaViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MediaViewHolder, position: Int) {
        val mediaItem = getItem(position)
        
        android.util.Log.d("MediaViewerAdapter", "Binding ViewHolder for: ${mediaItem.name} (position: $position)")
        
        // Log memory status before cleanup
        logMemoryStatus("BEFORE cleanup")
        
        // Only release previous video player if this holder had one
        if (currentVideoHolder == holder) {
            android.util.Log.d("MediaViewerAdapter", "Releasing current video holder player")
            holder.releasePlayer()
        }
        
        // Selective state reset - only reset what's necessary
        holder.prepareForNewContent(mediaItem)
        
        // Bind the new content
        holder.bind(mediaItem)
        
        // Track video holder for management
        if (mediaItem.isVideo) {
            currentVideoHolder = holder
        } else {
            // Clear video holder reference for photos
            if (currentVideoHolder == holder) {
                currentVideoHolder = null
            }
        }
        
        // Log memory status after setup
        logMemoryStatus("AFTER setup")
    }
    
    private fun logMemoryStatus(stage: String) {
        val runtime = Runtime.getRuntime()
        val usedMemory = runtime.totalMemory() - runtime.freeMemory()
        val maxMemory = runtime.maxMemory()
        val memoryUsagePercent = (usedMemory.toFloat() / maxMemory * 100).toInt()
        android.util.Log.d("MediaViewerAdapter", 
            "$stage - Memory: $memoryUsagePercent% (${usedMemory / 1024 / 1024}MB / ${maxMemory / 1024 / 1024}MB)")
    }

    override fun onViewRecycled(holder: MediaViewHolder) {
        super.onViewRecycled(holder)
        android.util.Log.d("MediaViewerAdapter", "ViewHolder recycled - selective cleanup")
        
        // Release player if this holder has one
        holder.releasePlayer()
        
        // Clear current video holder reference
        if (currentVideoHolder == holder) {
            android.util.Log.d("MediaViewerAdapter", "Clearing current video holder reference")
            currentVideoHolder = null
        }
        
        // Clear Glide image cache for this holder
        holder.clearGlideCache()
        
        android.util.Log.d("MediaViewerAdapter", "ViewHolder selective cleanup completed")
    }
    
    override fun onViewAttachedToWindow(holder: MediaViewHolder) {
        super.onViewAttachedToWindow(holder)
        android.util.Log.d("MediaViewerAdapter", "ViewHolder attached to window")
    }
    
    override fun onViewDetachedFromWindow(holder: MediaViewHolder) {
        super.onViewDetachedFromWindow(holder)
        android.util.Log.d("MediaViewerAdapter", "ViewHolder detached from window")
        // Pause any active video when detached
        holder.onPause()
    }
    
    fun pauseAllVideos() {
        currentVideoHolder?.onPause()
    }
    
    fun releaseAllPlayers() {
        android.util.Log.d("MediaViewerAdapter", "Releasing current video player only")
        
        // Release current video holder only
        currentVideoHolder?.let { holder ->
            android.util.Log.d("MediaViewerAdapter", "Releasing current video holder")
            holder.releasePlayer()
            holder.videoViewHolder?.releasePlayer()
        }
        currentVideoHolder = null
        
        android.util.Log.d("MediaViewerAdapter", "Current video player released")
    }
    
    fun getCurrentVideoHolder(): VideoViewHolder? {
        val videoHolder = currentVideoHolder?.videoViewHolder
        android.util.Log.d("MediaViewerAdapter", "getCurrentVideoHolder: currentVideoHolder=${currentVideoHolder != null}, videoViewHolder=${videoHolder != null}")
        return videoHolder
    }
    
    fun getCurrentMediaViewHolder(): MediaViewHolder? {
        return currentVideoHolder
    }
    
    fun setCurrentVideoHolder(holder: MediaViewHolder?) {
        android.util.Log.d("MediaViewerAdapter", "Manually setting current video holder: ${holder != null}")
        currentVideoHolder = holder
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
                val context = binding.root.context
                if (context is android.app.Activity && !context.isDestroyed && !context.isFinishing) {
                    Glide.with(context)
                        .load(mediaItem.path)
                        .centerCrop()
                        .into(binding.videoThumbnail)
                }
                
                // Set up video player
                videoViewHolder = VideoViewHolder(binding, onMediaClick)
                videoViewHolder?.bind(mediaItem)
                
                // CRITICAL: Set up gesture detection for videos AFTER player is ready
                setupVideoGestures()
                
                android.util.Log.d("MediaViewerAdapter", "Video gestures setup completed for: ${mediaItem.name}")
                    
            } else {
                // Show image, hide video container
                binding.photoImageView.visibility = View.VISIBLE
                binding.videoContainer.visibility = View.GONE
                
                // Release any video player
                releasePlayer()
                
                // Load image with Glide
                val context = binding.root.context
                if (context is android.app.Activity && !context.isDestroyed && !context.isFinishing) {
                    Glide.with(context)
                        .load(mediaItem.path)
                        .fitCenter()
                        .into(binding.photoImageView)
                }

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
            android.util.Log.d("MediaViewerAdapter", "Setting up video gestures")
            
            // Set up gesture detection for videos without interfering with ViewPager2
            // SWAPPED: Single-tap -> play/pause, Double-tap -> show/hide UI
            val gestureDetector = android.view.GestureDetector(binding.root.context, object : android.view.GestureDetector.SimpleOnGestureListener() {
                override fun onSingleTapConfirmed(e: android.view.MotionEvent): Boolean {
                    android.util.Log.d("MediaViewerAdapter", "Single tap detected on gesture detector -> toggle playback")
                    onVideoDoubleClick?.let { callback ->
                        android.util.Log.d("MediaViewerAdapter", "Invoking single-tap playback toggle")
                        callback.invoke()
                    } ?: run {
                        android.util.Log.w("MediaViewerAdapter", "Playback toggle callback is null!")
                    }
                    return true
                }
                
                override fun onDoubleTap(e: android.view.MotionEvent): Boolean {
                    android.util.Log.d("MediaViewerAdapter", "Double tap detected on gesture detector -> toggle UI")
                    onMediaClick()
                    return true
                }
            })
            
            // Set up gesture listeners on ZoomablePlayerView with configurable actions
            (binding.playerView as? org.iurl.litegallery.ZoomablePlayerView)?.let { zoomablePlayerView ->
                android.util.Log.d("MediaViewerAdapter", "Setting up ZoomablePlayerView gestures")

                // Set up gesture action listeners
                zoomablePlayerView.setOnPlayPauseListener {
                    android.util.Log.d("MediaViewerAdapter", "Play/Pause gesture triggered")
                    onVideoDoubleClick?.invoke()
                }

                zoomablePlayerView.setOnToggleUIListener {
                    android.util.Log.d("MediaViewerAdapter", "Toggle UI gesture triggered")
                    onMediaClick()
                }

                zoomablePlayerView.setOnShowUIListener {
                    android.util.Log.d("MediaViewerAdapter", "Show UI gesture triggered")
                    // Show UI if it's hidden - can reuse onMediaClick if UI is hidden
                    onMediaClick()
                }

                zoomablePlayerView.setOnHideUIListener {
                    android.util.Log.d("MediaViewerAdapter", "Hide UI gesture triggered")
                    // Hide UI if it's shown - can reuse onMediaClick if UI is shown
                    onMediaClick()
                }

                zoomablePlayerView.setOnBrightnessChangeListener { brightness ->
                    android.util.Log.d("MediaViewerAdapter", "Brightness change: $brightness")
                    onBrightnessChange?.invoke(brightness)
                }

                zoomablePlayerView.setOnVolumeChangeListener { volume ->
                    android.util.Log.d("MediaViewerAdapter", "Volume change: $volume")
                    onVolumeChange?.invoke(volume)
                }

                zoomablePlayerView.setOnValueDisplayListener { type, value ->
                    android.util.Log.d("MediaViewerAdapter", "Value display: $type = $value")
                    onValueDisplay?.invoke(type, value)
                }

                zoomablePlayerView.setOnZoomChangeListener { zoomLevel ->
                    android.util.Log.d("MediaViewerAdapter", "Zoom change detected: $zoomLevel")
                    onZoomChange?.invoke(zoomLevel)
                }
            } ?: run {
                android.util.Log.w("MediaViewerAdapter", "PlayerView is not ZoomablePlayerView!")
            }
            
            // PRIORITY 2: Add gesture detection to video thumbnail for when player isn't ready
            binding.videoThumbnail?.setOnTouchListener { _, event ->
                android.util.Log.d("MediaViewerAdapter", "Touch event on video thumbnail")
                gestureDetector.onTouchEvent(event)
                true // Consume the event for video-specific gestures
            }
            
            // PRIORITY 3: Add gesture detection to video container as fallback
            binding.videoContainer.setOnTouchListener { _, event ->
                android.util.Log.d("MediaViewerAdapter", "Touch event on video container")
                gestureDetector.onTouchEvent(event)
                false // Don't consume to allow child views to handle
            }
            
            android.util.Log.d("MediaViewerAdapter", "Video gesture setup completed")
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
        
        fun getZoomImageView(): org.iurl.litegallery.ZoomImageView? {
            return binding.photoImageView as? org.iurl.litegallery.ZoomImageView
        }
        
        fun clearGlideCache() {
            val context = binding.root.context
            if (context is android.app.Activity && !context.isDestroyed && !context.isFinishing) {
                com.bumptech.glide.Glide.with(context).clear(binding.photoImageView)
            }
        }
        
        fun resetPhotoZoom() {
            getZoomImageView()?.resetZoom()
        }
        
        fun getZoomablePlayerView(): org.iurl.litegallery.ZoomablePlayerView? {
            return videoViewHolder?.getZoomablePlayerView()
        }
        
        fun prepareForNewContent(mediaItem: MediaItem) {
            android.util.Log.d("MediaViewerAdapter", "Preparing ViewHolder for new content: ${mediaItem.name}")
            
            // Only reset what's actually needed based on content type
            if (mediaItem.isVideo) {
                // Prepare for video content
                if (binding.videoContainer.visibility != View.VISIBLE) {
                    // Clear any existing image content
                    val context = binding.root.context
                    if (context is android.app.Activity && !context.isDestroyed && !context.isFinishing) {
                        com.bumptech.glide.Glide.with(context).clear(binding.photoImageView)
                    }
                    binding.photoImageView.visibility = View.GONE
                    binding.videoContainer.visibility = View.VISIBLE
                }
                
                // Reset video-specific UI elements
                binding.videoThumbnail?.visibility = View.VISIBLE
                binding.playButton?.visibility = View.VISIBLE
                
                // Only reset PlayerView zoom if it exists
                (binding.playerView as? org.iurl.litegallery.ZoomablePlayerView)?.resetZoom()
                
            } else {
                // Prepare for photo content
                if (binding.photoImageView.visibility != View.VISIBLE) {
                    // Release any existing video player only if switching from video
                    releasePlayer()
                    binding.photoImageView.visibility = View.VISIBLE
                    binding.videoContainer.visibility = View.GONE
                }
                
                // Reset photo-specific zoom
                resetPhotoZoom()
            }
            
            // Clear any pending callbacks
            binding.root.handler?.removeCallbacksAndMessages(null)
        }
        
        fun forceCompleteReset() {
            android.util.Log.d("MediaViewerAdapter", "Force complete reset of ViewHolder")
            
            // Release any existing video player
            releasePlayer()
            
            // Reset all UI elements to default state
            binding.photoImageView.visibility = View.VISIBLE
            binding.videoContainer.visibility = View.GONE
            binding.videoThumbnail?.visibility = View.VISIBLE
            binding.playButton?.visibility = View.VISIBLE
            
            // Clear image from Glide
            val context = binding.root.context
            if (context is android.app.Activity && !context.isDestroyed && !context.isFinishing) {
                com.bumptech.glide.Glide.with(context).clear(binding.photoImageView)
            }
            
            // Reset zoom states
            resetPhotoZoom()
            
            // Force PlayerView reset
            binding.playerView?.let { playerView ->
                playerView.player = null
                (playerView as? org.iurl.litegallery.ZoomablePlayerView)?.resetZoom()
            }
            
            // Clear any pending callbacks
            binding.root.handler?.removeCallbacksAndMessages(null)
        }
        
        fun clearAllReferences() {
            android.util.Log.d("MediaViewerAdapter", "Clearing all ViewHolder references")
            
            // Clear VideoViewHolder reference completely
            videoViewHolder?.let { vh ->
                vh.releasePlayer()
                videoViewHolder = null
            }
            
            // Clear all touch listeners that might hold references
            binding.root.setOnTouchListener(null)
            binding.photoImageView.setOnTouchListener(null)
            binding.playerView?.setOnTouchListener(null)
            binding.playButton?.setOnClickListener(null)
            
            // Clear gesture listeners
            (binding.photoImageView as? org.iurl.litegallery.ZoomImageView)?.let { zoomImageView ->
                zoomImageView.setOnImageClickListener {}
                zoomImageView.setOnZoomChangeListener {}
            }
            
            (binding.playerView as? org.iurl.litegallery.ZoomablePlayerView)?.let { zoomPlayerView ->
                zoomPlayerView.setOnPlayPauseListener {}
                zoomPlayerView.setOnToggleUIListener {}
                zoomPlayerView.setOnShowUIListener {}
                zoomPlayerView.setOnHideUIListener {}
                zoomPlayerView.setOnBrightnessChangeListener {}
                zoomPlayerView.setOnVolumeChangeListener {}
                zoomPlayerView.setOnValueDisplayListener { _, _ -> }
                zoomPlayerView.setOnZoomChangeListener {}
            }
            
            // Force clear any cached bitmaps in ImageViews
            binding.photoImageView.setImageDrawable(null)
            binding.videoThumbnail?.setImageDrawable(null)
            
            android.util.Log.d("MediaViewerAdapter", "All ViewHolder references cleared")
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
