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
    }
    
    private lateinit var binding: ActivityMediaViewerBinding
    private lateinit var mediaViewerAdapter: MediaViewerAdapter
    private lateinit var mediaScanner: MediaScanner
    private var isUIVisible = false
    private var mediaItems: List<MediaItem> = emptyList()
    private var currentPosition = 0
    
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
        mediaViewerAdapter = MediaViewerAdapter {
            toggleUI()
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
        
        // Video controls (placeholder)
        binding.playPauseButton.setOnClickListener {
            // TODO: Implement play/pause for videos
        }
        
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
    
    private fun showRenameDialog() {
        if (currentPosition >= mediaItems.size) return
        
        val currentMediaItem = mediaItems[currentPosition]
        val currentFile = java.io.File(currentMediaItem.path)
        
        // Check if it's a content URI (can't rename)
        if (currentMediaItem.path.startsWith("content://")) {
            android.widget.Toast.makeText(this, "Cannot rename files opened from other apps", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        
        // Create input dialog
        val input = android.widget.EditText(this)
        input.setText(currentFile.nameWithoutExtension)
        input.selectAll()
        
        android.app.AlertDialog.Builder(this)
            .setTitle(R.string.rename_file)
            .setMessage("Enter new name:")
            .setView(input)
            .setPositiveButton(R.string.ok) { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isNotEmpty() && newName != currentFile.nameWithoutExtension) {
                    performRename(currentFile, newName)
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
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
}