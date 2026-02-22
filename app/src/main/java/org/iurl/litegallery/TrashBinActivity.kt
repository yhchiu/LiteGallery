package org.iurl.litegallery

import android.content.Intent
import android.database.Cursor
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
    private var appTrashRecordByUri: Map<String, TrashBinDatabase.TrashRecord> = emptyMap()
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
            val (cleanupResult, items) = withContext(Dispatchers.IO) {
                val cleanup = TrashBinStore.cleanupExpiredTrash(this@TrashBinActivity)
                Pair(cleanup, scanTrashItems())
            }

            if (cleanupResult.removedScannerPaths.isNotEmpty()) {
                cleanupResult.removedScannerPaths.forEach { oldPath ->
                    notifyMediaScanner(oldPath, null)
                }
                android.widget.Toast.makeText(
                    this@TrashBinActivity,
                    getString(R.string.trash_auto_cleaned_count, cleanupResult.removedUris.size),
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
        val records = TrashBinStore.getTrashedRecords(this)
        if (records.isEmpty()) {
            appTrashRecordByUri = emptyMap()
            return emptyList()
        }

        val staleUris = mutableListOf<String>()
        val items = mutableListOf<MediaItem>()
        val recordMap = mutableMapOf<String, TrashBinDatabase.TrashRecord>()

        records.forEach { record ->
            val mediaItem = createMediaItemFromRecord(record)
            if (mediaItem == null) {
                staleUris.add(record.trashedUri)
                return@forEach
            }

            items.add(mediaItem)
            recordMap[mediaItem.path] = record
        }

        if (staleUris.isNotEmpty()) {
            TrashBinStore.removeTrashedUris(this, staleUris)
        }

        appTrashRecordByUri = recordMap
        return items
    }

    private fun createMediaItemFromRecord(record: TrashBinDatabase.TrashRecord): MediaItem? {
        val uri = runCatching { Uri.parse(record.trashedUri) }.getOrNull()
        if (uri == null || uri.scheme.isNullOrBlank()) {
            return createMediaItemFromFileReference(record.trashedUri)
        }

        return when (uri.scheme) {
            "file" -> createMediaItemFromFileReference(uri.path)
            "content" -> createMediaItemFromContentUri(record, uri)
            else -> null
        }
    }

    private fun createMediaItemFromFileReference(filePath: String?): MediaItem? {
        if (filePath.isNullOrBlank()) return null
        val file = File(filePath)
        if (!file.exists() || !file.isFile || !file.name.startsWith(TrashBinStore.TRASH_FILE_PREFIX)) {
            return null
        }
        return createMediaItemFromFile(file)
    }

    private fun createMediaItemFromContentUri(
        record: TrashBinDatabase.TrashRecord,
        uri: Uri
    ): MediaItem? {
        val projection = arrayOf(
            android.provider.OpenableColumns.DISPLAY_NAME,
            android.provider.OpenableColumns.SIZE,
            android.provider.DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            android.provider.DocumentsContract.Document.COLUMN_MIME_TYPE,
            android.provider.MediaStore.MediaColumns.DATE_MODIFIED,
            android.provider.MediaStore.MediaColumns.MIME_TYPE
        )

        val cursor = try {
            contentResolver.query(uri, projection, null, null, null)
        } catch (_: Exception) {
            null
        } ?: return null

        cursor.use {
            if (!it.moveToFirst()) return null

            val displayName = getCursorString(it, android.provider.OpenableColumns.DISPLAY_NAME)
                ?: uri.lastPathSegment
                ?: record.originalName
            if (!displayName.startsWith(TrashBinStore.TRASH_FILE_PREFIX)) return null

            val size = getCursorLong(it, android.provider.OpenableColumns.SIZE)?.coerceAtLeast(0L) ?: 0L
            val docLastModified =
                getCursorLong(it, android.provider.DocumentsContract.Document.COLUMN_LAST_MODIFIED)
                    ?.coerceAtLeast(0L)
                    ?: 0L
            val mediaDateModified =
                getCursorLong(it, android.provider.MediaStore.MediaColumns.DATE_MODIFIED)
                    ?.coerceAtLeast(0L)
                    ?: 0L
            val dateModifiedMs = when {
                docLastModified > 0L -> docLastModified
                mediaDateModified > 1_000_000_000_000L -> mediaDateModified
                mediaDateModified > 0L -> mediaDateModified * 1000L
                else -> record.trashedAtMs
            }
            val mimeType = getCursorString(it, android.provider.DocumentsContract.Document.COLUMN_MIME_TYPE)
                ?: getCursorString(it, android.provider.MediaStore.MediaColumns.MIME_TYPE)
                ?: resolveMimeTypeFromName(displayName)
            return MediaItem(
                name = displayName,
                path = record.trashedUri,
                dateModified = dateModifiedMs,
                size = size,
                mimeType = mimeType,
                duration = 0L
            )
        }
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

    private fun resolveMimeTypeFromName(name: String): String {
        val extension = name.substringAfterLast('.', "").lowercase()
        val isVideo = videoExtensions.contains(extension)
        return getMimeTypeFromExtension(extension, isVideo)
    }

    private fun getCursorString(cursor: Cursor, columnName: String): String? {
        val columnIndex = cursor.getColumnIndex(columnName)
        if (columnIndex < 0 || cursor.isNull(columnIndex)) return null
        return cursor.getString(columnIndex)
    }

    private fun getCursorLong(cursor: Cursor, columnName: String): Long? {
        val columnIndex = cursor.getColumnIndex(columnName)
        if (columnIndex < 0 || cursor.isNull(columnIndex)) return null
        return cursor.getLong(columnIndex)
    }

    private fun showTrashItemActions(mediaItem: MediaItem) {
        if (isSelectionMode) {
            toggleSelection(mediaItem.path)
            return
        }

        val titleText = if (isSystemTrashItem(mediaItem)) {
            mediaItem.name
        } else {
            resolveOriginalNameForAppTrashItem(mediaItem) ?: mediaItem.name
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
            .setTitle(titleText)
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

    private fun getAppTrashRecord(mediaItem: MediaItem): TrashBinDatabase.TrashRecord? {
        appTrashRecordByUri[mediaItem.path]?.let { return it }
        val scannerPathMatch = appTrashRecordByUri.values.firstOrNull { record ->
            TrashBinStore.resolveScannerPath(record.trashedUri) == mediaItem.path
        }
        if (scannerPathMatch != null) return scannerPathMatch
        return TrashBinStore.getTrashedRecord(this, mediaItem.path)
    }

    private fun resolveOriginalNameForAppTrashItem(mediaItem: MediaItem): String? {
        val record = getAppTrashRecord(mediaItem)
        if (record != null && record.originalName.isNotBlank()) {
            return record.originalName
        }
        return TrashBinStore.fallbackOriginalNameFromTrashedName(mediaItem.name).takeIf { it.isNotBlank() }
    }

    private fun resolveOriginalPathForAppTrashItem(mediaItem: MediaItem): String? {
        val record = getAppTrashRecord(mediaItem)
        val originalHint = record?.originalPathHint?.takeIf { it.isNotBlank() }
        if (!originalHint.isNullOrBlank()) return originalHint

        val originalUri = record?.originalUri?.takeIf { it.isNotBlank() }
        if (!originalUri.isNullOrBlank()) {
            val scannerPath = TrashBinStore.resolveScannerPath(originalUri)
            if (!scannerPath.isNullOrBlank()) return scannerPath
            return originalUri
        }

        val localPath = TrashBinStore.resolveScannerPath(mediaItem.path) ?: mediaItem.path
        val trashedFile = File(localPath)
        val parent = trashedFile.parentFile ?: return null
        val originalName = resolveOriginalNameForAppTrashItem(mediaItem) ?: return null
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

        val record = getAppTrashRecord(mediaItem)
        if (record == null) {
            TrashBinStore.removeTrashedUri(this, mediaItem.path)
            loadTrashItems()
            return
        }

        val trashedFile = resolveLocalFileFromReference(record.trashedUri)
        if (trashedFile == null) {
            restoreSingleUriItem(record)
            return
        }

        val parent = trashedFile.parentFile
        if (!trashedFile.exists() || parent == null) {
            TrashBinStore.removeTrashedUri(this, record.trashedUri)
            loadTrashItems()
            return
        }

        val originalName = record.originalName
        val targetFile = File(parent, originalName)

        if (!targetFile.exists()) {
            restoreSingleLocalItem(record, trashedFile, targetFile, overwriteExisting = false)
            return
        }

        android.app.AlertDialog.Builder(this)
            .setTitle(R.string.file_conflict_title)
            .setMessage(R.string.file_conflict_message)
            .setPositiveButton(R.string.overwrite) { _, _ ->
                restoreSingleLocalItem(record, trashedFile, targetFile, overwriteExisting = true)
            }
            .setNeutralButton(R.string.use_new_name) { _, _ ->
                val nonConflictFile = findAvailableFileName(parent, originalName)
                restoreSingleLocalItem(record, trashedFile, nonConflictFile, overwriteExisting = false)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun resolveLocalFileFromReference(reference: String): File? {
        if (reference.startsWith("/")) return File(reference)
        val uri = runCatching { Uri.parse(reference) }.getOrNull()
        return when {
            uri == null || uri.scheme.isNullOrBlank() -> File(reference)
            uri.scheme == "file" && !uri.path.isNullOrBlank() -> File(uri.path!!)
            else -> null
        }
    }

    private fun restoreSingleLocalItem(
        record: TrashBinDatabase.TrashRecord,
        trashedFile: File,
        targetFile: File,
        overwriteExisting: Boolean
    ) {
        lifecycleScope.launch {
            val oldReference = record.trashedUri
            val success = withContext(Dispatchers.IO) {
                if (!trashedFile.exists()) return@withContext false
                if (overwriteExisting && targetFile.exists() && !targetFile.delete()) return@withContext false
                if (targetFile.exists()) return@withContext false
                trashedFile.renameTo(targetFile)
            }

            if (success) {
                TrashBinStore.removeTrashedUri(this@TrashBinActivity, oldReference)
                notifyMediaScanner(oldReference, targetFile.absolutePath)
                hasMediaCollectionChanged = true
                android.widget.Toast.makeText(this@TrashBinActivity, R.string.success, android.widget.Toast.LENGTH_SHORT).show()
                loadTrashItems()
            } else {
                android.widget.Toast.makeText(this@TrashBinActivity, R.string.error, android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun restoreSingleUriItem(record: TrashBinDatabase.TrashRecord) {
        lifecycleScope.launch {
            val restoredUri = withContext(Dispatchers.IO) {
                restoreContentUriByRename(record.trashedUri, record.originalName)
            }

            if (restoredUri != null) {
                TrashBinStore.removeTrashedUri(this@TrashBinActivity, record.trashedUri)
                hasMediaCollectionChanged = true
                android.widget.Toast.makeText(this@TrashBinActivity, R.string.success, android.widget.Toast.LENGTH_SHORT).show()
                loadTrashItems()
            } else {
                android.widget.Toast.makeText(this@TrashBinActivity, R.string.error, android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun restoreContentUriByRename(
        trashedUriString: String,
        preferredName: String
    ): Uri? {
        val uri = runCatching { Uri.parse(trashedUriString) }.getOrNull() ?: return null
        if (uri.scheme != "content") return null

        try {
            val restored = android.provider.DocumentsContract.renameDocument(
                contentResolver,
                uri,
                preferredName
            )
            if (restored != null) return restored
        } catch (_: Exception) {
            // Fallback to auto-generated non-conflict names below.
        }

        val dotIndex = preferredName.lastIndexOf('.')
        val hasExtension = dotIndex > 0 && dotIndex < preferredName.length - 1
        val baseName = if (hasExtension) preferredName.substring(0, dotIndex) else preferredName
        val extension = if (hasExtension) preferredName.substring(dotIndex) else ""

        for (index in 1..9999) {
            val candidateName = "$baseName ($index)$extension"
            val renamed = try {
                android.provider.DocumentsContract.renameDocument(
                    contentResolver,
                    uri,
                    candidateName
                )
            } catch (_: Exception) {
                null
            }
            if (renamed != null) return renamed
        }

        return null
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
        val record = getAppTrashRecord(mediaItem)
        if (record == null) {
            TrashBinStore.removeTrashedUri(this, mediaItem.path)
            loadTrashItems()
            return
        }

        lifecycleScope.launch {
            val deleted = withContext(Dispatchers.IO) {
                deleteTrashedReference(record.trashedUri)
            }

            if (deleted) {
                TrashBinStore.removeTrashedUri(this@TrashBinActivity, record.trashedUri)
                notifyMediaScanner(record.trashedUri, null)
                hasMediaCollectionChanged = true
                android.widget.Toast.makeText(this@TrashBinActivity, R.string.success, android.widget.Toast.LENGTH_SHORT).show()
                loadTrashItems()
            } else {
                android.widget.Toast.makeText(this@TrashBinActivity, R.string.error, android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun deleteTrashedReference(reference: String): Boolean {
        val localFile = resolveLocalFileFromReference(reference)
        if (localFile != null) {
            return !localFile.exists() || localFile.delete()
        }

        val uri = runCatching { Uri.parse(reference) }.getOrNull() ?: return false
        if (uri.scheme != "content") return false
        if (isContentReferenceMissing(reference)) return true

        return try {
            if (android.provider.DocumentsContract.isDocumentUri(this, uri)) {
                android.provider.DocumentsContract.deleteDocument(contentResolver, uri)
            } else {
                contentResolver.delete(uri, null, null) > 0
            }
        } catch (_: java.io.FileNotFoundException) {
            true
        } catch (_: Exception) {
            false
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
        return !appTrashRecordByUri.containsKey(item.path)
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
        val appRecords = targetItems
            .mapNotNull { appTrashRecordByUri[it.path] }
            .distinctBy { it.trashedUri }
        val systemUris = targetItems
            .filter { appTrashRecordByUri[it.path] == null }
            .mapNotNull { runCatching { Uri.parse(it.path) }.getOrNull() }

        val appResult = restoreAllItemsInternal(appRecords)
        val systemResult = executeSystemTrashAction(systemUris, SystemTrashActionType.RESTORE)
        return MixedRestoreResult(appResult, systemResult)
    }

    private fun deleteMixedItemsInternal(targetItems: List<MediaItem>): MixedDeleteResult {
        val appRecords = targetItems
            .mapNotNull { appTrashRecordByUri[it.path] }
            .distinctBy { it.trashedUri }
        val systemUris = targetItems
            .filter { appTrashRecordByUri[it.path] == null }
            .mapNotNull { runCatching { Uri.parse(it.path) }.getOrNull() }

        val appResult = emptyTrashInternal(appRecords)
        val systemResult = executeSystemTrashAction(systemUris, SystemTrashActionType.DELETE)
        return MixedDeleteResult(appResult, systemResult)
    }

    private fun applyAppRestoreResult(result: BulkResult) {
        if (result.removedPaths.isNotEmpty()) {
            TrashBinStore.removeTrashedUris(this@TrashBinActivity, result.removedPaths)
        }
        result.scannerUpdates.forEach { update ->
            notifyMediaScanner(update.oldPath, update.newPath)
        }
    }

    private fun applyAppDeleteResult(result: DeleteResult) {
        if (result.removedPaths.isNotEmpty()) {
            TrashBinStore.removeTrashedUris(this@TrashBinActivity, result.removedPaths)
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

    private fun restoreAllItemsInternal(records: List<TrashBinDatabase.TrashRecord>): BulkResult {
        val removedPaths = mutableListOf<String>()
        val scannerUpdates = mutableListOf<ScannerUpdate>()
        var failedCount = 0

        records.forEach { record ->
            val oldReference = record.trashedUri
            val trashedFile = resolveLocalFileFromReference(oldReference)

            if (trashedFile != null) {
                if (!trashedFile.exists()) {
                    removedPaths.add(oldReference)
                    return@forEach
                }

                val parent = trashedFile.parentFile
                if (parent == null) {
                    failedCount++
                    return@forEach
                }

                val originalName = record.originalName
                val target = if (File(parent, originalName).exists()) {
                    findAvailableFileName(parent, originalName)
                } else {
                    File(parent, originalName)
                }

                if (trashedFile.renameTo(target)) {
                    removedPaths.add(oldReference)
                    scannerUpdates.add(ScannerUpdate(oldReference, target.absolutePath))
                } else {
                    failedCount++
                }
                return@forEach
            }

            val restoredUri = restoreContentUriByRename(oldReference, record.originalName)
            if (restoredUri != null) {
                removedPaths.add(oldReference)
            } else if (isContentReferenceMissing(oldReference)) {
                removedPaths.add(oldReference)
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

    private fun emptyTrashInternal(records: List<TrashBinDatabase.TrashRecord>): DeleteResult {
        val removedPaths = mutableListOf<String>()
        var failedCount = 0

        records.forEach { record ->
            val removed = deleteTrashedReference(record.trashedUri)
            if (removed) {
                removedPaths.add(record.trashedUri)
            } else {
                failedCount++
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

    private fun isContentReferenceMissing(reference: String): Boolean {
        val uri = runCatching { Uri.parse(reference) }.getOrNull() ?: return false
        if (uri.scheme != "content") return false
        return try {
            contentResolver.query(
                uri,
                arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                !cursor.moveToFirst()
            } ?: true
        } catch (_: SecurityException) {
            false
        } catch (_: Exception) {
            false
        }
    }

    private fun notifyMediaScanner(oldPath: String, newPath: String?) {
        val oldScannerPath = TrashBinStore.resolveScannerPath(oldPath)
            ?: oldPath.takeIf { it.startsWith("/") }
        if (!oldScannerPath.isNullOrBlank()) {
            android.media.MediaScannerConnection.scanFile(
                this,
                arrayOf(oldScannerPath),
                null
            ) { _, _ -> }
        }

        val newScannerPath = if (!newPath.isNullOrBlank()) {
            TrashBinStore.resolveScannerPath(newPath) ?: newPath.takeIf { it.startsWith("/") }
        } else {
            null
        }
        if (!newScannerPath.isNullOrBlank() && newScannerPath != oldScannerPath) {
            android.media.MediaScannerConnection.scanFile(
                this,
                arrayOf(newScannerPath),
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
