package com.litegallery

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

class MediaViewerActivity : AppCompatActivity() {
    
    companion object {
        const val EXTRA_MEDIA_PATH = "extra_media_path"
        const val EXTRA_FOLDER_PATH = "extra_folder_path"
        const val EXTRA_CURRENT_POSITION = "extra_current_position"
        
        // Rename history constants
        private const val RENAME_HISTORY_PREFS = "rename_history"
        private const val RENAME_PREFIXES_KEY = "prefixes_list"
        private const val RENAME_SUFFIXES_KEY = "suffixes_list"
        private const val MAX_HISTORY_SIZE = 8
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
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMediaViewerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupFullScreen()
        setupViewPager()
        setupUI()
        loadMedia()
    }
    
    override fun onPause() {
        super.onPause()
        mediaViewerAdapter.pauseAllVideos()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        stopProgressUpdate()
        mediaViewerAdapter.releaseAllPlayers()
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
        
        // Restore original callback for single tap
        mediaViewerAdapter = MediaViewerAdapter {
            toggleUI()
        }
        
        // Set up double-tap listener for videos
        mediaViewerAdapter.setVideoDoubleClickListener {
            toggleVideoPlayback()
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
                    if (memoryUsagePercent > 75) {
                        android.util.Log.w("MediaViewerActivity", "High memory usage, forcing cleanup")
                        mediaViewerAdapter.releaseAllPlayers()
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
    
    private fun setupUI() {
        // Initially hide UI overlays
        hideUI()
        
        // Set up action buttons (placeholder implementations)
        binding.deleteButton.setOnClickListener {
            // TODO: Implement delete functionality
        }
        
        binding.shareButton.setOnClickListener {
            // TODO: Implement share functionality
        }
        
        binding.editButton.setOnClickListener {
            // TODO: Implement edit functionality
        }
        
        binding.renameButton.setOnClickListener {
            showRenameDialog()
        }
        
        binding.rotateButton.setOnClickListener {
            // TODO: Implement rotate functionality
        }
        
        binding.propertiesButton.setOnClickListener {
            // TODO: Implement properties dialog
        }
        
        binding.menuButton.setOnClickListener {
            // TODO: Implement menu functionality
        }
        
        // Video controls
        setupVideoControls()
        
        binding.expandControlsButton.setOnClickListener {
            toggleAdvancedControls()
        }
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
        val mediaItem = MediaItem(
            name = fileName,
            path = path,
            dateModified = System.currentTimeMillis(),
            size = 0,
            mimeType = getMimeTypeFromPath(path)
        )
        mediaItems = listOf(mediaItem)
        mediaViewerAdapter.submitList(mediaItems)
    }
    
    private fun handleContentUri(uri: android.net.Uri) {
        lifecycleScope.launch {
            try {
                val fileName = getFileNameFromUri(uri) ?: "Unknown"
                binding.fileNameTextView.text = fileName
                
                val mediaItem = MediaItem(
                    name = fileName,
                    path = uri.toString(),
                    dateModified = System.currentTimeMillis(),
                    size = 0,
                    mimeType = contentResolver.getType(uri) ?: "image/*"
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
    }
    
    private fun toggleVideoPlayback() {
        val currentVideoHolder = getCurrentVideoHolder()
        currentVideoHolder?.let { holder ->
            holder.exoPlayer?.let { player ->
                if (player.isPlaying) {
                    player.pause()
                    binding.playPauseButton.setImageResource(R.drawable.ic_play)
                } else {
                    player.play()
                    binding.playPauseButton.setImageResource(R.drawable.ic_pause)
                }
            }
        }
    }
    
    private fun seekFrameBackward() {
        val currentVideoHolder = getCurrentVideoHolder()
        currentVideoHolder?.exoPlayer?.let { player ->
            val frameDuration = 1000L / 30L // Assume 30fps, seek by ~33ms
            val newPosition = maxOf(0, player.currentPosition - frameDuration)
            player.seekTo(newPosition)
        }
    }
    
    private fun seekFrameForward() {
        val currentVideoHolder = getCurrentVideoHolder()
        currentVideoHolder?.exoPlayer?.let { player ->
            val frameDuration = 1000L / 30L // Assume 30fps, seek by ~33ms
            val newPosition = minOf(player.duration, player.currentPosition + frameDuration)
            player.seekTo(newPosition)
        }
    }
    
    private fun getCurrentVideoHolder(): VideoViewHolder? {
        // We need to access the current video holder from the adapter
        return mediaViewerAdapter.getCurrentVideoHolder()
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
        val prefixesSet = prefs.getStringSet(RENAME_PREFIXES_KEY, emptySet()) ?: emptySet()
        return prefixesSet.toMutableList().sortedBy { it.lowercase() }.toMutableList()
    }
    
    private fun getRenameSuffixes(): MutableList<String> {
        val prefs = getSharedPreferences(RENAME_HISTORY_PREFS, android.content.Context.MODE_PRIVATE)
        val suffixesSet = prefs.getStringSet(RENAME_SUFFIXES_KEY, emptySet()) ?: emptySet()
        return suffixesSet.toMutableList().sortedBy { it.lowercase() }.toMutableList()
    }
    
    private fun saveRenamePrefixes(prefixes: List<String>) {
        val prefs = getSharedPreferences(RENAME_HISTORY_PREFS, android.content.Context.MODE_PRIVATE)
        prefs.edit().putStringSet(RENAME_PREFIXES_KEY, prefixes.toSet()).apply()
    }
    
    private fun saveRenameSuffixes(suffixes: List<String>) {
        val prefs = getSharedPreferences(RENAME_HISTORY_PREFS, android.content.Context.MODE_PRIVATE)
        prefs.edit().putStringSet(RENAME_SUFFIXES_KEY, suffixes.toSet()).apply()
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
        
        if (currentList.size > MAX_HISTORY_SIZE) {
            currentList.removeAt(currentList.size - 1)
        }
        
        saveFunction(currentList)
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
    
    private fun showRenameOptionsMenu(editText: android.widget.EditText, prefixes: List<String>, suffixes: List<String>, originalName: String) {
        val options = mutableListOf<String>()
        
        // Add prefixes section
        if (prefixes.isNotEmpty()) {
            options.add("--- PREFIXES ---")
            prefixes.forEach { prefix ->
                options.add("+ $prefix (→ $prefix$originalName)")
            }
        }
        
        // Add suffixes section
        if (suffixes.isNotEmpty()) {
            if (options.isNotEmpty()) options.add("")
            options.add("--- SUFFIXES ---")
            suffixes.forEach { suffix ->
                options.add("+ $suffix (→ $originalName$suffix)")
            }
        }
        
        if (options.isEmpty()) {
            android.widget.Toast.makeText(this, "No rename history yet", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        
        // Show popup menu
        android.app.AlertDialog.Builder(this)
            .setTitle("Quick Rename Options")
            .setItems(options.toTypedArray()) { _, which ->
                val selectedOption = options[which]
                if (!selectedOption.startsWith("---") && selectedOption.isNotBlank()) {
                    when {
                        selectedOption.startsWith("+ ") && selectedOption.contains("(→ ") -> {
                            val newName = selectedOption.substringAfter("(→ ").substringBefore(")")
                            editText.setText(newName)
                            editText.setSelection(newName.length)
                        }
                    }
                }
            }
            .setNegativeButton("Close", null)
            .show()
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
}