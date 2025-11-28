package org.iurl.litegallery

import android.view.View
import androidx.recyclerview.widget.RecyclerView
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import org.iurl.litegallery.databinding.ItemMediaViewerBinding

class VideoViewHolder(
    private val binding: ItemMediaViewerBinding,
    private val onMediaClick: () -> Unit
) : RecyclerView.ViewHolder(binding.root) {
    
    var exoPlayer: ExoPlayer? = null
        private set
    private var isPlayerReady = false
    private var hasBeenPlayed = false // Track if video has ever been played
    private var boundMediaItem: org.iurl.litegallery.MediaItem? = null
    private var retryCount = 0 // Track retry attempts
    private val maxRetries = 1 // Maximum number of retry attempts
    private var loadingTimeoutRunnable: Runnable? = null // Timeout checker for loading
    private val loadingTimeout = 3000L // 3 seconds timeout for player loading
    var isInvalidVideo = false // Track if video is invalid/corrupted
        private set
    
    fun bind(mediaItem: org.iurl.litegallery.MediaItem) {
        android.util.Log.d("VideoViewHolder", "=== BIND START: ${mediaItem.name} ===")
        
        // Only reset if we don't have a clean state already
        if (exoPlayer != null) {
            android.util.Log.d("VideoViewHolder", "Previous player exists - releasing it")
            releasePlayer()
        }
        boundMediaItem = mediaItem
        
        // Reset internal state
        isPlayerReady = false
        hasBeenPlayed = false
        retryCount = 0 // Reset retry counter for new media
        isInvalidVideo = false // Reset invalid flag for new media
        
        // Hide photo view, show video container
        binding.photoImageView.visibility = View.GONE
        binding.videoContainer.visibility = View.VISIBLE
        
        // Show thumbnail initially
        binding.videoThumbnail?.visibility = View.VISIBLE
        binding.playButton?.visibility = View.VISIBLE
        
        // Setup player immediately - no delay needed for TextureView
        android.util.Log.d("VideoViewHolder", "Setting up video player for: ${mediaItem.name}")
        setupVideoPlayer(mediaItem)
        
        android.util.Log.d("VideoViewHolder", "=== BIND COMPLETE: ${mediaItem.name} ===")
        
        // Set click listener
        binding.root.setOnClickListener {
            onMediaClick()
        }
        
        // Set up play button click (only for initial play)
        binding.playButton?.setOnClickListener {
            togglePlayback()
        }
    }

    fun ensurePreparedIfNeeded() {
        if (exoPlayer == null) {
            boundMediaItem?.let {
                android.util.Log.d("VideoViewHolder", "Re-preparing player after release for: ${it.name}")
                setupVideoPlayer(it)
            }
        }
    }
    
    private fun setupVideoPlayer(mediaItem: org.iurl.litegallery.MediaItem) {
        android.util.Log.d("VideoViewHolder", "=== FRESH PLAYER SETUP: ${mediaItem.name} (Retry: $retryCount/$maxRetries) ===")
        
        // Ensure we start completely fresh - no reuse of any previous state
        if (exoPlayer != null) {
            android.util.Log.w("VideoViewHolder", "Player still exists - forcing release")
            releasePlayer()
        }
        
        // TextureView setup with guaranteed fresh state
        try {
            // Check available memory before creating ExoPlayer (more lenient check)
            val runtime = Runtime.getRuntime()
            val usedMemory = runtime.totalMemory() - runtime.freeMemory()
            val maxMemory = runtime.maxMemory()
            val availableMemory = maxMemory - usedMemory
            val memoryUsagePercent = (usedMemory.toFloat() / maxMemory * 100).toInt()
            
            android.util.Log.d("VideoViewHolder", 
                "Memory check before ExoPlayer creation: $memoryUsagePercent% used, ${availableMemory / 1024 / 1024}MB available")
            
            // Only prevent player creation if memory is critically low
            if (memoryUsagePercent > 95 || availableMemory < 20 * 1024 * 1024) { // Less than 20MB available
                android.util.Log.w("VideoViewHolder", "Critically low memory for video playback - showing thumbnail only")
                binding.videoThumbnail?.visibility = View.VISIBLE
                binding.playButton?.visibility = if (hasBeenPlayed) View.GONE else View.VISIBLE
                return
            }
            
            try {
                android.util.Log.d("VideoViewHolder", "Creating BRAND NEW ExoPlayer instance for: ${mediaItem.name}")
                
                exoPlayer = ExoPlayer.Builder(binding.root.context)
                    .setLoadControl(
                        // Balanced memory optimization for large video files
                        DefaultLoadControl.Builder()
                            .setBufferDurationsMs(
                                3000,  // Min buffer: 3s (must be >= bufferForPlaybackAfterRebufferMs)
                                15000, // Max buffer: 15s (balanced approach)
                                1500,  // Buffer before playback: 1.5s
                                2500   // Buffer after rebuffer: 2.5s (must be <= minBufferMs)
                            )
                            .setTargetBufferBytes(2 * 1024 * 1024) // 2MB target buffer (more reasonable)
                            .setPrioritizeTimeOverSizeThresholds(true) // Allow larger buffers if needed for playback
                            .build()
                    )
                    .setBandwidthMeter(androidx.media3.exoplayer.upstream.DefaultBandwidthMeter.getSingletonInstance(binding.root.context))
                    // Use custom allocator with smaller allocation size
                    .setMediaSourceFactory(
                        androidx.media3.exoplayer.source.DefaultMediaSourceFactory(
                            androidx.media3.datasource.DefaultDataSource.Factory(binding.root.context),
                            androidx.media3.extractor.DefaultExtractorsFactory()
                        ).setLoadErrorHandlingPolicy(
                            androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy(2) // Reduce retry attempts
                        )
                    )
                    .build().apply {
                    
                    val videoMediaItem = if (mediaItem.path.startsWith("content://")) {
                        // Handle content URI
                        MediaItem.fromUri(android.net.Uri.parse(mediaItem.path))
                    } else {
                        // Handle file path
                        MediaItem.fromUri(mediaItem.path)
                    }
                    
                    setMediaItem(videoMediaItem)
                    playWhenReady = false // Don't auto-play to save memory
                    
                    addListener(object : Player.Listener {
                        override fun onPlaybackStateChanged(playbackState: Int) {
                            android.util.Log.d("VideoViewHolder", "Playback state changed: $playbackState")
                            when (playbackState) {
                                Player.STATE_READY -> {
                                    isPlayerReady = true
                                    retryCount = 0 // Reset retry count on successful load
                                    cancelLoadingTimeout() // Cancel timeout when player is ready
                                    binding.videoThumbnail?.visibility = View.GONE
                                    // Show play button only if video has never been played
                                    binding.playButton?.visibility = if (hasBeenPlayed) View.GONE else View.VISIBLE
                                    android.util.Log.d("VideoViewHolder", "✅ Player ready successfully")
                                }
                                Player.STATE_BUFFERING -> {
                                    // Hide play button during buffering
                                    binding.playButton?.visibility = View.GONE
                                }
                                Player.STATE_ENDED -> {
                                    seekTo(0) // Reset to beginning
                                    pause() // Ensure it's paused
                                    // Don't show play button - video has been played
                                }
                                Player.STATE_IDLE -> {
                                    // Reset UI state
                                    binding.videoThumbnail?.visibility = View.VISIBLE
                                    binding.playButton?.visibility = if (hasBeenPlayed) View.GONE else View.VISIBLE
                                }
                            }
                        }
                        
                        override fun onIsPlayingChanged(isPlaying: Boolean) {
                            updatePlayButton(isPlaying)
                            // Mark as played when video starts playing
                            if (isPlaying && !hasBeenPlayed) {
                                hasBeenPlayed = true
                                binding.playButton?.visibility = View.GONE
                            }
                        }
                        
                        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                            // Handle playback errors gracefully
                            binding.videoThumbnail?.visibility = View.VISIBLE
                            binding.playButton?.visibility = if (hasBeenPlayed) View.GONE else View.VISIBLE
                            
                            android.util.Log.e("VideoViewHolder", "Playback error: ${error.message}")
                            
                            // Check if this is an OutOfMemoryError
                            val cause = error.cause
                            if (cause is OutOfMemoryError || error.message?.contains("OutOfMemory") == true) {
                                android.util.Log.e("VideoViewHolder", "OutOfMemoryError detected - performing cleanup")
                                
                                // Basic cleanup for OOM
                                releasePlayer()
                                
                                // Show error message to user
                                android.util.Log.w("VideoViewHolder", "Video too large for available memory - showing thumbnail instead")
                                isInvalidVideo = true
                                binding.videoThumbnail?.visibility = View.VISIBLE
                                binding.playButton?.visibility = View.GONE // Hide play button for OOM videos
                                
                            } else {
                                // Try to recover by auto-retrying for other errors
                                android.util.Log.w("VideoViewHolder", "Playback error - attempting recovery: ${error.message}")
                                retryPlayerSetup()
                            }
                        }
                        
                        override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                            android.util.Log.d("VideoViewHolder", "Video size changed: ${videoSize.width}x${videoSize.height}")
                            // Update the ZoomablePlayerView with video dimensions
                            (binding.playerView as? org.iurl.litegallery.ZoomablePlayerView)?.setVideoSize(videoSize.width, videoSize.height)
                        }
                    })
                }
                
                // Attach fresh player to TextureView
                binding.playerView?.let { playerView ->
                    try {
                        android.util.Log.d("VideoViewHolder", "Attaching FRESH ExoPlayer to TextureView for: ${mediaItem.name}")
                        
                        // Ensure PlayerView is clean before attachment
                        if (playerView.player != null) {
                            android.util.Log.w("VideoViewHolder", "PlayerView still has old player attached!")
                            playerView.player = null
                        }
                        
                        playerView.player = exoPlayer
                        playerView.useController = false // We'll use custom controls
                        
                        // Prepare immediately - TextureView doesn't need complex surface management
                        exoPlayer?.prepare()
                        android.util.Log.d("VideoViewHolder", "✅ FRESH ExoPlayer prepared successfully for: ${mediaItem.name}")
                        
                    } catch (e: Exception) {
                        android.util.Log.e("VideoViewHolder", "❌ Error setting up fresh TextureView player: ${e.message}")
                        releasePlayer()
                    }
                } ?: run {
                    android.util.Log.e("VideoViewHolder", "❌ PlayerView is null!")
                }
                
                android.util.Log.d("VideoViewHolder", "=== FRESH PLAYER SETUP COMPLETE: ${mediaItem.name} ===")
                
            } catch (e: Exception) {
                android.util.Log.e("VideoViewHolder", "Error creating ExoPlayer: ${e.message}")
                // Try to retry instead of just showing thumbnail
                retryPlayerSetup()
            }
        } catch (setupError: Exception) {
            android.util.Log.e("VideoViewHolder", "Error in TextureView setup: ${setupError.message}")
            retryPlayerSetup()
        }
    }
    
    /**
     * Retry player setup if retry limit not reached
     */
    private fun retryPlayerSetup() {
        if (retryCount < maxRetries) {
            retryCount++
            android.util.Log.w("VideoViewHolder", "🔄 Attempting automatic retry $retryCount/$maxRetries...")

            // Release current player before retry
            releasePlayer()

            // Retry after a short delay to allow cleanup
            binding.root.postDelayed({
                boundMediaItem?.let { mediaItem ->
                    android.util.Log.d("VideoViewHolder", "Retrying player setup for: ${mediaItem.name}")
                    setupVideoPlayer(mediaItem)
                }
            }, 500) // 500ms delay before retry
        } else {
            android.util.Log.e("VideoViewHolder", "❌ Max retries reached - video is invalid")
            isInvalidVideo = true // Mark video as invalid
            releasePlayer()
            binding.videoThumbnail?.visibility = View.VISIBLE
            binding.playButton?.visibility = View.GONE // Hide play button for invalid videos
        }
    }

    /**
     * Manually reload the video (called from menu)
     */
    fun reloadVideo() {
        android.util.Log.d("VideoViewHolder", "🔄 Manual reload requested")

        // Reset all state
        retryCount = 0
        isInvalidVideo = false

        // Reload the video
        boundMediaItem?.let { mediaItem ->
            android.util.Log.d("VideoViewHolder", "Reloading video: ${mediaItem.name}")
            setupVideoPlayer(mediaItem)
        }
    }

    /**
     * Start a timeout check for player loading
     */
    private fun startLoadingTimeout() {
        cancelLoadingTimeout() // Cancel any existing timeout

        loadingTimeoutRunnable = Runnable {
            if (!isPlayerReady && exoPlayer != null) {
                android.util.Log.w("VideoViewHolder", "⏱️ Player loading timeout - player not ready after ${loadingTimeout}ms")
                retryPlayerSetup()
            }
        }

        binding.root.postDelayed(loadingTimeoutRunnable!!, loadingTimeout)
        android.util.Log.d("VideoViewHolder", "Started loading timeout check (${loadingTimeout}ms)")
    }

    /**
     * Cancel any pending loading timeout check
     */
    private fun cancelLoadingTimeout() {
        loadingTimeoutRunnable?.let {
            binding.root.removeCallbacks(it)
            loadingTimeoutRunnable = null
            android.util.Log.d("VideoViewHolder", "Cancelled loading timeout check")
        }
    }

    private fun togglePlayback() {
        exoPlayer?.let { player ->
            if (player.isPlaying) {
                player.pause()
            } else {
                player.play()
            }
        }
    }
    
    private fun updatePlayButton(isPlaying: Boolean) {
        // Center play button is only shown before first play
        // Once video has been played, it's never shown again
        binding.playButton?.visibility = if (hasBeenPlayed) View.GONE else View.VISIBLE
    }
    
    fun onResume() {
        exoPlayer?.playWhenReady = false
    }
    
    fun onPause() {
        exoPlayer?.pause()
    }
    
    fun getZoomablePlayerView(): org.iurl.litegallery.ZoomablePlayerView? {
        val zoomableView = binding.playerView as? org.iurl.litegallery.ZoomablePlayerView
        android.util.Log.d("VideoViewHolder", "getZoomablePlayerView: ${zoomableView != null}, player: ${exoPlayer != null}, isPlaying: ${exoPlayer?.isPlaying}")
        return zoomableView
    }
    
    private fun forceCompleteStateReset() {
        android.util.Log.d("VideoViewHolder", "Complete state reset")
        
        // Release any existing player completely
        releasePlayer()
        
        // Reset all internal state variables
        isPlayerReady = false
        hasBeenPlayed = false
        
        // Clear PlayerView completely
        binding.playerView?.let { playerView ->
            playerView.player = null
            (playerView as? org.iurl.litegallery.ZoomablePlayerView)?.resetZoom()
        }
        
        // Reset UI state
        binding.videoThumbnail?.visibility = View.VISIBLE
        binding.playButton?.visibility = View.VISIBLE
        
        // Cancel any pending operations
        binding.root.handler?.removeCallbacksAndMessages(null)
        
        android.util.Log.d("VideoViewHolder", "Complete state reset finished")
    }
    
    fun releasePlayer() {
        // Cancel any pending timeout checks
        cancelLoadingTimeout()

        exoPlayer?.let { player ->
            try {
                android.util.Log.d("VideoViewHolder", "Releasing ExoPlayer (TextureView mode)...")
                
                // Detach from PlayerView first
                binding.playerView?.player = null
                
                // Stop and release player
                try {
                    if (player.isPlaying) {
                        player.pause()
                    }
                    player.stop()
                    player.clearMediaItems()
                    player.release()
                    android.util.Log.d("VideoViewHolder", "ExoPlayer released successfully")
                } catch (e: Exception) {
                    android.util.Log.e("VideoViewHolder", "Error during player release: ${e.message}")
                }
                
            } catch (e: Exception) {
                android.util.Log.e("VideoViewHolder", "Error in release process: ${e.message}")
                // Emergency cleanup
                try {
                    binding.playerView?.player = null
                    player.release()
                } catch (emergencyError: Exception) {
                    android.util.Log.e("VideoViewHolder", "Emergency release failed: ${emergencyError.message}")
                }
            } finally {
                exoPlayer = null
                isPlayerReady = false
                
                // Reset UI state
                binding.videoThumbnail?.visibility = View.VISIBLE
                binding.playButton?.visibility = if (hasBeenPlayed) View.GONE else View.VISIBLE
                
                android.util.Log.d("VideoViewHolder", "TextureView player cleanup completed")
            }
        }
    }
}
