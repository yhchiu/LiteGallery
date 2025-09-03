package com.litegallery

import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import com.litegallery.databinding.ActivityMediaViewerBinding
import kotlinx.coroutines.launch
import java.io.File

class MediaViewerActivity : AppCompatActivity() {
    
    companion object {
        const val EXTRA_MEDIA_PATH = "extra_media_path"
        const val EXTRA_FOLDER_PATH = "extra_folder_path"
        const val EXTRA_CURRENT_POSITION = "extra_current_position"
        
        // Rename history constants
        private const val RENAME_HISTORY_PREFS = "rename_history"
        private const val RENAME_PREFIXES_KEY = "prefixes_list"
        private const val RENAME_SUFFIXES_KEY = "suffixes_list"
    }
    
    private lateinit var binding: ActivityMediaViewerBinding
    private lateinit var mediaViewerAdapter: MediaViewerAdapter
    private lateinit var mediaScanner: MediaScanner
    private var isUIVisible = false
    private var mediaItems: List<MediaItem> = emptyList()
    private var currentPosition = 0
    
    // Video control variables
    private var progressUpdateHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var progressUpdateRunnable: Runnable? = null
    private var isUserSeeking = false
    private var isZoomed = false
    private var currentColorTheme: String? = null
    private var isFrameForwardModeEnabled = false
    
    override fun onCreate(savedInstanceState: Bundle?) {
        // Apply theme and color theme before setting content view
        ThemeHelper.applyTheme(this)
        ThemeHelper.applyColorTheme(this)
        
        super.onCreate(savedInstanceState)
        binding = ActivityMediaViewerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Initialize current color theme
        currentColorTheme = ThemeHelper.getCurrentColorTheme(this)
        
        setupFullScreen()
        setupViewPager()
        setupUI()
        loadMedia()
    }
    
    override fun onResume() {
        super.onResume()
        // Apply theme in case it was changed in settings
        ThemeHelper.applyTheme(this)
        
        // Check if color theme changed and recreate if necessary
        val newColorTheme = ThemeHelper.getCurrentColorTheme(this)
        if (currentColorTheme != null && currentColorTheme != newColorTheme) {
            recreate()
            return
        }
        currentColorTheme = newColorTheme
        
        // Re-apply action bar customization in case user changed settings
        applyActionBarCustomization()
    }
    
    override fun onPause() {
        super.onPause()
        // Pause the currently tracked video
        mediaViewerAdapter.pauseAllVideos()
        // Ensure every visible video gets paused (not just the tracked one)
        pauseAllVisibleVideos()
        // Stop progress updates to avoid background handler churn and logs
        stopProgressUpdate()
    }

    override fun onStop() {
        super.onStop()
        // When activity is no longer visible, fully release to free resources
        releaseAllVisibleVideos()
        // Ensure progress updates are stopped
        stopProgressUpdate()
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // User left the app (e.g., Home key) – pause playback
        pauseAllVisibleVideos()
    }

    override fun onStart() {
        super.onStart()
        // Re-initialize players if user returns to the app (remain paused)
        prepareVisibleVideosIfNeeded()
        // Resume progress updates only if UI is visible and current item is a video
        if (isUIVisible && currentPosition < mediaItems.size && mediaItems[currentPosition].isVideo) {
            startProgressUpdate()
        }
    }

    
    override fun onDestroy() {
        super.onDestroy()
        android.util.Log.d("MediaViewerActivity", "ACTIVITY DESTROY - thorough cleanup")
        
        stopProgressUpdate()
        
        // Thorough cleanup of all resources
        mediaViewerAdapter.releaseAllPlayers()
        
        // Clear ViewPager2 adapter to help with cleanup
        binding.viewPager.adapter = null
        
        // Force aggressive garbage collection
        System.gc()
        System.runFinalization()
        System.gc() // Double GC for thorough cleanup
        
        android.util.Log.d("MediaViewerActivity", "Activity cleanup completed")
    }
    
    override fun onLowMemory() {
        super.onLowMemory()
        android.util.Log.w("MediaViewerActivity", "Low memory warning - aggressive cleanup")
        
        // Force garbage collection and release video players
        mediaViewerAdapter.releaseAllPlayers()
        
        // Force multiple GC cycles for aggressive cleanup
        System.gc()
        System.runFinalization()
        System.gc()
        
        // Clear any cached images from Glide
        com.bumptech.glide.Glide.get(this).clearMemory()
    }
    
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        android.util.Log.w("MediaViewerActivity", "Memory trim requested: level $level")
        
        when (level) {
            android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL,
            android.content.ComponentCallbacks2.TRIM_MEMORY_COMPLETE -> {
                // Critical memory situation - release everything
                mediaViewerAdapter.releaseAllPlayers()
                com.bumptech.glide.Glide.get(this).clearMemory()
                System.gc()
                System.runFinalization()
                System.gc()
            }
            android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW,
            android.content.ComponentCallbacks2.TRIM_MEMORY_MODERATE -> {
                // Moderate memory pressure - pause videos
                mediaViewerAdapter.pauseAllVideos()
                com.bumptech.glide.Glide.get(this).onTrimMemory(level)
                System.gc()
            }
        }
    }
    
    private fun setupFullScreen() {
        // Use WindowInsetsController for proper insets handling with 3-button navigation
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
            val controller = window.insetsController
            controller?.hide(android.view.WindowInsets.Type.statusBars())
            controller?.systemBarsBehavior = android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            )
        }
        
        // Handle window insets to avoid navigation bar overlap
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            binding.root.setOnApplyWindowInsetsListener { view, insets ->
                val navigationInsets = insets.getInsets(android.view.WindowInsets.Type.navigationBars())
                val statusInsets = insets.getInsets(android.view.WindowInsets.Type.statusBars())
                
                // Apply padding to bottom overlay to avoid navigation bar
                binding.bottomOverlay.setPadding(
                    binding.bottomOverlay.paddingLeft,
                    binding.bottomOverlay.paddingTop,
                    binding.bottomOverlay.paddingRight,
                    navigationInsets.bottom
                )
                
                // Apply padding to top overlay to avoid status bar
                binding.topOverlay.setPadding(
                    binding.topOverlay.paddingLeft,
                    statusInsets.top,
                    binding.topOverlay.paddingRight,
                    binding.topOverlay.paddingBottom
                )
                
                insets
            }
        } else {
            // Legacy approach for older Android versions
            @Suppress("DEPRECATION")
            binding.root.setOnApplyWindowInsetsListener { view, insets ->
                binding.bottomOverlay.setPadding(
                    binding.bottomOverlay.paddingLeft,
                    binding.bottomOverlay.paddingTop,
                    binding.bottomOverlay.paddingRight,
                    insets.systemWindowInsetBottom
                )
                
                binding.topOverlay.setPadding(
                    binding.topOverlay.paddingLeft,
                    insets.systemWindowInsetTop,
                    binding.topOverlay.paddingRight,
                    binding.topOverlay.paddingBottom
                )
                
                insets
            }
        }
    }
    
    private fun setupViewPager() {
        mediaScanner = MediaScanner(this)
        
        // Set up single tap callback that can switch between UI toggle and frame forward
        mediaViewerAdapter = MediaViewerAdapter {
            if (isFrameForwardModeEnabled && currentPosition < mediaItems.size && mediaItems[currentPosition].isVideo) {
                seekFrameForward()
            } else {
                toggleUI()
            }
        }
        
        // Set up video single-tap listener (note: method name is misleading, it's actually single-tap)
        mediaViewerAdapter.setVideoDoubleClickListener {
            if (isFrameForwardModeEnabled) {
                seekFrameForward()
            } else {
                toggleVideoPlayback()
            }
        }
        
        // Set up zoom change listener
        mediaViewerAdapter.setZoomChangeListener { zoomLevel ->
            isZoomed = zoomLevel > 1f
            setViewPagerSwipingEnabled(!isZoomed)
            updateZoomLevelDisplay(zoomLevel)
        }
        
        binding.viewPager.adapter = mediaViewerAdapter
        
        // Add page change callback to handle video transitions
        binding.viewPager.registerOnPageChangeCallback(object : androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                currentPosition = position
                updateFileName(position)
                
                // Aggressive memory management for large video files
                if (position < mediaItems.size && mediaItems[position].isVideo) {
                    // Check available memory before loading video
                    val runtime = Runtime.getRuntime()
                    val usedMemory = runtime.totalMemory() - runtime.freeMemory()
                    val maxMemory = runtime.maxMemory()
                    val availableMemory = maxMemory - usedMemory
                    val memoryUsagePercent = (usedMemory.toFloat() / maxMemory * 100).toInt()
                    
                    android.util.Log.d("MediaViewerActivity", 
                        "Memory usage: $memoryUsagePercent% ($usedMemory / $maxMemory), available: $availableMemory")
                    
                    // If memory usage is high, force cleanup before loading new video
                    // BUT avoid breaking current page references
                    if (memoryUsagePercent > 85) { // Increase threshold to be less aggressive
                        android.util.Log.w("MediaViewerActivity", "Very high memory usage, selective cleanup")
                        
                        // Only release players for non-current pages to preserve current functionality
                        // Don't use releaseAllPlayers() as it breaks zoom functionality
                        
                        com.bumptech.glide.Glide.get(this@MediaViewerActivity).clearMemory()
                        System.gc()
                        System.runFinalization()
                        System.gc()
                    }
                    
                    // Small delay to allow cleanup
                    binding.viewPager.postDelayed({
                        System.gc()
                    }, 300)
                }
            }
            
            override fun onPageScrollStateChanged(state: Int) {
                super.onPageScrollStateChanged(state)
                // Pause all videos when starting to scroll
                if (state == androidx.viewpager2.widget.ViewPager2.SCROLL_STATE_DRAGGING) {
                    mediaViewerAdapter.pauseAllVideos()
                } else if (state == androidx.viewpager2.widget.ViewPager2.SCROLL_STATE_IDLE) {
                    // Force GC after scroll completes
                    binding.viewPager.postDelayed({
                        System.gc()
                    }, 100)
                }
            }
        })
    }

    private fun pauseAllVisibleVideos() {
        val recyclerView = binding.viewPager.getChildAt(0) as? androidx.recyclerview.widget.RecyclerView ?: return
        for (i in 0 until recyclerView.childCount) {
            val child = recyclerView.getChildAt(i)
            val holder = recyclerView.getChildViewHolder(child)
            val mediaHolder = holder as? MediaViewerAdapter.MediaViewHolder
            mediaHolder?.videoViewHolder?.onPause()
        }
        // Also pause the adapter-tracked holder just in case it's not visible
        mediaViewerAdapter.getCurrentVideoHolder()?.onPause()
    }

    private fun releaseAllVisibleVideos() {
        val recyclerView = binding.viewPager.getChildAt(0) as? androidx.recyclerview.widget.RecyclerView ?: return
        for (i in 0 until recyclerView.childCount) {
            val child = recyclerView.getChildAt(i)
            val holder = recyclerView.getChildViewHolder(child)
            val mediaHolder = holder as? MediaViewerAdapter.MediaViewHolder
            mediaHolder?.videoViewHolder?.releasePlayer()
        }
        mediaViewerAdapter.getCurrentVideoHolder()?.releasePlayer()
    }

    private fun prepareVisibleVideosIfNeeded() {
        val recyclerView = binding.viewPager.getChildAt(0) as? androidx.recyclerview.widget.RecyclerView ?: return
        for (i in 0 until recyclerView.childCount) {
            val child = recyclerView.getChildAt(i)
            val holder = recyclerView.getChildViewHolder(child)
            val mediaHolder = holder as? MediaViewerAdapter.MediaViewHolder
            mediaHolder?.videoViewHolder?.ensurePreparedIfNeeded()
        }
    }
    
    private fun setupUI() {
        // Initially hide UI overlays
        hideUI()
        
        // Set up action buttons
        binding.deleteButton.setOnClickListener {
            confirmAndDeleteCurrent()
        }
        
        binding.shareButton.setOnClickListener {
            shareCurrent()
        }
        
        binding.editButton.setOnClickListener {
            editCurrent()
        }
        
        binding.renameButton.setOnClickListener {
            showRenameDialog()
        }
        
        // Rotate screen orientation
        binding.rotateButton.setOnClickListener {
            toggleScreenOrientation()
        }
        
        binding.propertiesButton.setOnClickListener {
            showPropertiesDialog()
        }

        binding.rotatePhotoButton?.setOnClickListener {
            rotatePhotoPreview()
        }

        binding.copyButton?.setOnClickListener {
            android.widget.Toast.makeText(this, "Copy: coming soon", android.widget.Toast.LENGTH_SHORT).show()
        }
        
        binding.moveButton?.setOnClickListener {
            android.widget.Toast.makeText(this, "Move: coming soon", android.widget.Toast.LENGTH_SHORT).show()
        }
        
        binding.menuButton.setOnClickListener {
            // TODO: Implement menu functionality
        }
        
        // Video controls
        setupVideoControls()
        
        binding.expandControlsButton.setOnClickListener {
            toggleAdvancedControls()
        }
        
        // Zoom button
        binding.zoomButton.setOnClickListener {
            cycleZoom()
        }

        // Build action bar per user customization
        applyActionBarCustomization()
    }

    private fun applyActionBarCustomization() {
        val prefs = getSharedPreferences("action_bar_prefs", MODE_PRIVATE)
        val defaultOrder = listOf("delete", "share", "edit", "rename", "rotate_screen", "properties", "rotate_photo", "copy", "move")
        val order = (prefs.getString("order", null)?.split(',')?.filter { it.isNotBlank() } ?: defaultOrder)
        val visible = (prefs.getString("visible", null)?.split(',')?.filter { it.isNotBlank() }?.toSet() ?: defaultOrder.toSet())

        val container = binding.actionButtonsContainer
        container.removeAllViews()

        val map = mutableMapOf<String, android.view.View?>()
        map["delete"] = binding.deleteButton
        map["share"] = binding.shareButton
        map["edit"] = binding.editButton
        map["rename"] = binding.renameButton
        map["rotate_screen"] = binding.rotateButton
        map["properties"] = binding.propertiesButton
        map["rotate_photo"] = binding.rotatePhotoButton
        map["copy"] = binding.copyButton
        map["move"] = binding.moveButton

        // Add in the specified order, respecting visibility
        order.forEach { key ->
            val v = map[key]
            if (v != null) {
                v.visibility = if (visible.contains(key)) android.view.View.VISIBLE else android.view.View.GONE
                if (v.visibility == android.view.View.VISIBLE) {
                    // Ensure proper layout params
                    if (v.parent != null) (v.parent as? android.view.ViewGroup)?.removeView(v)
                    container.addView(v)
                }
            }
        }
        // Include any remaining visible items not present in order
        map.forEach { (key, v) ->
            if (v != null && v.parent == null && visible.contains(key)) {
                container.addView(v)
            }
        }
    }

    private fun getCurrentMediaItem(): MediaItem? = mediaItems.getOrNull(currentPosition)

    private fun confirmAndDeleteCurrent() {
        val item = getCurrentMediaItem() ?: return
        android.app.AlertDialog.Builder(this)
            .setTitle(R.string.delete)
            .setMessage(R.string.delete_confirmation)
            .setPositiveButton(R.string.delete) { _, _ ->
                try {
                    val file = java.io.File(item.path)
                    val ok = file.delete()
                    if (ok) {
                        android.widget.Toast.makeText(this, R.string.success, android.widget.Toast.LENGTH_SHORT).show()
                        notifyMediaScanner(item.path, item.path)
                        // Remove from list and update
                        val newList = mediaItems.toMutableList()
                        val idx = currentPosition
                        if (idx in newList.indices) newList.removeAt(idx)
                        mediaItems = newList
                        mediaViewerAdapter.submitList(mediaItems)
                        if (currentPosition >= mediaItems.size) currentPosition = (mediaItems.size - 1).coerceAtLeast(0)
                        if (mediaItems.isNotEmpty()) updateFileName(currentPosition) else finish()
                    } else {
                        android.widget.Toast.makeText(this, R.string.error, android.widget.Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    android.util.Log.e("MediaViewerActivity", "Delete failed: ${e.message}")
                    android.widget.Toast.makeText(this, R.string.error, android.widget.Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun shareCurrent() {
        val item = getCurrentMediaItem() ?: return
        try {
            val uri = if (item.path.startsWith("content://")) android.net.Uri.parse(item.path) else android.net.Uri.fromFile(java.io.File(item.path))
            val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = item.mimeType
                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(android.content.Intent.createChooser(shareIntent, getString(R.string.share)))
        } catch (e: Exception) {
            android.util.Log.e("MediaViewerActivity", "Share failed: ${e.message}")
        }
    }

    private fun editCurrent() {
        val item = getCurrentMediaItem() ?: return
        try {
            val uri = if (item.path.startsWith("content://")) android.net.Uri.parse(item.path) else android.net.Uri.fromFile(java.io.File(item.path))
            val editIntent = android.content.Intent(android.content.Intent.ACTION_EDIT).apply {
                setDataAndType(uri, item.mimeType)
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(editIntent)
        } catch (e: Exception) {
            android.util.Log.e("MediaViewerActivity", "Edit failed: ${e.message}")
        }
    }

    private fun toggleScreenOrientation() {
        val current = requestedOrientation
        requestedOrientation = if (current == android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) {
            android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } else {
            android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
    }

    private fun rotatePhotoPreview() {
        val holder = getCurrentPhotoHolder() ?: return
        val ziv = holder.getZoomImageView() ?: return
        // Apply temporary display rotation by 90 degrees
        val currentRotation = ziv.rotation
        ziv.rotation = (currentRotation + 90f) % 360f
    }

    private fun showPropertiesDialog() {
        val item = getCurrentMediaItem() ?: return
        val file = java.io.File(item.path)
        val name = item.name
        val size = if (file.exists()) file.length() else 0L
        val date = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(file.lastModified()))
        val builder = StringBuilder()
        builder.append(getString(R.string.file_name)).append(": ").append(name).append('\n')
        builder.append(getString(R.string.file_size)).append(": ").append(formatSize(size)).append('\n')
        
        // Add dimensions if available
        if (item.width > 0 && item.height > 0) {
            builder.append("Dimensions: ").append(item.width).append(" × ").append(item.height).append('\n')
        }
        
        builder.append(getString(R.string.file_date)).append(": ").append(date).append('\n')
        builder.append(getString(R.string.file_path)).append(": ").append(item.path)
        android.app.AlertDialog.Builder(this)
            .setTitle(R.string.properties)
            .setMessage(builder.toString())
            .setPositiveButton(R.string.ok, null)
            .show()
    }

    private fun formatSize(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val z = (63 - java.lang.Long.numberOfLeadingZeros(bytes)) / 10
        return String.format(java.util.Locale.US, "%.1f %sB", bytes / (1L shl (z * 10)).toDouble(), " KMGTPE"[z])
    }
    
    private fun loadMedia() {
        // Check if this is an external intent (ACTION_VIEW)
        if (intent.action == android.content.Intent.ACTION_VIEW) {
            handleExternalIntent()
            return
        }
        
        // Handle internal app navigation
        val mediaPath = intent.getStringExtra(EXTRA_MEDIA_PATH)
        val folderPath = intent.getStringExtra(EXTRA_FOLDER_PATH)
        currentPosition = intent.getIntExtra(EXTRA_CURRENT_POSITION, 0)
        
        folderPath?.let { path ->
            lifecycleScope.launch {
                try {
                    mediaItems = mediaScanner.scanMediaInFolder(path)
                    mediaViewerAdapter.submitList(mediaItems) {
                        // Set current position after adapter is updated
                        if (currentPosition < mediaItems.size) {
                            binding.viewPager.setCurrentItem(currentPosition, false)
                            updateFileName(currentPosition)
                        }
                    }
                } catch (e: Exception) {
                    // Handle error - could show toast or error message
                }
            }
        } ?: run {
            // Single media item case
            mediaPath?.let { path ->
                handleSingleMediaFile(path)
            }
        }
    }
    
    private fun handleExternalIntent() {
        val uri = intent.data
        if (uri != null) {
            val path = getRealPathFromURI(uri)
            if (path != null) {
                handleSingleMediaFile(path)
                // Also scan the parent folder for navigation
                scanParentFolder(path)
            } else {
                // Handle content:// URI directly
                handleContentUri(uri)
            }
        }
    }
    
    private fun handleSingleMediaFile(path: String) {
        val fileName = path.substringAfterLast("/")
        binding.fileNameTextView.text = fileName
        
        // Create single item list
        val dimensions = getMediaDimensionsFromPath(path)
        val mediaItem = MediaItem(
            name = fileName,
            path = path,
            dateModified = System.currentTimeMillis(),
            size = 0,
            mimeType = getMimeTypeFromPath(path),
            width = dimensions.first,
            height = dimensions.second
        )
        mediaItems = listOf(mediaItem)
        mediaViewerAdapter.submitList(mediaItems)
    }
    
    private fun handleContentUri(uri: android.net.Uri) {
        lifecycleScope.launch {
            try {
                val fileName = getFileNameFromUri(uri) ?: "Unknown"
                binding.fileNameTextView.text = fileName
                
                val dimensions = getMediaDimensionsFromUri(uri)
                val mediaItem = MediaItem(
                    name = fileName,
                    path = uri.toString(),
                    dateModified = System.currentTimeMillis(),
                    size = 0,
                    mimeType = contentResolver.getType(uri) ?: "image/*",
                    width = dimensions.first,
                    height = dimensions.second
                )
                mediaItems = listOf(mediaItem)
                mediaViewerAdapter.submitList(mediaItems)
            } catch (e: Exception) {
                // Handle error
            }
        }
    }
    
    private fun scanParentFolder(filePath: String) {
        val parentPath = java.io.File(filePath).parent
        parentPath?.let { path ->
            lifecycleScope.launch {
                try {
                    val allItems = mediaScanner.scanMediaInFolder(path)
                    val currentIndex = allItems.indexOfFirst { it.path == filePath }
                    
                    if (allItems.isNotEmpty()) {
                        mediaItems = allItems
                        mediaViewerAdapter.submitList(mediaItems) {
                            if (currentIndex >= 0) {
                                binding.viewPager.setCurrentItem(currentIndex, false)
                                currentPosition = currentIndex
                            }
                        }
                    }
                } catch (e: Exception) {
                    // Keep single item if folder scan fails
                }
            }
        }
    }
    
    private fun getRealPathFromURI(uri: android.net.Uri): String? {
        if (uri.scheme == "file") {
            return uri.path
        }
        
        val projection = arrayOf(android.provider.MediaStore.MediaColumns.DATA)
        val cursor = contentResolver.query(uri, projection, null, null, null)
        
        return cursor?.use {
            if (it.moveToFirst()) {
                val columnIndex = it.getColumnIndexOrThrow(android.provider.MediaStore.MediaColumns.DATA)
                it.getString(columnIndex)
            } else null
        }
    }
    
    private fun getFileNameFromUri(uri: android.net.Uri): String? {
        val cursor = contentResolver.query(uri, null, null, null, null)
        return cursor?.use {
            if (it.moveToFirst()) {
                val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0) it.getString(nameIndex) else null
            } else null
        }
    }
    
    private fun getMimeTypeFromPath(path: String): String {
        val extension = path.substringAfterLast(".", "").lowercase()
        return when (extension) {
            "jpg", "jpeg", "png", "gif", "webp", "bmp" -> "image/$extension"
            "mp4", "avi", "mov", "mkv", "3gp", "webm" -> "video/$extension"
            else -> if (path.contains("video", ignoreCase = true)) "video/*" else "image/*"
        }
    }
    
    private fun updateFileName(position: Int) {
        if (position < mediaItems.size) {
            binding.fileNameTextView.text = mediaItems[position].name
            // Reset zoom level display when switching media
            updateZoomLevelDisplay(1f)
            isZoomed = false
            setViewPagerSwipingEnabled(true)
        }
    }
    
    private fun toggleUI() {
        if (isUIVisible) {
            hideUI()
        } else {
            showUI()
        }
    }
    
    private fun showUI() {
        isUIVisible = true
        binding.topOverlay.visibility = View.VISIBLE
        binding.bottomOverlay.visibility = View.VISIBLE
        
        // Show video controls if current item is a video
        if (currentPosition < mediaItems.size && mediaItems[currentPosition].isVideo) {
            binding.videoProgressBar.visibility = View.VISIBLE
            binding.videoControls.visibility = View.VISIBLE
            startProgressUpdate()
        } else {
            binding.videoProgressBar.visibility = View.GONE
            binding.videoControls.visibility = View.GONE
        }
        
        // Initialize zoom level display
        updateZoomLevelDisplay(1f)
        
        // Show system UI temporarily with proper insets handling
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            val controller = window.insetsController
            controller?.show(android.view.WindowInsets.Type.statusBars() or android.view.WindowInsets.Type.navigationBars())
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            )
        }
    }
    
    private fun hideUI() {
        isUIVisible = false
        binding.topOverlay.visibility = View.GONE
        binding.bottomOverlay.visibility = View.GONE
        binding.videoProgressBar.visibility = View.GONE
        binding.videoControls.visibility = View.GONE
        
        // Stop progress updates when UI is hidden
        stopProgressUpdate()
        
        // Hide system UI with proper insets handling
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            val controller = window.insetsController
            controller?.hide(android.view.WindowInsets.Type.statusBars())
            // Keep navigation bars visible to prevent overlap with 3-button navigation
            controller?.systemBarsBehavior = android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            )
        }
    }
    
    private fun toggleAdvancedControls() {
        val isVisible = binding.advancedControls.visibility == View.VISIBLE
        binding.advancedControls.visibility = if (isVisible) View.GONE else View.VISIBLE
    }
    
    private fun cycleZoom() {
        android.util.Log.d("MediaViewerActivity", "cycleZoom called for position: $currentPosition")
        val currentItem = mediaViewerAdapter.currentList.getOrNull(currentPosition) ?: return
        
        if (currentItem.isVideo) {
            // Prefer the visible ViewHolder at currentPosition
            android.util.Log.d("MediaViewerActivity", "Cycling zoom for video")
            val recyclerView = binding.viewPager.getChildAt(0) as? androidx.recyclerview.widget.RecyclerView
            val mediaViewHolder = recyclerView?.findViewHolderForAdapterPosition(currentPosition) as? MediaViewerAdapter.MediaViewHolder
            val visibleZoomable = mediaViewHolder?.getZoomablePlayerView()
            android.util.Log.d("MediaViewerActivity", "Visible holder: ${mediaViewHolder != null}, Zoomable: ${visibleZoomable != null}")
            if (visibleZoomable != null) {
                visibleZoomable.cycleZoom()
                android.util.Log.d("MediaViewerActivity", "Video zoom cycled on visible holder")
                return
            }

            // Fallback: use adapter-tracked holder
            val adapterVideoHolder = mediaViewerAdapter.getCurrentVideoHolder()
            val adapterZoomable = adapterVideoHolder?.getZoomablePlayerView()
            android.util.Log.d("MediaViewerActivity", "Adapter holder: ${adapterVideoHolder != null}, Zoomable: ${adapterZoomable != null}")
            if (adapterZoomable != null) {
                adapterZoomable.cycleZoom()
                android.util.Log.d("MediaViewerActivity", "Video zoom cycled on adapter holder")
                return
            }

            android.util.Log.e("MediaViewerActivity", "Failed to find ZoomablePlayerView for current video")
            
        } else {
            // Handle photo zoom
            android.util.Log.d("MediaViewerActivity", "Cycling zoom for photo")
            val currentViewHolder = getCurrentPhotoHolder()
            val zoomImageView = currentViewHolder?.getZoomImageView()
            android.util.Log.d("MediaViewerActivity", "Photo holder: ${currentViewHolder != null}, ZoomImageView: ${zoomImageView != null}")
            zoomImageView?.cycleZoom()
        }
    }
    
    private fun getCurrentPhotoHolder(): MediaViewerAdapter.MediaViewHolder? {
        val recyclerView = binding.viewPager.getChildAt(0) as? androidx.recyclerview.widget.RecyclerView
        return recyclerView?.findViewHolderForAdapterPosition(currentPosition) as? MediaViewerAdapter.MediaViewHolder
    }
    
    private fun setViewPagerSwipingEnabled(enabled: Boolean) {
        binding.viewPager.isUserInputEnabled = enabled
    }
    
    private fun updateZoomLevelDisplay(zoomLevel: Float) {
        val displayText = if (zoomLevel == zoomLevel.toInt().toFloat()) {
            "${zoomLevel.toInt()}x"
        } else {
            String.format("%.1fx", zoomLevel)
        }
        binding.zoomLevelText.text = displayText
    }
    
    private fun setupVideoControls() {
        // Play/Pause button
        binding.playPauseButton.setOnClickListener {
            toggleVideoPlayback()
        }
        
        // SeekBar listener
        binding.progressSeekBar.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val currentVideoHolder = getCurrentVideoHolder()
                    currentVideoHolder?.exoPlayer?.let { player ->
                        val seekPosition = (progress.toFloat() / 100f * player.duration).toLong()
                        binding.currentTimeText.text = formatTime(seekPosition)
                    }
                }
            }
            
            override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {
                isUserSeeking = true
            }
            
            override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {
                isUserSeeking = false
                val currentVideoHolder = getCurrentVideoHolder()
                currentVideoHolder?.exoPlayer?.let { player ->
                    val seekPosition = (seekBar!!.progress.toFloat() / 100f * player.duration).toLong()
                    player.seekTo(seekPosition)
                }
            }
        })
        
        // Frame navigation buttons
        binding.frameBackButton.setOnClickListener {
            seekFrameBackward()
        }
        
        binding.frameForwardButton.setOnClickListener {
            seekFrameForward()
        }
        
        // Frame forward mode toggle switch
        binding.frameForwardModeSwitch.setOnCheckedChangeListener { _, isChecked ->
            isFrameForwardModeEnabled = isChecked
        }
        
        // Prevent single-tap triggers on video control areas to avoid accidental activation
        setupVideoControlTouchBlocking()
    }
    
    private fun setupVideoControlTouchBlocking() {
        // Block touch events on video progress bar area to prevent accidental single-tap triggers
        binding.videoProgressBar.setOnTouchListener { _, _ ->
            // Consume the touch event to prevent it from reaching the video player
            true
        }
        
        // Block touch events on video controls area to prevent accidental single-tap triggers  
        binding.videoControls.setOnTouchListener { _, _ ->
            // Consume the touch event to prevent it from reaching the video player
            true
        }
        
        // Block touch events on advanced controls area
        binding.advancedControls.setOnTouchListener { _, _ ->
            // Consume the touch event to prevent it from reaching the video player
            true
        }
        
        // Note: Individual buttons within these containers will still receive their click events
        // because they are processed before the parent container's touch listener
    }
    
    private fun toggleVideoPlayback() {
        android.util.Log.d("MediaViewerActivity", "toggleVideoPlayback called")
        val currentVideoHolder = getCurrentVideoHolder()
        android.util.Log.d("MediaViewerActivity", "Current video holder: ${currentVideoHolder != null}")
        
        currentVideoHolder?.let { holder ->
            android.util.Log.d("MediaViewerActivity", "Video holder found, checking player: ${holder.exoPlayer != null}")
            holder.exoPlayer?.let { player ->
                android.util.Log.d("MediaViewerActivity", "Player found, isPlaying: ${player.isPlaying}")
                if (player.isPlaying) {
                    player.pause()
                    binding.playPauseButton.setImageResource(R.drawable.ic_play)
                    android.util.Log.d("MediaViewerActivity", "Video paused")
                } else {
                    player.play()
                    binding.playPauseButton.setImageResource(R.drawable.ic_pause)
                    android.util.Log.d("MediaViewerActivity", "Video playing")
                }
            } ?: run {
                android.util.Log.w("MediaViewerActivity", "No ExoPlayer found in video holder")
            }
        } ?: run {
            android.util.Log.w("MediaViewerActivity", "No current video holder found")
        }
    }
    
    private fun seekFrameBackward() {
        val currentVideoHolder = getCurrentVideoHolder()
        currentVideoHolder?.exoPlayer?.let { player ->
            // Use Media3's seekBack with custom increment (33ms for frame-by-frame)
            val frameDurationMs = 33L // ~30fps frame duration
            val newPosition = maxOf(0, player.currentPosition - frameDurationMs)
            player.seekTo(newPosition)
        }
    }
    
    private fun seekFrameForward() {
        val currentVideoHolder = getCurrentVideoHolder()
        currentVideoHolder?.exoPlayer?.let { player ->
            // Use Media3's seekForward with custom increment (33ms for frame-by-frame)
            val frameDurationMs = 33L // ~30fps frame duration
            val newPosition = minOf(player.duration, player.currentPosition + frameDurationMs)
            player.seekTo(newPosition)
        }
    }
    
    private fun getCurrentVideoHolder(): VideoViewHolder? {
        // android.util.Log.d("MediaViewerActivity", "Getting current video holder for position: $currentPosition")

        // Prefer the visible ViewHolder at currentPosition
        val recyclerView = binding.viewPager.getChildAt(0) as? androidx.recyclerview.widget.RecyclerView
        val mediaViewHolder = recyclerView?.findViewHolderForAdapterPosition(currentPosition) as? MediaViewerAdapter.MediaViewHolder
        val visibleVideoHolder = mediaViewHolder?.videoViewHolder
        if (visibleVideoHolder != null) {
            // android.util.Log.d("MediaViewerActivity", "Using visible video holder")
            return visibleVideoHolder
        }

        // Fallback: adapter-tracked holder
        val adapterVideoHolder = mediaViewerAdapter.getCurrentVideoHolder()
        if (adapterVideoHolder != null) {
            android.util.Log.d("MediaViewerActivity", "Using adapter-tracked video holder")
            return adapterVideoHolder
        }

        android.util.Log.w("MediaViewerActivity", "No current video holder found")
        return null
    }
    
    private fun startProgressUpdate() {
        stopProgressUpdate()
        progressUpdateRunnable = object : Runnable {
            override fun run() {
                updateVideoProgress()
                progressUpdateHandler.postDelayed(this, 100) // Update every 100ms
            }
        }
        progressUpdateHandler.post(progressUpdateRunnable!!)
    }
    
    private fun stopProgressUpdate() {
        progressUpdateRunnable?.let {
            progressUpdateHandler.removeCallbacks(it)
            progressUpdateRunnable = null
        }
    }
    
    private fun updateVideoProgress() {
        if (isUserSeeking) return
        
        val currentVideoHolder = getCurrentVideoHolder()
        currentVideoHolder?.exoPlayer?.let { player ->
            val currentPosition = player.currentPosition
            val duration = player.duration
            
            if (duration > 0) {
                val progress = (currentPosition.toFloat() / duration.toFloat() * 100).toInt()
                binding.progressSeekBar.progress = progress
                binding.currentTimeText.text = formatTime(currentPosition)
                binding.durationText.text = formatTime(duration)
                
                // Update play/pause button icon
                binding.playPauseButton.setImageResource(
                    if (player.isPlaying) R.drawable.ic_pause else R.drawable.ic_play
                )
            }
        }
    }
    
    private fun formatTime(timeMs: Long): String {
        val seconds = (timeMs / 1000) % 60
        val minutes = (timeMs / (1000 * 60)) % 60
        val hours = timeMs / (1000 * 60 * 60)
        
        return if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%d:%02d", minutes, seconds)
        }
    }
    
    private fun showRenameDialog() {
        if (currentPosition >= mediaItems.size) return
        
        val currentMediaItem = mediaItems[currentPosition]
        val currentFile = java.io.File(currentMediaItem.path)
        
        // Check if it's a content URI (can't rename)
        if (currentMediaItem.path.startsWith("content://")) {
            android.widget.Toast.makeText(this, "Cannot rename files opened from other apps", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        
        // Inflate custom dialog layout
        val dialogView = layoutInflater.inflate(R.layout.dialog_rename_file, null)
        val editText = dialogView.findViewById<android.widget.EditText>(R.id.renameEditText)
        val dropdownButton = dialogView.findViewById<android.widget.ImageButton>(R.id.dropdownButton)
        val addPrefixButton = dialogView.findViewById<android.widget.Button>(R.id.addPrefixButton)
        val addSuffixButton = dialogView.findViewById<android.widget.Button>(R.id.addSuffixButton)
        
        // Set current filename
        val originalName = currentFile.nameWithoutExtension
        editText.setText(originalName)
        editText.selectAll()
        
        // Get history data
        val prefixes = getRenamePrefixes()
        val suffixes = getRenameSuffixes()
        
        // Create dialog
        val dialog = android.app.AlertDialog.Builder(this)
            .setTitle(R.string.rename_file)
            .setView(dialogView)
            .setPositiveButton(R.string.ok, null) // Set null to handle manually
            .setNegativeButton(R.string.cancel, null)
            .create()
        
        // Handle dropdown button click
        dropdownButton.setOnClickListener {
            showRenameOptionsMenu(editText, prefixes, suffixes, originalName)
        }
        
        // Handle quick prefix button
        addPrefixButton.setOnClickListener {
            showPrefixSuffixDialog(editText, originalName, isPrefix = true, prefixes)
        }
        
        // Handle quick suffix button
        addSuffixButton.setOnClickListener {
            showPrefixSuffixDialog(editText, originalName, isPrefix = false, suffixes)
        }
        
        // Show dialog
        dialog.show()
        
        // Handle OK button manually to prevent auto-dismiss
        dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val newName = editText.text.toString().trim()
            if (newName.isNotEmpty() && newName != originalName) {
                analyzeRename(originalName, newName)
                performRename(currentFile, newName)
                dialog.dismiss()
            } else {
                android.widget.Toast.makeText(this, "Please enter a different name", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
        
        // Focus and show keyboard
        editText.requestFocus()
        dialog.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
    }
    
    private fun performRename(originalFile: java.io.File, newName: String) {
        lifecycleScope.launch {
            try {
                val fileExtension = originalFile.extension
                val newFileName = if (fileExtension.isNotEmpty()) "$newName.$fileExtension" else newName
                val newFile = java.io.File(originalFile.parent, newFileName)
                
                // Check if file with new name already exists
                if (newFile.exists()) {
                    android.widget.Toast.makeText(this@MediaViewerActivity, 
                        "File with name '$newFileName' already exists", 
                        android.widget.Toast.LENGTH_SHORT).show()
                    return@launch
                }
                
                // Perform rename operation
                val success = originalFile.renameTo(newFile)
                
                if (success) {
                    // Update media item
                    val updatedMediaItem = mediaItems[currentPosition].copy(
                        name = newFileName,
                        path = newFile.absolutePath
                    )
                    
                    // Update the list
                    val updatedList = mediaItems.toMutableList()
                    updatedList[currentPosition] = updatedMediaItem
                    mediaItems = updatedList
                    
                    // Update adapter
                    mediaViewerAdapter.submitList(mediaItems)
                    
                    // Update filename display
                    updateFileName(currentPosition)
                    
                    // Notify media scanner about the change
                    notifyMediaScanner(originalFile.absolutePath, newFile.absolutePath)
                    
                    android.widget.Toast.makeText(this@MediaViewerActivity, 
                        "File renamed to '$newFileName'", 
                        android.widget.Toast.LENGTH_SHORT).show()
                        
                } else {
                    android.widget.Toast.makeText(this@MediaViewerActivity, 
                        "Failed to rename file", 
                        android.widget.Toast.LENGTH_SHORT).show()
                }
                
            } catch (e: Exception) {
                android.util.Log.e("MediaViewerActivity", "Error renaming file: ${e.message}")
                android.widget.Toast.makeText(this@MediaViewerActivity, 
                    "Error renaming file: ${e.message}", 
                    android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun notifyMediaScanner(oldPath: String, newPath: String) {
        // Remove old file from media scanner
        android.media.MediaScannerConnection.scanFile(
            this,
            arrayOf(oldPath),
            null
        ) { _, _ -> }
        
        // Add new file to media scanner
        android.media.MediaScannerConnection.scanFile(
            this,
            arrayOf(newPath),
            null
        ) { _, _ -> }
    }
    
    private fun getRenamePrefixes(): MutableList<String> {
        val prefs = getSharedPreferences(RENAME_HISTORY_PREFS, android.content.Context.MODE_PRIVATE)
        val anyVal = prefs.all[RENAME_PREFIXES_KEY]
        return when (anyVal) {
            is String -> anyVal.split('\n').filter { it.isNotBlank() }.toMutableList()
            is Set<*> -> {
                val list = anyVal.filterIsInstance<String>().toMutableList()
                // Migrate to ordered String storage (newest-first maintained by addToHistory)
                prefs.edit().putString(RENAME_PREFIXES_KEY, list.joinToString("\n")).apply()
                list
            }
            else -> mutableListOf()
        }
    }
    
    private fun getRenameSuffixes(): MutableList<String> {
        val prefs = getSharedPreferences(RENAME_HISTORY_PREFS, android.content.Context.MODE_PRIVATE)
        val anyVal = prefs.all[RENAME_SUFFIXES_KEY]
        return when (anyVal) {
            is String -> anyVal.split('\n').filter { it.isNotBlank() }.toMutableList()
            is Set<*> -> {
                val list = anyVal.filterIsInstance<String>().toMutableList()
                // Migrate to ordered String storage
                prefs.edit().putString(RENAME_SUFFIXES_KEY, list.joinToString("\n")).apply()
                list
            }
            else -> mutableListOf()
        }
    }
    
    private fun saveRenamePrefixes(prefixes: List<String>) {
        val prefs = getSharedPreferences(RENAME_HISTORY_PREFS, android.content.Context.MODE_PRIVATE)
        prefs.edit().putString(RENAME_PREFIXES_KEY, prefixes.joinToString("\n")).apply()
    }
    
    private fun saveRenameSuffixes(suffixes: List<String>) {
        val prefs = getSharedPreferences(RENAME_HISTORY_PREFS, android.content.Context.MODE_PRIVATE)
        prefs.edit().putString(RENAME_SUFFIXES_KEY, suffixes.joinToString("\n")).apply()
    }
    
    private fun addToHistory(item: String, isPrefix: Boolean) {
        if (item.isBlank()) return
        
        val (currentList, saveFunction) = if (isPrefix) {
            getRenamePrefixes() to ::saveRenamePrefixes
        } else {
            getRenameSuffixes() to ::saveRenameSuffixes
        }
        
        currentList.remove(item)
        currentList.add(0, item)
        
        val maxHistorySize = getMaxHistorySize()
        if (currentList.size > maxHistorySize) {
            currentList.removeAt(currentList.size - 1)
        }
        
        saveFunction(currentList)
    }
    
    private fun getMaxHistorySize(): Int {
        val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(this)
        return prefs.getString("rename_history_count", "30")?.toIntOrNull() ?: 30
    }
    
    private fun getDefaultSortOption(): String {
        val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(this)
        return prefs.getString("rename_default_sort", "time_desc") ?: "time_desc"
    }
    
    private fun analyzeRename(originalName: String, newName: String) {
        // Detect if it's a prefix addition
        if (newName.endsWith(originalName) && newName != originalName) {
            val prefix = newName.substring(0, newName.length - originalName.length)
            if (prefix.isNotBlank()) {
                addToHistory(prefix, isPrefix = true)
            }
        }
        // Detect if it's a suffix addition
        else if (newName.startsWith(originalName) && newName != originalName) {
            val suffix = newName.substring(originalName.length)
            if (suffix.isNotBlank()) {
                addToHistory(suffix, isPrefix = false)
            }
        }
    }
    
    private fun showRenameOptionsMenu(editText: android.widget.EditText, initialPrefixes: List<String>, initialSuffixes: List<String>, originalName: String) {
        val inflater = layoutInflater
        val dialogView = inflater.inflate(R.layout.dialog_quick_rename_options, null)
        val recyclerView = dialogView.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.optionsRecyclerView)
        val sortKeySpinner = dialogView.findViewById<android.widget.Spinner>(R.id.sortKeySpinner)
        val orderAscButton = dialogView.findViewById<android.widget.ImageButton>(R.id.orderAscButton)
        val orderDescButton = dialogView.findViewById<android.widget.ImageButton>(R.id.orderDescButton)
        val closeButton = dialogView.findViewById<android.widget.Button>(R.id.closeButton)

        // Prepare adapters for spinners (Chinese labels per requirement)
        val sortKeyOptions = listOf("時間", "文字", "文字(忽略英數字)")
        // Use dropdown layout for better visibility in dialogs
        sortKeySpinner.adapter = android.widget.ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, sortKeyOptions).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        // Use default sort option from settings
        val defaultSort = getDefaultSortOption()
        val (defaultSortIndex, defaultIsDesc) = when (defaultSort) {
            "time_desc" -> 0 to true
            "time_asc" -> 0 to false
            "text_asc" -> 1 to false
            "text_desc" -> 1 to true
            "text_ignore_asc" -> 2 to false
            "text_ignore_desc" -> 2 to true
            else -> 0 to true
        }
        sortKeySpinner.setSelection(defaultSortIndex)
        var isDesc = defaultIsDesc
        fun updateOrderButtons() {
            // Highlight selected order; use alpha for simplicity
            if (isDesc) {
                orderDescButton.alpha = 1.0f
                orderAscButton.alpha = 0.5f
            } else {
                orderDescButton.alpha = 0.5f
                orderAscButton.alpha = 1.0f
            }
        }
        updateOrderButtons()

        // Backing data for RecyclerView
        data class RenameOption(val text: String, val action: ((String) -> String)?, val isPrefix: Boolean = false, val isSeparator: Boolean = false, val isHeader: Boolean = false)
        val optionsList = mutableListOf<RenameOption>()
        
        // Prepare holder for dialog so we can dismiss on item click
        var activeDialog: android.app.AlertDialog? = null
        
        // Create RecyclerView Adapter with swipe-to-delete support
        val adapter = object : androidx.recyclerview.widget.RecyclerView.Adapter<androidx.recyclerview.widget.RecyclerView.ViewHolder>() {
            override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): androidx.recyclerview.widget.RecyclerView.ViewHolder {
                val view = inflater.inflate(R.layout.item_rename_option, parent, false)
                return object : androidx.recyclerview.widget.RecyclerView.ViewHolder(view) {}
            }
            
            override fun getItemCount(): Int = optionsList.size
            
            override fun onBindViewHolder(holder: androidx.recyclerview.widget.RecyclerView.ViewHolder, position: Int) {
                val option = optionsList[position]
                val textView = holder.itemView.findViewById<android.widget.TextView>(R.id.optionText)
                textView.text = option.text
                
                // Get theme-appropriate colors
                val context = holder.itemView.context
                android.util.Log.d("RenameDialog", "Binding item $position: ${option.text}, isHeader=${option.isHeader}, isSeparator=${option.isSeparator}")
                
                // Reset any previous styling
                textView.background = null
                textView.alpha = 1.0f
                
                if (option.isHeader || option.isSeparator) {
                    // Use theme's secondary text color for headers and separators
                    val typedValue = android.util.TypedValue()
                    val resolved = context.theme.resolveAttribute(android.R.attr.textColorSecondary, typedValue, true)
                    val color = if (resolved) typedValue.data else android.graphics.Color.GRAY
                    textView.setTextColor(color)
                    textView.setTypeface(null, android.graphics.Typeface.BOLD)
                    holder.itemView.isClickable = false
                    holder.itemView.setOnClickListener(null)
                    holder.itemView.background = null
                    android.util.Log.d("RenameDialog", "Set header/separator color: $color")
                } else {
                    // Use theme's primary text color for regular items
                    val typedValue = android.util.TypedValue()
                    val resolved = context.theme.resolveAttribute(android.R.attr.textColorPrimary, typedValue, true)
                    val color = if (resolved) typedValue.data else android.graphics.Color.BLACK
                    textView.setTextColor(color)
                    textView.setTypeface(null, android.graphics.Typeface.NORMAL)
                    holder.itemView.isClickable = true
                    // Set selectable item background using TypedValue
                    val backgroundTypedValue = android.util.TypedValue()
                    context.theme.resolveAttribute(android.R.attr.selectableItemBackground, backgroundTypedValue, true)
                    holder.itemView.setBackgroundResource(backgroundTypedValue.resourceId)
                    holder.itemView.setOnClickListener {
                        option.action?.let { action ->
                            val newName = action.invoke(originalName)
                            editText.setText(newName)
                            editText.setSelection(newName.length)
                            activeDialog?.dismiss()
                        }
                    }
                    android.util.Log.d("RenameDialog", "Set regular item color: $color")
                }
            }
        }
        
        // Set up RecyclerView
        recyclerView.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)
        recyclerView.adapter = adapter

        fun buildList(sortKeyIndex: Int, orderIndex: Int) {
            optionsList.clear()

            // Always get fresh data from SharedPreferences
            val currentPrefixes = getRenamePrefixes()
            val currentSuffixes = getRenameSuffixes()

            fun sorted(list: List<String>): List<String> {
                return when (sortKeyIndex) {
                    0 -> { // 時間
                        if (orderIndex == 0) list // 降冪: newest -> oldest (stored order)
                        else list.asReversed()    // 升冪: oldest -> newest
                    }
                    1 -> { // 文字
                        val cmp = compareBy<String> { it.lowercase() }
                        val s = list.sortedWith(cmp)
                        if (orderIndex == 0) s.asReversed() else s
                    }
                    2 -> { // 文字(忽略英數字)
                        val regex = "[A-Za-z0-9]".toRegex()
                        val cmp = compareBy<String> { it.replace(regex, "").lowercase() }
                        val s = list.sortedWith(cmp)
                        if (orderIndex == 0) s.asReversed() else s
                    }
                    else -> list
                }
            }

            val sortedPrefixes = sorted(currentPrefixes)
            val sortedSuffixes = sorted(currentSuffixes)

            if (sortedPrefixes.isNotEmpty()) {
                optionsList.add(RenameOption("--- PREFIXES ---", null, isHeader = true))
                sortedPrefixes.forEach { prefix ->
                    optionsList.add(RenameOption(prefix, { name -> prefix + name }, isPrefix = true))
                }
            }

            if (sortedSuffixes.isNotEmpty()) {
                if (optionsList.isNotEmpty()) {
                    optionsList.add(RenameOption("", null, isSeparator = true))
                }
                optionsList.add(RenameOption("--- SUFFIXES ---", null, isHeader = true))
                sortedSuffixes.forEach { suffix ->
                    optionsList.add(RenameOption(suffix, { name -> name + suffix }, isPrefix = false))
                }
            }
        }

        fun refresh() {
            val orderIndex = if (isDesc) 0 else 1
            buildList(sortKeySpinner.selectedItemPosition, orderIndex)
            // Use notifyDataSetChanged to force complete refresh
            recyclerView.post {
                adapter.notifyDataSetChanged()
                // Force layout refresh
                recyclerView.invalidateItemDecorations()
            }
        }
        
        // Set up swipe-to-delete functionality
        val itemTouchHelper = androidx.recyclerview.widget.ItemTouchHelper(object : androidx.recyclerview.widget.ItemTouchHelper.SimpleCallback(0, androidx.recyclerview.widget.ItemTouchHelper.LEFT or androidx.recyclerview.widget.ItemTouchHelper.RIGHT) {
            override fun onMove(recyclerView: androidx.recyclerview.widget.RecyclerView, viewHolder: androidx.recyclerview.widget.RecyclerView.ViewHolder, target: androidx.recyclerview.widget.RecyclerView.ViewHolder): Boolean {
                return false
            }
            
            override fun getSwipeDirs(recyclerView: androidx.recyclerview.widget.RecyclerView, viewHolder: androidx.recyclerview.widget.RecyclerView.ViewHolder): Int {
                val position = viewHolder.adapterPosition
                val option = optionsList.getOrNull(position)
                // Only allow swipe on actual rename options (not headers or separators)
                return if (option != null && !option.isHeader && !option.isSeparator && option.action != null) {
                    androidx.recyclerview.widget.ItemTouchHelper.LEFT or androidx.recyclerview.widget.ItemTouchHelper.RIGHT
                } else {
                    0
                }
            }
            
            override fun onSwiped(viewHolder: androidx.recyclerview.widget.RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                val option = optionsList[position]
                
                // Show confirmation dialog
                android.app.AlertDialog.Builder(this@MediaViewerActivity)
                    .setTitle("Delete Rename Option")
                    .setMessage("Delete \"${option.text}\" from ${if (option.isPrefix) "prefixes" else "suffixes"}?")
                    .setPositiveButton("Delete") { _, _ ->
                        // Remove from the appropriate list
                        if (option.isPrefix) {
                            val currentPrefixes = getRenamePrefixes()
                            currentPrefixes.remove(option.text)
                            saveRenamePrefixes(currentPrefixes)
                        } else {
                            val currentSuffixes = getRenameSuffixes()
                            currentSuffixes.remove(option.text)
                            saveRenameSuffixes(currentSuffixes)
                        }
                        
                        // Refresh the list
                        refresh()
                    }
                    .setNegativeButton("Cancel") { _, _ ->
                        // Restore the item by refreshing the entire list to ensure proper state
                        refresh()
                    }
                    .setOnDismissListener {
                        // Restore the item if dialog is dismissed without action
                        refresh()
                    }
                    .show()
            }
        })
        itemTouchHelper.attachToRecyclerView(recyclerView)

        // Initial population
        refresh()

        // Sorting listeners
        sortKeySpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                refresh()
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }
        orderAscButton.setOnClickListener {
            isDesc = false
            updateOrderButtons()
            refresh()
        }
        orderDescButton.setOnClickListener {
            isDesc = true
            updateOrderButtons()
            refresh()
        }

        val dialog = android.app.AlertDialog.Builder(this)
            .setTitle("Quick Rename Options")
            .setView(dialogView)
            .create()

        closeButton.setOnClickListener { dialog.dismiss() }
        activeDialog = dialog
        dialog.show()
    }
    
    private fun showPrefixSuffixDialog(editText: android.widget.EditText, originalName: String, isPrefix: Boolean, existingItems: List<String>) {
        val input = android.widget.EditText(this)
        input.hint = if (isPrefix) "Enter prefix to add" else "Enter suffix to add"
        
        val dialogTitle = if (isPrefix) "Add Prefix" else "Add Suffix"
        val previewText = android.widget.TextView(this)
        previewText.textSize = 14f
        previewText.setPadding(0, 16, 0, 0)
        
        // Create container layout
        val container = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(50, 40, 50, 40)
            addView(input)
            addView(previewText)
        }
        
        // Update preview as user types
        input.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val preview = if (isPrefix) "${s}$originalName" else "$originalName$s"
                previewText.text = "Preview: $preview"
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
        
        android.app.AlertDialog.Builder(this)
            .setTitle(dialogTitle)
            .setView(container)
            .setPositiveButton(R.string.ok) { _, _ ->
                val addition = input.text.toString().trim()
                if (addition.isNotEmpty()) {
                    val newName = if (isPrefix) "$addition$originalName" else "$originalName$addition"
                    editText.setText(newName)
                    editText.setSelection(newName.length)
                    
                    // Add to history
                    addToHistory(addition, isPrefix)
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
            
        input.requestFocus()
    }
    
    private fun getMediaDimensionsFromPath(path: String): Pair<Int, Int> {
        return try {
            val isVideo = path.contains("video", ignoreCase = true) || 
                         listOf("mp4", "avi", "mov", "mkv", "3gp", "webm").any { path.endsWith(it, ignoreCase = true) }
            
            if (isVideo) {
                getVideoDimensionsFromPath(path)
            } else {
                getImageDimensionsFromPath(path)
            }
        } catch (e: Exception) {
            Pair(0, 0)
        }
    }
    
    private fun getMediaDimensionsFromUri(uri: android.net.Uri): Pair<Int, Int> {
        return try {
            val mimeType = contentResolver.getType(uri) ?: ""
            val isVideo = mimeType.startsWith("video/")
            
            if (isVideo) {
                getVideoDimensionsFromUri(uri)
            } else {
                getImageDimensionsFromUri(uri)
            }
        } catch (e: Exception) {
            Pair(0, 0)
        }
    }
    
    private fun getImageDimensionsFromPath(path: String): Pair<Int, Int> {
        val options = BitmapFactory.Options()
        options.inJustDecodeBounds = true
        BitmapFactory.decodeFile(path, options)
        return Pair(options.outWidth, options.outHeight)
    }
    
    private fun getImageDimensionsFromUri(uri: android.net.Uri): Pair<Int, Int> {
        val options = BitmapFactory.Options()
        options.inJustDecodeBounds = true
        contentResolver.openInputStream(uri)?.use { inputStream ->
            BitmapFactory.decodeStream(inputStream, null, options)
        }
        return Pair(options.outWidth, options.outHeight)
    }
    
    private fun getVideoDimensionsFromPath(path: String): Pair<Int, Int> {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(path)
            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            Pair(width, height)
        } finally {
            retriever.release()
        }
    }
    
    private fun getVideoDimensionsFromUri(uri: android.net.Uri): Pair<Int, Int> {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(this, uri)
            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            Pair(width, height)
        } finally {
            retriever.release()
        }
    }
}
