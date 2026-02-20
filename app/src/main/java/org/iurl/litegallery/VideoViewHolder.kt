package org.iurl.litegallery

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import androidx.recyclerview.widget.RecyclerView
import org.iurl.litegallery.databinding.ItemMediaViewerBinding
import java.io.File

class VideoViewHolder(
    private val binding: ItemMediaViewerBinding,
    private val onMediaClick: () -> Unit,
    private val onPlayPauseClick: () -> Unit
) : RecyclerView.ViewHolder(binding.root) {

    var exoPlayer: ExoPlayer? = null
        private set

    private var boundMediaItem: org.iurl.litegallery.MediaItem? = null
    private var hasBeenPlayed = false
    private var isPlayerReady = false
    private var isActive = false
    var isInvalidVideo = false
        private set

    fun bind(mediaItem: org.iurl.litegallery.MediaItem, shouldActivate: Boolean) {
        boundMediaItem = mediaItem
        isActive = shouldActivate

        binding.photoImageView.visibility = View.GONE
        binding.videoContainer.visibility = View.VISIBLE
        binding.videoThumbnail?.visibility = View.VISIBLE
        binding.playButton?.visibility = if (hasBeenPlayed) View.GONE else View.VISIBLE

        binding.root.setOnClickListener {
            onMediaClick()
        }
        binding.playButton?.setOnClickListener {
            onPlayPauseClick()
        }

        if (shouldActivate) {
            activateSharedPlayer()
        } else {
            deactivateSharedPlayer()
        }
    }

    fun setVideoActive(shouldActivate: Boolean) {
        isActive = shouldActivate
        if (shouldActivate) {
            activateSharedPlayer()
        } else {
            deactivateSharedPlayer()
        }
    }

    fun ensurePreparedIfNeeded() {
        if (!isActive) return
        if (exoPlayer == null) {
            activateSharedPlayer()
        }
    }

    fun isPlayerAvailable(): Boolean {
        return exoPlayer != null && !isInvalidVideo
    }

    fun reloadVideo() {
        retryCount = 0
        isInvalidVideo = false
        hasBeenPlayed = false
        PlaybackDiagnostics.recordManualReload(binding.root.context, boundMediaItem?.path)
        if (!isActive) {
            isActive = true
            activateSharedPlayer()
            return
        }
        prepareCurrentMedia(force = true)
    }

    fun onPause() {
        if (activeOwner === this) {
            sharedPlayer?.pause()
        }
    }

    fun onResume() {
        if (activeOwner === this) {
            sharedPlayer?.playWhenReady = false
        }
    }

    fun releasePlayer() {
        deactivateSharedPlayer()
    }

    fun getZoomablePlayerView(): org.iurl.litegallery.ZoomablePlayerView? {
        return binding.playerView as? org.iurl.litegallery.ZoomablePlayerView
    }

    private fun activateSharedPlayer() {
        val mediaItem = boundMediaItem ?: return
        val player = ensureSharedPlayer(binding.root.context)

        if (activeOwner !== this) {
            cancelLoadingTimeout()
            activeOwner?.detachPlayerViewOnly()
            activeOwner?.exoPlayer = null
            activeOwner = this
            retryCount = 0
        }

        attachPlayer(player)

        if (activePath != mediaItem.path || player.currentMediaItem == null || isInvalidVideo) {
            hasBeenPlayed = false
            isInvalidVideo = false
            prepareCurrentMedia(force = true)
            return
        }

        hasBeenPlayed = player.currentPosition > 0L
        isPlayerReady = player.playbackState == Player.STATE_READY
        if (isPlayerReady) {
            showReadyUi()
        } else {
            showLoadingUi()
            scheduleLoadingTimeout()
        }
    }

    private fun deactivateSharedPlayer() {
        if (activeOwner === this) {
            sharedPlayer?.pause()
            cancelLoadingTimeout()
            detachPlayerViewOnly()
            activeOwner = null
        }
        exoPlayer = null
    }

    private fun attachPlayer(player: ExoPlayer) {
        if (binding.playerView.player !== player) {
            binding.playerView.player = player
        }
        binding.playerView.useController = false
        exoPlayer = player
    }

    private fun detachPlayerViewOnly() {
        if (binding.playerView.player != null) {
            binding.playerView.player = null
        }
    }

    private fun prepareCurrentMedia(force: Boolean) {
        val item = boundMediaItem ?: return
        val player = sharedPlayer ?: return
        if (!isActive || activeOwner !== this) return

        if (!force && activePath == item.path && player.currentMediaItem != null) {
            return
        }

        isPlayerReady = false
        showLoadingUi()

        val mediaUri = if (item.path.startsWith("content://")) {
            Uri.parse(item.path)
        } else {
            Uri.fromFile(File(item.path))
        }

        player.setMediaItem(MediaItem.fromUri(mediaUri), true)
        player.prepare()
        activePath = item.path
        scheduleLoadingTimeout()
    }

    private fun showLoadingUi() {
        binding.videoThumbnail?.visibility = View.VISIBLE
        binding.playButton?.visibility = if (hasBeenPlayed) View.GONE else View.VISIBLE
    }

    private fun showReadyUi() {
        binding.videoThumbnail?.visibility = View.GONE
        binding.playButton?.visibility = if (hasBeenPlayed) View.GONE else View.VISIBLE
    }

    private fun showInvalidUi() {
        binding.videoThumbnail?.visibility = View.VISIBLE
        binding.playButton?.visibility = View.GONE
    }

    private fun onSharedPlaybackStateChanged(playbackState: Int) {
        if (activeOwner !== this) return

        when (playbackState) {
            Player.STATE_READY -> {
                isPlayerReady = true
                retryCount = 0
                cancelLoadingTimeout()
                showReadyUi()
            }

            Player.STATE_BUFFERING -> {
                showLoadingUi()
            }

            Player.STATE_ENDED -> {
                exoPlayer?.seekTo(0)
                exoPlayer?.pause()
            }

            Player.STATE_IDLE -> {
                showLoadingUi()
            }
        }
    }

    private fun onSharedIsPlayingChanged(isPlaying: Boolean) {
        if (activeOwner !== this) return

        if (isPlaying && !hasBeenPlayed) {
            hasBeenPlayed = true
            binding.playButton?.visibility = View.GONE
        }

        if (!isPlaying && isPlayerReady && !hasBeenPlayed) {
            binding.playButton?.visibility = View.VISIBLE
        }
    }

    private fun onSharedPlayerError(error: PlaybackException) {
        if (activeOwner !== this) return

        PlaybackDiagnostics.recordPlaybackError(
            context = binding.root.context,
            mediaPath = boundMediaItem?.path,
            errorCodeName = error.errorCodeName,
            errorMessage = error.message,
            retryCount = retryCount
        )

        android.util.Log.e(
            "VideoViewHolder",
            "Playback error: ${error.errorCodeName}, ${error.message}"
        )

        retryPrepare("player_error")
    }

    private fun onSharedVideoSizeChanged(videoSize: VideoSize) {
        if (activeOwner !== this) return
        getZoomablePlayerView()?.setVideoSize(videoSize.width, videoSize.height)
    }

    private fun retryPrepare(reason: String) {
        if (!isActive || activeOwner !== this) return

        if (retryCount >= MAX_RETRIES) {
            isInvalidVideo = true
            PlaybackDiagnostics.recordMarkedInvalid(
                context = binding.root.context,
                mediaPath = boundMediaItem?.path,
                reason = reason,
                retryCount = retryCount
            )
            showInvalidUi()
            android.util.Log.e("VideoViewHolder", "Marking video invalid after retries: $reason")
            return
        }

        retryCount++
        PlaybackDiagnostics.recordRetry(
            context = binding.root.context,
            mediaPath = boundMediaItem?.path,
            reason = reason,
            retryCount = retryCount,
            maxRetries = MAX_RETRIES
        )
        android.util.Log.w("VideoViewHolder", "Retrying playback ($retryCount/$MAX_RETRIES): $reason")
        prepareCurrentMedia(force = true)
    }

    companion object {
        private var sharedPlayer: ExoPlayer? = null
        private var sharedPlayerListener: Player.Listener? = null
        private var activeOwner: VideoViewHolder? = null
        private var activePath: String? = null
        private var retryCount = 0

        private const val MAX_RETRIES = 2
        private const val LOADING_TIMEOUT_MS = 8000L

        private val mainHandler = Handler(Looper.getMainLooper())
        private var loadingTimeoutRunnable: Runnable? = null

        @Synchronized
        private fun ensureSharedPlayer(context: Context): ExoPlayer {
            val existing = sharedPlayer
            if (existing != null) return existing

            val player = ExoPlayer.Builder(context.applicationContext).build()
            val listener = object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    activeOwner?.onSharedPlaybackStateChanged(playbackState)
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    activeOwner?.onSharedIsPlayingChanged(isPlaying)
                }

                override fun onPlayerError(error: PlaybackException) {
                    activeOwner?.onSharedPlayerError(error)
                }

                override fun onVideoSizeChanged(videoSize: VideoSize) {
                    activeOwner?.onSharedVideoSizeChanged(videoSize)
                }
            }
            player.addListener(listener)

            sharedPlayerListener = listener
            sharedPlayer = player
            return player
        }

        private fun scheduleLoadingTimeout() {
            cancelLoadingTimeout()
            loadingTimeoutRunnable = Runnable {
                val owner = activeOwner ?: return@Runnable
                val player = sharedPlayer ?: return@Runnable
                if (owner.isPlayerReady || player.playbackState == Player.STATE_READY) {
                    return@Runnable
                }
                PlaybackDiagnostics.recordLoadingTimeout(
                    context = owner.binding.root.context,
                    mediaPath = owner.boundMediaItem?.path,
                    playbackState = player.playbackState,
                    retryCount = retryCount,
                    maxRetries = MAX_RETRIES
                )
                owner.retryPrepare("loading_timeout")
            }
            mainHandler.postDelayed(loadingTimeoutRunnable!!, LOADING_TIMEOUT_MS)
        }

        private fun cancelLoadingTimeout() {
            loadingTimeoutRunnable?.let {
                mainHandler.removeCallbacks(it)
                loadingTimeoutRunnable = null
            }
        }

        @JvmStatic
        fun releaseSharedPlayer() {
            cancelLoadingTimeout()

            activeOwner?.detachPlayerViewOnly()
            activeOwner?.exoPlayer = null
            activeOwner = null
            activePath = null
            retryCount = 0

            sharedPlayer?.let { player ->
                sharedPlayerListener?.let { listener ->
                    player.removeListener(listener)
                }
                player.release()
            }

            sharedPlayer = null
            sharedPlayerListener = null
        }
    }
}
