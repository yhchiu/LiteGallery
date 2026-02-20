package org.iurl.litegallery

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.iurl.litegallery.databinding.ActivityTrashBinBinding
import java.io.File

class TrashBinActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityTrashBinBinding
    private lateinit var mediaAdapter: MediaAdapter
    private var currentColorTheme: String? = null
    private var trashItems: List<MediaItem> = emptyList()
    private val selectedPaths = mutableSetOf<String>()
    private var previousSelectionPaths: Set<String> = emptySet()
    private var isSelectionMode = false
    private var hasMediaCollectionChanged = false

    private val imageExtensions = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif")
    private val videoExtensions = setOf("mp4", "avi", "mov", "mkv", "3gp", "webm", "m4v", "flv")
    
    override fun onCreate(savedInstanceState: Bundle?) {
        // Apply theme and color theme before setting content view
        ThemeHelper.applyTheme(this)
        ThemeHelper.applyColorTheme(this)
        
        super.onCreate(savedInstanceState)
        binding = ActivityTrashBinBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Handle window insets for status/navigation bars.
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
        }
        binding.root.setOnApplyWindowInsetsListener { view, insets ->
            val navigationInsets = insets.getInsets(android.view.WindowInsets.Type.navigationBars())
            val statusInsets = insets.getInsets(android.view.WindowInsets.Type.statusBars())
            view.setPadding(0, statusInsets.top, 0, navigationInsets.bottom)
            insets
        }
        
        // Initialize current color theme
        currentColorTheme = ThemeHelper.getCurrentColorTheme(this)
        
        setupToolbar()
        setupRecyclerView()
        setupSelectionActionBar()
        setupBackHandler()
        loadTrashItems()
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

        loadTrashItems()
    }

    override fun finish() {
        if (hasMediaCollectionChanged) {
            setResult(
                RESULT_OK,
                Intent().apply {
                    putExtra(MediaViewerActivity.RESULT_MEDIA_CHANGED, true)
                }
            )
        }
        super.finish()
    }
    
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.trash_bin_menu, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        val hasItems = trashItems.isNotEmpty()
        val restoreItem = menu.findItem(R.id.action_restore_all)
        val deleteItem = menu.findItem(R.id.action_empty_trash)
        val selectAllItem = menu.findItem(R.id.action_select_all)

        if (isSelectionMode) {
            restoreItem?.isVisible = false
            deleteItem?.isVisible = false
            selectAllItem?.isVisible = true
            selectAllItem?.isEnabled = hasItems && selectedPaths.size < trashItems.size
        } else {
            restoreItem?.isVisible = true
            deleteItem?.isVisible = true
            restoreItem?.title = getString(R.string.restore_all)
            deleteItem?.title = getString(R.string.empty_trash)
            restoreItem?.isEnabled = hasItems
            deleteItem?.isEnabled = hasItems
            selectAllItem?.isVisible = false
        }
        return super.onPrepareOptionsMenu(menu)
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                if (isSelectionMode) {
                    exitSelectionMode()
                } else {
                    finish()
                }
                true
            }
            R.id.action_empty_trash -> {
                if (isSelectionMode) {
                    confirmAndDeleteSelected()
                } else {
                    confirmAndEmptyTrash()
                }
                true
            }
            R.id.action_restore_all -> {
                if (isSelectionMode) {
                    restoreSelectedItems()
                } else {
                    restoreAllItems()
                }
                true
            }
            R.id.action_select_all -> {
                selectAllItems()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    
    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
            title = getString(R.string.trash_bin_title)
        }
    }
    
    private fun setupRecyclerView() {
        mediaAdapter = MediaAdapter(
            onMediaClick = { mediaItem, _ ->
                if (isSelectionMode) {
                    toggleSelection(mediaItem.path)
                } else {
                    showTrashItemActions(mediaItem)
                }
            },
            onMediaLongClick = { mediaItem, _ ->
                if (!isSelectionMode) {
                    enterSelectionMode()
                }
                toggleSelection(mediaItem.path)
            },
            isItemSelected = { mediaItem ->
                selectedPaths.contains(mediaItem.path)
            }
        )
        mediaAdapter.viewMode = MediaAdapter.ViewMode.GRID
        
        binding.recyclerView.apply {
            adapter = mediaAdapter
            layoutManager = GridLayoutManager(this@TrashBinActivity, 3)
        }
    }

    private fun setupSelectionActionBar() {
        binding.restoreSelectedButton.setOnClickListener {
            restoreSelectedItems()
        }
        binding.deleteSelectedButton.setOnClickListener {
            confirmAndDeleteSelected()
        }
        binding.clearSelectionButton.setOnClickListener {
            clearSelection()
        }
    }
    
    private fun loadTrashItems() {
        lifecycleScope.launch {
            val (reindexedCount, cleanupResult, items) = withContext(Dispatchers.IO) {
                val reindexed = TrashBinStore.reindexOrphanTrashedFiles(this@TrashBinActivity)
                val cleanup = TrashBinStore.cleanupExpiredTrash(this@TrashBinActivity)
                Triple(reindexed, cleanup, scanTrashItems())
            }

            if (reindexedCount > 0) {
                android.widget.Toast.makeText(
                    this@TrashBinActivity,
                    getString(R.string.trash_reindexed_count, reindexedCount),
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }

            if (cleanupResult.removedPaths.isNotEmpty()) {
                cleanupResult.removedPaths.forEach { oldPath ->
                    notifyMediaScanner(oldPath, null)
                }
                android.widget.Toast.makeText(
                    this@TrashBinActivity,
                    getString(R.string.trash_auto_cleaned_count, cleanupResult.removedPaths.size),
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }

            trashItems = items
            selectedPaths.retainAll(items.map { it.path }.toSet())
            if (isSelectionMode && selectedPaths.isEmpty()) {
                isSelectionMode = false
            }

            mediaAdapter.submitList(items) {
                if (items.isEmpty()) {
                    showEmptyState()
                } else {
                    showListState()
                }
                updateSelectionUi()
                invalidateOptionsMenu()
            }
        }
    }

    private fun scanTrashItems(): List<MediaItem> {
        val persistedPaths = TrashBinStore.getTrashedPaths(this)
        if (persistedPaths.isEmpty()) return emptyList()

        val stalePaths = mutableListOf<String>()
        val items = mutableListOf<MediaItem>()

        persistedPaths.forEach { path ->
            val file = File(path)
            if (!file.exists() || !file.isFile || !file.name.startsWith(TrashBinStore.TRASH_FILE_PREFIX)) {
                stalePaths.add(path)
                return@forEach
            }

            val mediaItem = createMediaItemFromFile(file)
            if (mediaItem == null) {
                stalePaths.add(path)
                return@forEach
            }

            items.add(mediaItem)
        }

        if (stalePaths.isNotEmpty()) {
            TrashBinStore.removeTrashedPaths(this, stalePaths)
        }

        return items.sortedByDescending { it.dateModified }
    }

    private fun createMediaItemFromFile(file: File): MediaItem? {
        val extension = file.extension.lowercase()
        val isVideo = videoExtensions.contains(extension)
        val isImage = imageExtensions.contains(extension)
        if (!isVideo && !isImage) return null

        return MediaItem(
            name = file.name,
            path = file.absolutePath,
            dateModified = file.lastModified(),
            size = file.length(),
            mimeType = getMimeTypeFromExtension(extension, isVideo),
            duration = 0L
        )
    }

    private fun getMimeTypeFromExtension(extension: String, isVideo: Boolean): String {
        return if (isVideo) {
            when (extension) {
                "mp4" -> "video/mp4"
                "avi" -> "video/x-msvideo"
                "mov" -> "video/quicktime"
                "mkv" -> "video/x-matroska"
                "3gp" -> "video/3gpp"
                "webm" -> "video/webm"
                else -> "video/*"
            }
        } else {
            when (extension) {
                "jpg", "jpeg" -> "image/jpeg"
                "png" -> "image/png"
                "gif" -> "image/gif"
                "webp" -> "image/webp"
                "bmp" -> "image/bmp"
                "heic" -> "image/heic"
                "heif" -> "image/heif"
                else -> "image/*"
            }
        }
    }

    private fun showTrashItemActions(mediaItem: MediaItem) {
        if (isSelectionMode) {
            toggleSelection(mediaItem.path)
            return
        }

        val actions = arrayOf(
            getString(R.string.restore),
            getString(R.string.delete_permanently)
        )

        android.app.AlertDialog.Builder(this)
            .setTitle(mediaItem.name)
            .setItems(actions) { _, which ->
                when (which) {
                    0 -> confirmRestoreItem(mediaItem)
                    1 -> confirmDeletePermanently(mediaItem)
                }
            }
            .show()
    }

    private fun confirmRestoreItem(mediaItem: MediaItem) {
        val trashedFile = File(mediaItem.path)
        val parent = trashedFile.parentFile
        if (!trashedFile.exists() || parent == null) {
            TrashBinStore.removeTrashedPath(this, mediaItem.path)
            loadTrashItems()
            return
        }

        val originalName = TrashBinStore.resolveOriginalName(this, trashedFile)
        val targetFile = File(parent, originalName)

        if (!targetFile.exists()) {
            restoreSingleItem(trashedFile, targetFile, overwriteExisting = false)
            return
        }

        android.app.AlertDialog.Builder(this)
            .setTitle(R.string.file_conflict_title)
            .setMessage(R.string.file_conflict_message)
            .setPositiveButton(R.string.overwrite) { _, _ ->
                restoreSingleItem(trashedFile, targetFile, overwriteExisting = true)
            }
            .setNeutralButton(R.string.use_new_name) { _, _ ->
                val nonConflictFile = findAvailableFileName(parent, originalName)
                restoreSingleItem(trashedFile, nonConflictFile, overwriteExisting = false)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun restoreSingleItem(trashedFile: File, targetFile: File, overwriteExisting: Boolean) {
        lifecycleScope.launch {
            val oldPath = trashedFile.absolutePath
            val success = withContext(Dispatchers.IO) {
                if (!trashedFile.exists()) return@withContext false
                if (overwriteExisting && targetFile.exists() && !targetFile.delete()) return@withContext false
                if (targetFile.exists()) return@withContext false
                trashedFile.renameTo(targetFile)
            }

            if (success) {
                TrashBinStore.removeTrashedPath(this@TrashBinActivity, oldPath)
                notifyMediaScanner(oldPath, targetFile.absolutePath)
                hasMediaCollectionChanged = true
                android.widget.Toast.makeText(this@TrashBinActivity, R.string.success, android.widget.Toast.LENGTH_SHORT).show()
                loadTrashItems()
            } else {
                android.widget.Toast.makeText(this@TrashBinActivity, R.string.error, android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun confirmDeletePermanently(mediaItem: MediaItem) {
        android.app.AlertDialog.Builder(this)
            .setTitle(R.string.delete_permanently)
            .setMessage(R.string.delete_confirmation)
            .setPositiveButton(R.string.delete) { _, _ ->
                deleteSingleTrashedItem(mediaItem)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun deleteSingleTrashedItem(mediaItem: MediaItem) {
        lifecycleScope.launch {
            val path = mediaItem.path
            val deleted = withContext(Dispatchers.IO) {
                val file = File(path)
                !file.exists() || file.delete()
            }

            if (deleted) {
                TrashBinStore.removeTrashedPath(this@TrashBinActivity, path)
                notifyMediaScanner(path, null)
                android.widget.Toast.makeText(this@TrashBinActivity, R.string.success, android.widget.Toast.LENGTH_SHORT).show()
                loadTrashItems()
            } else {
                android.widget.Toast.makeText(this@TrashBinActivity, R.string.error, android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun restoreAllItems() {
        if (trashItems.isEmpty()) return

        lifecycleScope.launch {
            val snapshot = trashItems.map { it.path }
            val result = withContext(Dispatchers.IO) {
                restoreAllItemsInternal(snapshot)
            }

            if (result.removedPaths.isNotEmpty()) {
                TrashBinStore.removeTrashedPaths(this@TrashBinActivity, result.removedPaths)
            }
            result.scannerUpdates.forEach { update ->
                notifyMediaScanner(update.oldPath, update.newPath)
            }
            if (result.scannerUpdates.isNotEmpty()) {
                hasMediaCollectionChanged = true
            }

            val message = if (result.failedCount == 0) R.string.success else R.string.error
            android.widget.Toast.makeText(this@TrashBinActivity, message, android.widget.Toast.LENGTH_SHORT).show()
            loadTrashItems()
        }
    }

    private fun restoreSelectedItems() {
        val targetPaths = selectedPaths.toList()
        if (targetPaths.isEmpty()) return

        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                restoreAllItemsInternal(targetPaths)
            }

            if (result.removedPaths.isNotEmpty()) {
                TrashBinStore.removeTrashedPaths(this@TrashBinActivity, result.removedPaths)
            }
            result.scannerUpdates.forEach { update ->
                notifyMediaScanner(update.oldPath, update.newPath)
            }
            if (result.scannerUpdates.isNotEmpty()) {
                hasMediaCollectionChanged = true
            }

            val message = if (result.failedCount == 0) R.string.success else R.string.error
            android.widget.Toast.makeText(this@TrashBinActivity, message, android.widget.Toast.LENGTH_SHORT).show()
            exitSelectionMode()
            loadTrashItems()
        }
    }

    private fun confirmAndEmptyTrash() {
        if (trashItems.isEmpty()) return

        android.app.AlertDialog.Builder(this)
            .setTitle(R.string.empty_trash)
            .setMessage(R.string.empty_trash_confirmation)
            .setPositiveButton(R.string.empty_trash) { _, _ ->
                emptyTrash()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun emptyTrash() {
        lifecycleScope.launch {
            val snapshot = trashItems.map { it.path }
            val result = withContext(Dispatchers.IO) {
                emptyTrashInternal(snapshot)
            }

            if (result.removedPaths.isNotEmpty()) {
                TrashBinStore.removeTrashedPaths(this@TrashBinActivity, result.removedPaths)
                result.removedPaths.forEach { oldPath ->
                    notifyMediaScanner(oldPath, null)
                }
            }

            val message = if (result.failedCount == 0) R.string.success else R.string.error
            android.widget.Toast.makeText(this@TrashBinActivity, message, android.widget.Toast.LENGTH_SHORT).show()
            loadTrashItems()
        }
    }

    private fun confirmAndDeleteSelected() {
        if (selectedPaths.isEmpty()) return

        android.app.AlertDialog.Builder(this)
            .setTitle(R.string.delete_selected_permanently)
            .setMessage(getString(R.string.delete_selected_confirmation, selectedPaths.size))
            .setPositiveButton(R.string.delete) { _, _ ->
                deleteSelectedItems()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun deleteSelectedItems() {
        val targetPaths = selectedPaths.toList()
        if (targetPaths.isEmpty()) return

        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                emptyTrashInternal(targetPaths)
            }

            if (result.removedPaths.isNotEmpty()) {
                TrashBinStore.removeTrashedPaths(this@TrashBinActivity, result.removedPaths)
                result.removedPaths.forEach { oldPath ->
                    notifyMediaScanner(oldPath, null)
                }
            }

            val message = if (result.failedCount == 0) R.string.success else R.string.error
            android.widget.Toast.makeText(this@TrashBinActivity, message, android.widget.Toast.LENGTH_SHORT).show()
            exitSelectionMode()
            loadTrashItems()
        }
    }

    private fun restoreAllItemsInternal(paths: List<String>): BulkResult {
        val removedPaths = mutableListOf<String>()
        val scannerUpdates = mutableListOf<ScannerUpdate>()
        var failedCount = 0

        paths.forEach { oldPath ->
            val trashedFile = File(oldPath)
            if (!trashedFile.exists()) {
                removedPaths.add(oldPath)
                return@forEach
            }

            val parent = trashedFile.parentFile
            if (parent == null) {
                failedCount++
                return@forEach
            }

            val originalName = TrashBinStore.resolveOriginalName(this, trashedFile)
            val target = if (File(parent, originalName).exists()) {
                findAvailableFileName(parent, originalName)
            } else {
                File(parent, originalName)
            }

            if (trashedFile.renameTo(target)) {
                removedPaths.add(oldPath)
                scannerUpdates.add(ScannerUpdate(oldPath, target.absolutePath))
            } else {
                failedCount++
            }
        }

        return BulkResult(
            removedPaths = removedPaths,
            scannerUpdates = scannerUpdates,
            failedCount = failedCount
        )
    }

    private fun emptyTrashInternal(paths: List<String>): DeleteResult {
        val removedPaths = mutableListOf<String>()
        var failedCount = 0

        paths.forEach { path ->
            val file = File(path)
            when {
                !file.exists() -> removedPaths.add(path)
                file.delete() -> removedPaths.add(path)
                else -> failedCount++
            }
        }

        return DeleteResult(
            removedPaths = removedPaths,
            failedCount = failedCount
        )
    }

    private fun findAvailableFileName(parent: File, originalName: String): File {
        val dotIndex = originalName.lastIndexOf('.')
        val hasExtension = dotIndex > 0 && dotIndex < originalName.length - 1
        val baseName = if (hasExtension) originalName.substring(0, dotIndex) else originalName
        val extension = if (hasExtension) originalName.substring(dotIndex) else ""

        for (index in 1..9999) {
            val candidate = File(parent, "$baseName ($index)$extension")
            if (!candidate.exists()) return candidate
        }

        return File(parent, "$baseName (restored)$extension")
    }

    private fun notifyMediaScanner(oldPath: String, newPath: String?) {
        android.media.MediaScannerConnection.scanFile(
            this,
            arrayOf(oldPath),
            null
        ) { _, _ -> }

        if (!newPath.isNullOrBlank() && newPath != oldPath) {
            android.media.MediaScannerConnection.scanFile(
                this,
                arrayOf(newPath),
                null
            ) { _, _ -> }
        }
    }

    private fun showListState() {
        binding.recyclerView.visibility = View.VISIBLE
        binding.emptyView.visibility = View.GONE
    }
    
    private fun showEmptyState() {
        exitSelectionMode()
        binding.recyclerView.visibility = View.GONE
        binding.emptyView.visibility = View.VISIBLE
        binding.emptyView.text = getString(R.string.trash_bin_empty)
    }

    private fun setupBackHandler() {
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isSelectionMode) {
                    exitSelectionMode()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    private fun enterSelectionMode() {
        if (isSelectionMode) return
        isSelectionMode = true
        updateSelectionUi()
    }

    private fun exitSelectionMode() {
        if (!isSelectionMode) return
        isSelectionMode = false
        selectedPaths.clear()
        updateSelectionUi()
    }

    private fun toggleSelection(path: String) {
        if (selectedPaths.contains(path)) {
            selectedPaths.remove(path)
        } else {
            selectedPaths.add(path)
        }

        if (selectedPaths.isEmpty()) {
            exitSelectionMode()
            return
        }

        updateSelectionUi()
    }

    private fun selectAllItems() {
        if (trashItems.isEmpty()) return
        selectedPaths.clear()
        selectedPaths.addAll(trashItems.map { it.path })
        isSelectionMode = true
        updateSelectionUi()
    }

    private fun clearSelection() {
        exitSelectionMode()
    }

    private fun updateSelectionUi() {
        supportActionBar?.title = if (isSelectionMode) {
            getString(R.string.trash_selected_count, selectedPaths.size)
        } else {
            getString(R.string.trash_bin_title)
        }
        val hasSelection = selectedPaths.isNotEmpty()
        binding.selectionActionBar.visibility = if (isSelectionMode) View.VISIBLE else View.GONE
        binding.selectedCountTextView.text = getString(R.string.trash_selected_count, selectedPaths.size)
        binding.restoreSelectedButton.isEnabled = hasSelection
        binding.deleteSelectedButton.isEnabled = hasSelection
        binding.clearSelectionButton.isEnabled = hasSelection

        val changedPaths = (previousSelectionPaths + selectedPaths) - (previousSelectionPaths intersect selectedPaths)
        mediaAdapter.notifySelectionChanged(changedPaths)
        previousSelectionPaths = selectedPaths.toSet()

        invalidateOptionsMenu()
    }

    private data class ScannerUpdate(
        val oldPath: String,
        val newPath: String?
    )

    private data class BulkResult(
        val removedPaths: List<String>,
        val scannerUpdates: List<ScannerUpdate>,
        val failedCount: Int
    )

    private data class DeleteResult(
        val removedPaths: List<String>,
        val failedCount: Int
    )
}
