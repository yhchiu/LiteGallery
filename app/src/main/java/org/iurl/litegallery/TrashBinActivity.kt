package org.iurl.litegallery

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
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
    private var pendingSystemTrashAction: PendingSystemTrashAction? = null

    private val imageExtensions = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif")
    private val videoExtensions = setOf("mp4", "avi", "mov", "mkv", "3gp", "webm", "m4v", "flv")

    private val systemTrashActionLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        val pending = pendingSystemTrashAction
        pendingSystemTrashAction = null
        if (pending == null) return@registerForActivityResult

        if (result.resultCode == RESULT_OK) {
            hasMediaCollectionChanged = true
            if (pending.exitSelectionAfter) {
                exitSelectionMode()
            }
            android.widget.Toast.makeText(this, R.string.success, android.widget.Toast.LENGTH_SHORT).show()
            loadTrashItems()
        } else {
            if (pending.exitSelectionAfter) {
                exitSelectionMode()
            }
            android.widget.Toast.makeText(this, R.string.error, android.widget.Toast.LENGTH_SHORT).show()
        }
    }
    
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
                    confirmAndRestoreAll()
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
        val appTrashItems = scanAppTrashItems()
        val systemTrashItems = scanSystemTrashItems()
        if (appTrashItems.isEmpty() && systemTrashItems.isEmpty()) return emptyList()
        return (appTrashItems + systemTrashItems)
            .distinctBy { it.path }
            .sortedByDescending { it.dateModified }
    }

    private fun scanAppTrashItems(): List<MediaItem> {
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

        return items
    }

    private fun scanSystemTrashItems(): List<MediaItem> {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.R) return emptyList()
        val items = mutableListOf<MediaItem>()
        items += querySystemTrashForCollection(
            baseCollectionUri = android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            isVideoCollection = false
        )
        items += querySystemTrashForCollection(
            baseCollectionUri = android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            isVideoCollection = true
        )
        return items
    }

    private fun querySystemTrashForCollection(
        baseCollectionUri: Uri,
        isVideoCollection: Boolean
    ): List<MediaItem> {
        val projection = mutableListOf(
            android.provider.MediaStore.MediaColumns._ID,
            android.provider.MediaStore.MediaColumns.DISPLAY_NAME,
            android.provider.MediaStore.MediaColumns.DATE_MODIFIED,
            android.provider.MediaStore.MediaColumns.SIZE,
            android.provider.MediaStore.MediaColumns.MIME_TYPE
        ).apply {
            if (isVideoCollection) {
                add(android.provider.MediaStore.Video.Media.DURATION)
            }
        }.toTypedArray()

        val selection = "${android.provider.MediaStore.MediaColumns.IS_TRASHED} = 1"
        val cursor = try {
            val queryArgs = android.os.Bundle().apply {
                putInt(
                    android.provider.MediaStore.QUERY_ARG_MATCH_TRASHED,
                    android.provider.MediaStore.MATCH_INCLUDE
                )
                putString(android.content.ContentResolver.QUERY_ARG_SQL_SELECTION, selection)
            }
            contentResolver.query(baseCollectionUri, projection, queryArgs, null)
        } catch (_: Exception) {
            try {
                contentResolver.query(baseCollectionUri, projection, selection, null, null)
            } catch (_: Exception) {
                null
            }
        } ?: return emptyList()

        val items = mutableListOf<MediaItem>()
        cursor.use {
            val idColumn = it.getColumnIndex(android.provider.MediaStore.MediaColumns._ID)
            val nameColumn = it.getColumnIndex(android.provider.MediaStore.MediaColumns.DISPLAY_NAME)
            val dateColumn = it.getColumnIndex(android.provider.MediaStore.MediaColumns.DATE_MODIFIED)
            val sizeColumn = it.getColumnIndex(android.provider.MediaStore.MediaColumns.SIZE)
            val mimeColumn = it.getColumnIndex(android.provider.MediaStore.MediaColumns.MIME_TYPE)
            val durationColumn = if (isVideoCollection) {
                it.getColumnIndex(android.provider.MediaStore.Video.Media.DURATION)
            } else {
                -1
            }

            while (it.moveToNext()) {
                val id = if (idColumn >= 0) it.getLong(idColumn) else -1L
                if (id <= 0L) continue

                val itemUri = android.content.ContentUris.withAppendedId(baseCollectionUri, id)
                val name = if (nameColumn >= 0) it.getString(nameColumn) else null
                val dateModifiedMs = if (dateColumn >= 0) it.getLong(dateColumn) * 1000L else 0L
                val size = if (sizeColumn >= 0) it.getLong(sizeColumn).coerceAtLeast(0L) else 0L
                val mimeType = if (mimeColumn >= 0) it.getString(mimeColumn) else null
                val duration = if (durationColumn >= 0) it.getLong(durationColumn).coerceAtLeast(0L) else 0L

                items.add(
                    MediaItem(
                        name = name ?: itemUri.lastPathSegment ?: getString(R.string.unknown_value),
                        path = itemUri.toString(),
                        dateModified = dateModifiedMs,
                        size = size,
                        mimeType = mimeType ?: if (isVideoCollection) "video/*" else "image/*",
                        duration = duration
                    )
                )
            }
        }
        return items
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

        val trashSource = if (isSystemTrashItem(mediaItem)) {
            getString(R.string.trash_source_system)
        } else {
            getString(R.string.trash_source_app)
        }
        val originalPath = resolveOriginalPathForActionDialog(mediaItem)
            ?: getString(R.string.unknown_value)
        val detailsMessage = buildString {
            append(getString(R.string.trash_source_label))
            append(": ")
            append(trashSource)
            append('\n')
            append(getString(R.string.original_file_path))
            append(": ")
            append(originalPath)
        }

        android.app.AlertDialog.Builder(this)
            .setTitle(mediaItem.name)
            .setMessage(detailsMessage)
            .setPositiveButton(R.string.restore) { _, _ ->
                confirmRestoreItem(mediaItem)
            }
            .setNeutralButton(R.string.delete_permanently) { _, _ ->
                confirmDeletePermanently(mediaItem)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun resolveOriginalPathForActionDialog(mediaItem: MediaItem): String? {
        return if (isSystemTrashItem(mediaItem)) {
            resolveOriginalPathForSystemTrashItem(mediaItem)
        } else {
            resolveOriginalPathForAppTrashItem(mediaItem)
        }
    }

    private fun resolveOriginalPathForAppTrashItem(mediaItem: MediaItem): String? {
        val trashedFile = File(mediaItem.path)
        val parent = trashedFile.parentFile ?: return null
        val originalName = if (trashedFile.exists()) {
            TrashBinStore.resolveOriginalName(this, trashedFile)
        } else {
            TrashBinStore.fallbackOriginalNameFromTrashedName(trashedFile.name)
        }
        if (originalName.isBlank()) return null
        return File(parent, originalName).absolutePath
    }

    private fun resolveOriginalPathForSystemTrashItem(mediaItem: MediaItem): String? {
        val uri = runCatching { Uri.parse(mediaItem.path) }.getOrNull() ?: return null
        val projection = arrayOf(
            android.provider.MediaStore.MediaColumns.DATA,
            android.provider.MediaStore.MediaColumns.RELATIVE_PATH,
            android.provider.MediaStore.MediaColumns.DISPLAY_NAME
        )
        val cursor = try {
            contentResolver.query(uri, projection, null, null, null)
        } catch (_: Exception) {
            null
        } ?: return null

        cursor.use {
            if (!it.moveToFirst()) return null

            val dataColumn = it.getColumnIndex(android.provider.MediaStore.MediaColumns.DATA)
            if (dataColumn >= 0) {
                val dataPath = it.getString(dataColumn)
                if (!dataPath.isNullOrBlank()) return dataPath
            }

            val relativePathColumn = it.getColumnIndex(android.provider.MediaStore.MediaColumns.RELATIVE_PATH)
            val nameColumn = it.getColumnIndex(android.provider.MediaStore.MediaColumns.DISPLAY_NAME)
            val relativePath = if (relativePathColumn >= 0) it.getString(relativePathColumn) else null
            val displayName = if (nameColumn >= 0) it.getString(nameColumn) else mediaItem.name

            if (!relativePath.isNullOrBlank() && !displayName.isNullOrBlank()) {
                val root = android.os.Environment.getExternalStorageDirectory()
                return File(File(root, relativePath), displayName).absolutePath
            }
        }

        return null
    }

    private fun confirmRestoreItem(mediaItem: MediaItem) {
        if (isSystemTrashItem(mediaItem)) {
            val uri = runCatching { Uri.parse(mediaItem.path) }.getOrNull()
            if (uri == null) {
                android.widget.Toast.makeText(this, R.string.error, android.widget.Toast.LENGTH_SHORT).show()
                return
            }
            performSystemTrashAction(
                uris = listOf(uri),
                actionType = SystemTrashActionType.RESTORE,
                exitSelectionAfter = false
            )
            return
        }

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
                if (isSystemTrashItem(mediaItem)) {
                    val uri = runCatching { Uri.parse(mediaItem.path) }.getOrNull()
                    if (uri == null) {
                        android.widget.Toast.makeText(this@TrashBinActivity, R.string.error, android.widget.Toast.LENGTH_SHORT).show()
                    } else {
                        performSystemTrashAction(
                            uris = listOf(uri),
                            actionType = SystemTrashActionType.DELETE,
                            exitSelectionAfter = false
                        )
                    }
                } else {
                    deleteSingleTrashedItem(mediaItem)
                }
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
                hasMediaCollectionChanged = true
                android.widget.Toast.makeText(this@TrashBinActivity, R.string.success, android.widget.Toast.LENGTH_SHORT).show()
                loadTrashItems()
            } else {
                android.widget.Toast.makeText(this@TrashBinActivity, R.string.error, android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun restoreAllItems() {
        if (trashItems.isEmpty()) return
        performRestoreForItems(trashItems, exitSelectionAfter = false)
    }

    private fun confirmAndRestoreAll() {
        if (trashItems.isEmpty()) return

        android.app.AlertDialog.Builder(this)
            .setTitle(R.string.restore_all)
            .setMessage(R.string.restore_all_confirmation)
            .setPositiveButton(R.string.restore_all) { _, _ ->
                restoreAllItems()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun restoreSelectedItems() {
        val targetItems = resolveSelectedItems()
        if (targetItems.isEmpty()) return
        performRestoreForItems(targetItems, exitSelectionAfter = true)
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
        performDeleteForItems(trashItems, exitSelectionAfter = false)
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
        val targetItems = resolveSelectedItems()
        if (targetItems.isEmpty()) return
        performDeleteForItems(targetItems, exitSelectionAfter = true)
    }

    private fun resolveSelectedItems(): List<MediaItem> {
        if (selectedPaths.isEmpty()) return emptyList()
        return trashItems.filter { selectedPaths.contains(it.path) }
    }

    private fun isSystemTrashItem(item: MediaItem): Boolean {
        return item.path.startsWith("content://")
    }

    private fun performRestoreForItems(targetItems: List<MediaItem>, exitSelectionAfter: Boolean) {
        lifecycleScope.launch {
            val mixedResult = withContext(Dispatchers.IO) {
                restoreMixedItemsInternal(targetItems)
            }

            applyAppRestoreResult(mixedResult.appResult)
            val launchedUserAction = launchSystemUserActionIfNeeded(mixedResult.systemResult, exitSelectionAfter)

            val appSuccess = mixedResult.appResult.removedPaths.size
            val appFailed = mixedResult.appResult.failedCount
            val systemSuccess = mixedResult.systemResult.successCount
            val systemActionLaunchFailed =
                !launchedUserAction && mixedResult.systemResult.userActionIntentSender != null
            val systemFailed = mixedResult.systemResult.failedCount +
                if (systemActionLaunchFailed) mixedResult.systemResult.userActionUris.size else 0
            val totalSuccess = appSuccess + systemSuccess
            val totalFailed = appFailed + systemFailed

            if (totalSuccess > 0) {
                hasMediaCollectionChanged = true
            }

            if (!launchedUserAction) {
                val message = if (totalSuccess > 0 && totalFailed == 0) R.string.success else R.string.error
                android.widget.Toast.makeText(this@TrashBinActivity, message, android.widget.Toast.LENGTH_SHORT).show()
                if (exitSelectionAfter) exitSelectionMode()
                loadTrashItems()
            } else {
                if (appSuccess > 0 || appFailed > 0) {
                    loadTrashItems()
                }
            }
        }
    }

    private fun performDeleteForItems(targetItems: List<MediaItem>, exitSelectionAfter: Boolean) {
        lifecycleScope.launch {
            val mixedResult = withContext(Dispatchers.IO) {
                deleteMixedItemsInternal(targetItems)
            }

            applyAppDeleteResult(mixedResult.appResult)
            val launchedUserAction = launchSystemUserActionIfNeeded(mixedResult.systemResult, exitSelectionAfter)

            val appSuccess = mixedResult.appResult.removedPaths.size
            val appFailed = mixedResult.appResult.failedCount
            val systemSuccess = mixedResult.systemResult.successCount
            val systemActionLaunchFailed =
                !launchedUserAction && mixedResult.systemResult.userActionIntentSender != null
            val systemFailed = mixedResult.systemResult.failedCount +
                if (systemActionLaunchFailed) mixedResult.systemResult.userActionUris.size else 0
            val totalSuccess = appSuccess + systemSuccess
            val totalFailed = appFailed + systemFailed

            if (totalSuccess > 0) {
                hasMediaCollectionChanged = true
            }

            if (!launchedUserAction) {
                val message = if (totalSuccess > 0 && totalFailed == 0) R.string.success else R.string.error
                android.widget.Toast.makeText(this@TrashBinActivity, message, android.widget.Toast.LENGTH_SHORT).show()
                if (exitSelectionAfter) exitSelectionMode()
                loadTrashItems()
            } else {
                if (appSuccess > 0 || appFailed > 0) {
                    loadTrashItems()
                }
            }
        }
    }

    private fun restoreMixedItemsInternal(targetItems: List<MediaItem>): MixedRestoreResult {
        val appPaths = targetItems
            .filterNot(::isSystemTrashItem)
            .map { it.path }
        val systemUris = targetItems
            .filter(::isSystemTrashItem)
            .mapNotNull { runCatching { Uri.parse(it.path) }.getOrNull() }

        val appResult = restoreAllItemsInternal(appPaths)
        val systemResult = executeSystemTrashAction(systemUris, SystemTrashActionType.RESTORE)
        return MixedRestoreResult(appResult, systemResult)
    }

    private fun deleteMixedItemsInternal(targetItems: List<MediaItem>): MixedDeleteResult {
        val appPaths = targetItems
            .filterNot(::isSystemTrashItem)
            .map { it.path }
        val systemUris = targetItems
            .filter(::isSystemTrashItem)
            .mapNotNull { runCatching { Uri.parse(it.path) }.getOrNull() }

        val appResult = emptyTrashInternal(appPaths)
        val systemResult = executeSystemTrashAction(systemUris, SystemTrashActionType.DELETE)
        return MixedDeleteResult(appResult, systemResult)
    }

    private fun applyAppRestoreResult(result: BulkResult) {
        if (result.removedPaths.isNotEmpty()) {
            TrashBinStore.removeTrashedPaths(this@TrashBinActivity, result.removedPaths)
        }
        result.scannerUpdates.forEach { update ->
            notifyMediaScanner(update.oldPath, update.newPath)
        }
    }

    private fun applyAppDeleteResult(result: DeleteResult) {
        if (result.removedPaths.isNotEmpty()) {
            TrashBinStore.removeTrashedPaths(this@TrashBinActivity, result.removedPaths)
            result.removedPaths.forEach { oldPath ->
                notifyMediaScanner(oldPath, null)
            }
        }
    }

    private fun performSystemTrashAction(
        uris: List<Uri>,
        actionType: SystemTrashActionType,
        exitSelectionAfter: Boolean
    ) {
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                executeSystemTrashAction(uris, actionType)
            }

            val launched = launchSystemUserActionIfNeeded(result, exitSelectionAfter)
            val effectiveFailed = result.failedCount + if (!launched && result.userActionIntentSender != null) {
                result.userActionUris.size
            } else {
                0
            }
            if (result.successCount > 0) {
                hasMediaCollectionChanged = true
            }

            if (!launched) {
                val message = if (result.successCount > 0 && effectiveFailed == 0) {
                    R.string.success
                } else {
                    R.string.error
                }
                android.widget.Toast.makeText(this@TrashBinActivity, message, android.widget.Toast.LENGTH_SHORT).show()
                if (exitSelectionAfter) exitSelectionMode()
                loadTrashItems()
            }
        }
    }

    private fun executeSystemTrashAction(
        uris: List<Uri>,
        actionType: SystemTrashActionType
    ): SystemTrashOperationResult {
        if (uris.isEmpty()) return SystemTrashOperationResult(successCount = 0, failedCount = 0)

        val uniqueUris = uris.distinct()
        var successCount = 0
        var failedCount = 0
        val userActionRequiredUris = mutableListOf<Uri>()

        uniqueUris.forEach { uri ->
            try {
                val success = when (actionType) {
                    SystemTrashActionType.RESTORE -> restoreSystemTrashDirect(uri)
                    SystemTrashActionType.DELETE -> deleteSystemItemDirect(uri)
                }
                if (success) {
                    successCount++
                } else {
                    failedCount++
                }
            } catch (_: SecurityException) {
                userActionRequiredUris.add(uri)
            } catch (_: Exception) {
                failedCount++
            }
        }

        if (userActionRequiredUris.isNotEmpty()) {
            val intentSender = buildSystemActionIntentSender(actionType, userActionRequiredUris)
            if (intentSender != null) {
                return SystemTrashOperationResult(
                    successCount = successCount,
                    failedCount = failedCount,
                    userActionIntentSender = intentSender,
                    userActionUris = userActionRequiredUris,
                    actionType = actionType
                )
            }
            failedCount += userActionRequiredUris.size
        }

        return SystemTrashOperationResult(
            successCount = successCount,
            failedCount = failedCount
        )
    }

    private fun restoreSystemTrashDirect(uri: Uri): Boolean {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.R) return false
        val values = android.content.ContentValues().apply {
            put(android.provider.MediaStore.MediaColumns.IS_TRASHED, 0)
        }
        return contentResolver.update(uri, values, null, null) > 0
    }

    private fun deleteSystemItemDirect(uri: Uri): Boolean {
        return contentResolver.delete(uri, null, null) > 0
    }

    private fun buildSystemActionIntentSender(
        actionType: SystemTrashActionType,
        uris: List<Uri>
    ): android.content.IntentSender? {
        if (uris.isEmpty()) return null
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.R) return null
        return try {
            when (actionType) {
                SystemTrashActionType.RESTORE ->
                    android.provider.MediaStore.createTrashRequest(contentResolver, uris, false).intentSender
                SystemTrashActionType.DELETE ->
                    android.provider.MediaStore.createDeleteRequest(contentResolver, uris).intentSender
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun launchSystemUserActionIfNeeded(
        result: SystemTrashOperationResult,
        exitSelectionAfter: Boolean
    ): Boolean {
        val intentSender = result.userActionIntentSender ?: return false
        val actionType = result.actionType ?: return false
        val uris = result.userActionUris
        if (uris.isEmpty()) return false

        pendingSystemTrashAction = PendingSystemTrashAction(
            actionType = actionType,
            uris = uris,
            exitSelectionAfter = exitSelectionAfter
        )

        return try {
            val request = androidx.activity.result.IntentSenderRequest.Builder(intentSender).build()
            systemTrashActionLauncher.launch(request)
            true
        } catch (_: Exception) {
            pendingSystemTrashAction = null
            false
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

    private data class MixedRestoreResult(
        val appResult: BulkResult,
        val systemResult: SystemTrashOperationResult
    )

    private data class MixedDeleteResult(
        val appResult: DeleteResult,
        val systemResult: SystemTrashOperationResult
    )

    private data class SystemTrashOperationResult(
        val successCount: Int,
        val failedCount: Int,
        val userActionIntentSender: android.content.IntentSender? = null,
        val userActionUris: List<Uri> = emptyList(),
        val actionType: SystemTrashActionType? = null
    )

    private data class PendingSystemTrashAction(
        val actionType: SystemTrashActionType,
        val uris: List<Uri>,
        val exitSelectionAfter: Boolean
    )

    private enum class SystemTrashActionType {
        RESTORE,
        DELETE
    }
}
