package com.litegallery

import android.view.View
import androidx.recyclerview.widget.RecyclerView
import com.google.android.exoplayer2.DefaultLoadControl
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import com.litegallery.databinding.ItemMediaViewerBinding

class VideoViewHolder(
    private val binding: ItemMediaViewerBinding,
    private val onMediaClick: () -> Unit
) : RecyclerView.ViewHolder(binding.root) {
    
    var exoPlayer: ExoPlayer? = null
        private set
    private var isPlayerReady = false
    private var hasBeenPlayed = false // Track if video has ever been played
    
    fun bind(mediaItem: com.litegallery.MediaItem) {
        // Reset for new video
        hasBeenPlayed = false
        
        // Hide photo view, show video container
        binding.photoImageView.visibility = View.GONE
        binding.videoContainer.visibility = View.VISIBLE
        
        // Show play button initially for new video
        binding.playButton?.visibility = View.VISIBLE
        
        setupVideoPlayer(mediaItem)
        
        // Set click listener
        binding.root.setOnClickListener {
            onMediaClick()
        }
        
        // Set up play button click (only for initial play)
        binding.playButton?.setOnClickListener {
            togglePlayback()
        }
    }
    
    private fun setupVideoPlayer(mediaItem: com.litegallery.MediaItem) {
        android.util.Log.d("VideoViewHolder", "Setting up video player for: ${mediaItem.name}")
        
        releasePlayer()
        
        // Add longer delay to ensure surface is completely free
        binding.root.postDelayed({
            // Check available memory before creating ExoPlayer
            val runtime = Runtime.getRuntime()
            val usedMemory = runtime.totalMemory() - runtime.freeMemory()
            val maxMemory = runtime.maxMemory()
            val availableMemory = maxMemory - usedMemory
            val memoryUsagePercent = (usedMemory.toFloat() / maxMemory * 100).toInt()
            
            android.util.Log.d("VideoViewHolder", 
                "Memory check before ExoPlayer creation: $memoryUsagePercent% used, ${availableMemory / 1024 / 1024}MB available")
            
            // If memory usage is critically high, don't create player
            if (memoryUsagePercent > 85 || availableMemory < 50 * 1024 * 1024) { // Less than 50MB available
                android.util.Log.w("VideoViewHolder", "Insufficient memory for video playback - showing thumbnail only")
                binding.videoThumbnail?.visibility = View.VISIBLE
                binding.playButton?.visibility = View.VISIBLE // Show play button on error
                return@postDelayed
            }
            
            try {
                android.util.Log.d("VideoViewHolder", "Creating new ExoPlayer instance...")
                
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
                    .setBandwidthMeter(com.google.android.exoplayer2.upstream.DefaultBandwidthMeter.getSingletonInstance(binding.root.context))
                    // Use custom allocator with smaller allocation size
                    .setMediaSourceFactory(
                        com.google.android.exoplayer2.source.DefaultMediaSourceFactory(
                            com.google.android.exoplayer2.upstream.DefaultDataSource.Factory(binding.root.context),
                            com.google.android.exoplayer2.extractor.DefaultExtractorsFactory()
                        ).setLoadErrorHandlingPolicy(
                            com.google.android.exoplayer2.upstream.DefaultLoadErrorHandlingPolicy(2) // Reduce retry attempts
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
                                    binding.videoThumbnail?.visibility = View.GONE
                                    // Show play button only if video has never been played
                                    binding.playButton?.visibility = if (hasBeenPlayed) View.GONE else View.VISIBLE
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
                        
                        override fun onPlayerError(error: com.google.android.exoplayer2.PlaybackException) {
                            // Handle playback errors gracefully
                            binding.videoThumbnail?.visibility = View.VISIBLE
                            binding.playButton?.visibility = if (hasBeenPlayed) View.GONE else View.VISIBLE
                            
                            android.util.Log.e("VideoViewHolder", "Playback error: ${error.message}")
                            
                            // Check if this is an OutOfMemoryError
                            val cause = error.cause
                            if (cause is OutOfMemoryError || error.message?.contains("OutOfMemory") == true) {
                                android.util.Log.e("VideoViewHolder", "OutOfMemoryError detected - performing aggressive cleanup")
                                
                                // Emergency memory cleanup
                                releasePlayer()
                                System.gc()
                                System.runFinalization() 
                                System.gc()
                                
                                // Clear Glide cache
                                try {
                                    com.bumptech.glide.Glide.get(binding.root.context).clearMemory()
                                } catch (e: Exception) {
                                    android.util.Log.w("VideoViewHolder", "Error clearing Glide cache: ${e.message}")
                                }
                                
                                // Show error message to user
                                android.util.Log.w("VideoViewHolder", "Video too large for available memory - showing thumbnail instead")
                                binding.videoThumbnail?.visibility = View.VISIBLE
                                binding.playButton?.visibility = if (hasBeenPlayed) View.GONE else View.VISIBLE
                                
                            } else {
                                // Try to recover by releasing and recreating player for other errors
                                releasePlayer()
                            }
                        }
                        
                        override fun onVideoSizeChanged(videoSize: com.google.android.exoplayer2.video.VideoSize) {
                            android.util.Log.d("VideoViewHolder", "Video size changed: ${videoSize.width}x${videoSize.height}")
                        }
                    })
                }
                
                // Wait longer before attaching to surface for large video files
                binding.root.postDelayed({
                    try {
                        // Attach player to PlayerView, then prepare
                        binding.playerView?.let { playerView ->
                            android.util.Log.d("VideoViewHolder", "Attaching player to PlayerView for large video file")
                            
                            // Ensure surface is ready before attachment
                            if (playerView.visibility != View.VISIBLE) {
                                playerView.visibility = View.VISIBLE
                            }
                            
                            // Force layout to ensure surface is created
                            playerView.requestLayout()
                            playerView.invalidate()
                            
                            // Short delay to ensure surface is ready
                            playerView.post {
                                try {
                                    playerView.player = exoPlayer
                                    playerView.useController = false // We'll use custom controls
                                    
                                    // Prepare after surface attachment
                                    playerView.post {
                                        try {
                                            exoPlayer?.prepare()
                                            android.util.Log.d("VideoViewHolder", "ExoPlayer prepared successfully")
                                        } catch (prepareError: Exception) {
                                            android.util.Log.e("VideoViewHolder", "Error preparing ExoPlayer: ${prepareError.message}")
                                            releasePlayer()
                                        }
                                    }
                                } catch (attachError: Exception) {
                                    android.util.Log.e("VideoViewHolder", "Error attaching to surface: ${attachError.message}")
                                    releasePlayer()
                                }
                            }
                        }
                        
                    } catch (e: Exception) {
                        android.util.Log.e("VideoViewHolder", "Error in surface attachment sequence: ${e.message}")
                        releasePlayer()
                    }
                }, 200) // Increased delay for large video file surface management
                
            } catch (e: Exception) {
                android.util.Log.e("VideoViewHolder", "Error creating ExoPlayer: ${e.message}")
                // Show thumbnail and play button as fallback
                binding.videoThumbnail?.visibility = View.VISIBLE
                binding.playButton?.visibility = if (hasBeenPlayed) View.GONE else View.VISIBLE
            }
        }, 150)
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
    
    fun releasePlayer() {
        exoPlayer?.let { player ->
            try {
                android.util.Log.d("VideoViewHolder", "Releasing ExoPlayer with aggressive surface cleanup...")
                
                // Stop playback immediately and pause first
                if (player.isPlaying) {
                    player.pause()
                }
                player.stop()
                
                // Detach from PlayerView with more aggressive surface management
                binding.playerView?.let { playerView ->
                    android.util.Log.d("VideoViewHolder", "Detaching player from surface...")
                    
                    // First detach the player
                    playerView.player = null
                    
                    // Force surface destruction and recreation with longer delays
                    playerView.visibility = View.INVISIBLE
                    playerView.post {
                        // Wait longer for surface cleanup
                        playerView.postDelayed({
                            playerView.visibility = View.VISIBLE
                            // Force surface view recreation
                            playerView.invalidate()
                            playerView.requestLayout()
                        }, 200)
                    }
                }
                
                // Wait before clearing media items to ensure surface is detached
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    try {
                        player.clearMediaItems()
                        player.release()
                        android.util.Log.d("VideoViewHolder", "ExoPlayer released successfully")
                    } catch (e: Exception) {
                        android.util.Log.e("VideoViewHolder", "Error in delayed release: ${e.message}")
                    }
                }, 100)
                
            } catch (e: Exception) {
                android.util.Log.e("VideoViewHolder", "Error releasing player: ${e.message}")
                // Force release even if error occurs
                try {
                    player.release()
                } catch (releaseError: Exception) {
                    android.util.Log.e("VideoViewHolder", "Error in force release: ${releaseError.message}")
                }
            } finally {
                exoPlayer = null
                isPlayerReady = false
                // Don't reset hasBeenPlayed - it should persist for this video instance
                
                // Reset UI state
                binding.videoThumbnail?.visibility = View.VISIBLE
                binding.playButton?.visibility = if (hasBeenPlayed) View.GONE else View.VISIBLE
                
                // Force multiple layout passes to ensure surface cleanup
                binding.root.post {
                    binding.root.requestLayout()
                    binding.root.invalidate()
                }
            }
        }
    }
}