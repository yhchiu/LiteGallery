package org.iurl.litegallery

import android.content.Context
import android.content.ContentUris
import android.database.Cursor
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class MediaScanner(private val context: Context) {

    companion object {
        internal fun buildImageFolderProjection(includeDeferredMetadata: Boolean): Array<String> {
            val projection = mutableListOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.RELATIVE_PATH,
                MediaStore.Images.Media.DATA,
                MediaStore.Images.Media.DATE_MODIFIED,
                MediaStore.Images.Media.MIME_TYPE
            )
            if (includeDeferredMetadata) {
                projection.add(MediaStore.Images.Media.SIZE)
                projection.add(MediaStore.Images.Media.WIDTH)
                projection.add(MediaStore.Images.Media.HEIGHT)
            }
            return projection.toTypedArray()
        }

        internal fun buildVideoFolderProjection(
            includeDeferredMetadata: Boolean,
            includeVideoDuration: Boolean
        ): Array<String> {
            val projection = mutableListOf(
                MediaStore.Video.Media._ID,
                MediaStore.Video.Media.DISPLAY_NAME,
                MediaStore.Video.Media.RELATIVE_PATH,
                MediaStore.Video.Media.DATA,
                MediaStore.Video.Media.DATE_MODIFIED,
                MediaStore.Video.Media.MIME_TYPE
            )
            if (includeVideoDuration) {
                projection.add(MediaStore.Video.Media.DURATION)
            }
            if (includeDeferredMetadata) {
                projection.add(MediaStore.Video.Media.SIZE)
                projection.add(MediaStore.Video.Media.WIDTH)
                projection.add(MediaStore.Video.Media.HEIGHT)
            }
            return projection.toTypedArray()
        }
    }
    
    private val fileSystemScanner = FileSystemScanner(context)
    private val primaryExternalRootPath: String? =
        Environment.getExternalStorageDirectory()?.absolutePath

    private data class FolderAggregate(
        var itemCount: Int = 0,
        var thumbnailPath: String? = null,
        var latestDateModifiedMs: Long = Long.MIN_VALUE
    )

    private data class FolderQuery(
        val selection: String,
        val selectionArgs: Array<String>
    )
    
    suspend fun scanMediaFolders(
        allowDeepFileSystemFallback: Boolean = false
    ): List<MediaFolder> = withContext(Dispatchers.IO) {
        val folders = mutableMapOf<String, FolderAggregate>()
        
        // Lightweight scan for folder list: only count items and keep a representative thumbnail path.
        scanImagesForFolderList(folders)
        
        scanVideosForFolderList(folders)

        if (folders.isNotEmpty()) {
            return@withContext folders.map { (path, aggregate) ->
                val folderFile = File(path)
                MediaFolder(
                    name = folderFile.name,
                    path = path,
                    itemCount = aggregate.itemCount,
                    thumbnail = aggregate.thumbnailPath
                )
            }.sortedBy { it.name }
        }

        // Deep file-system fallback is disabled by default and allowed only in advanced mode.
        if (allowDeepFileSystemFallback && canUseDeepFileSystemFallback()) {
            return@withContext fileSystemScanner.scanAllFoldersForMedia(ignoreNomedia = false)
        }
        emptyList()
    }

    private fun isTrashedFile(file: File): Boolean {
        return file.name.startsWith(TrashBinStore.TRASH_FILE_PREFIX)
    }
    
    suspend fun scanMediaInFolder(
        folderPath: String,
        includeDeferredMetadata: Boolean = false,
        includeVideoDuration: Boolean = true,
        mergeFileSystemFallback: Boolean = true
    ): List<MediaItem> = withContext(Dispatchers.IO) {
        val items = mutableListOf<MediaItem>()
        
        // Scan images in folder
        scanImagesInFolder(folderPath, items, includeDeferredMetadata)
        
        // Scan videos in folder
        scanVideosInFolder(folderPath, items, includeDeferredMetadata, includeVideoDuration)
        
        if (mergeFileSystemFallback && canUseDeepFileSystemFallback()) {
            // If MediaStore didn't find anything, try file system scan (for non-media folders)
            if (items.isEmpty()) {
                val fileSystemItems = fileSystemScanner.scanFolderForMedia(folderPath, ignoreNomedia = true)
                items.addAll(fileSystemItems)
            } else {
                // Merge with file system results to get files not in MediaStore
                val fileSystemItems = fileSystemScanner.scanFolderForMedia(folderPath, ignoreNomedia = true)
                val existingPaths = items.map { it.path }.toSet()
                fileSystemItems.forEach { item ->
                    if (!existingPaths.contains(item.path)) {
                        items.add(item)
                    }
                }
            }
        }
        
        val sortedItems = items.sortedByDescending { it.dateModified }
        if (includeDeferredMetadata) {
            if (sortedItems.any(::needsDeferredMetadata)) {
                enrichItemsWithSizeAndDimensions(sortedItems)
            } else {
                sortedItems
            }
        } else {
            sortedItems
        }
    }

    suspend fun enrichItemsWithSizeAndDimensions(items: List<MediaItem>): List<MediaItem> = withContext(Dispatchers.IO) {
        if (items.isEmpty()) return@withContext items

        val mutableItems = items.toMutableList()
        val indexByPath = mutableItems.mapIndexed { index, item -> item.path to index }.toMap()
        var changed = false

        // Query MediaStore in batches by folder only when metadata is missing.
        val foldersToQuery = mutableItems.asSequence()
            .filter { needsDeferredMetadata(it) && !it.path.startsWith("content://") }
            .mapNotNull { File(it.path).parent }
            .toSet()

        foldersToQuery.forEach { folderPath ->
            if (enrichImageMetadataInFolder(folderPath, indexByPath, mutableItems)) {
                changed = true
            }
            if (enrichVideoMetadataInFolder(folderPath, indexByPath, mutableItems)) {
                changed = true
            }
        }

        for (index in mutableItems.indices) {
            val current = mutableItems[index]
            if (!needsDeferredMetadata(current)) continue
            val enriched = fillMissingMetadataFromSource(current)
            if (enriched != current) {
                mutableItems[index] = enriched
                changed = true
            }
        }

        if (changed) mutableItems else items
    }

    suspend fun enrichItemWithSizeAndDimensions(item: MediaItem): MediaItem = withContext(Dispatchers.IO) {
        if (!needsDeferredMetadata(item)) return@withContext item

        var enrichedItem = enrichItemFromMediaStore(item)
        if (needsDeferredMetadata(enrichedItem)) {
            enrichedItem = fillMissingMetadataFromSource(enrichedItem)
        }
        enrichedItem
    }
    
    private fun scanImagesForFolderList(folders: MutableMap<String, FolderAggregate>) {
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.RELATIVE_PATH,
            MediaStore.Images.Media.DATA,
            MediaStore.Images.Media.DATE_MODIFIED
        )
        
        val cursor: Cursor? = context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            null
        )
        
        cursor?.use {
            val idColumn = it.getColumnIndex(MediaStore.Images.Media._ID)
            val nameColumn = it.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME)
            val relativePathColumn = it.getColumnIndex(MediaStore.Images.Media.RELATIVE_PATH)
            val dataColumn = it.getColumnIndex(MediaStore.Images.Media.DATA)
            val dateColumn = it.getColumnIndex(MediaStore.Images.Media.DATE_MODIFIED)
            
            while (it.moveToNext()) {
                val fileName = if (nameColumn >= 0) it.getString(nameColumn) else null
                if (isTrashedName(fileName)) continue

                val path = resolveCursorMediaPath(
                    cursor = it,
                    dataColumn = dataColumn,
                    relativePathColumn = relativePathColumn,
                    nameColumn = nameColumn,
                    idColumn = idColumn,
                    fallbackContentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                ) ?: continue
                val folderPath = deriveFolderPathFromCursor(it, path, relativePathColumn) ?: continue

                val dateModifiedMs = if (dateColumn >= 0) it.getLong(dateColumn) * 1000 else 0L
                updateFolderAggregate(folders, folderPath, path, dateModifiedMs)
            }
        }
    }
    
    private fun scanVideosForFolderList(folders: MutableMap<String, FolderAggregate>) {
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.RELATIVE_PATH,
            MediaStore.Video.Media.DATA,
            MediaStore.Video.Media.DATE_MODIFIED
        )
        
        val cursor: Cursor? = context.contentResolver.query(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            null
        )
        
        cursor?.use {
            val idColumn = it.getColumnIndex(MediaStore.Video.Media._ID)
            val nameColumn = it.getColumnIndex(MediaStore.Video.Media.DISPLAY_NAME)
            val relativePathColumn = it.getColumnIndex(MediaStore.Video.Media.RELATIVE_PATH)
            val dataColumn = it.getColumnIndex(MediaStore.Video.Media.DATA)
            val dateColumn = it.getColumnIndex(MediaStore.Video.Media.DATE_MODIFIED)
            
            while (it.moveToNext()) {
                val fileName = if (nameColumn >= 0) it.getString(nameColumn) else null
                if (isTrashedName(fileName)) continue

                val path = resolveCursorMediaPath(
                    cursor = it,
                    dataColumn = dataColumn,
                    relativePathColumn = relativePathColumn,
                    nameColumn = nameColumn,
                    idColumn = idColumn,
                    fallbackContentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                ) ?: continue
                val folderPath = deriveFolderPathFromCursor(it, path, relativePathColumn) ?: continue

                val dateModifiedMs = if (dateColumn >= 0) it.getLong(dateColumn) * 1000 else 0L
                updateFolderAggregate(folders, folderPath, path, dateModifiedMs)
            }
        }
    }

    private fun updateFolderAggregate(
        folders: MutableMap<String, FolderAggregate>,
        folderPath: String,
        mediaPath: String,
        dateModifiedMs: Long
    ) {
        val aggregate = folders.getOrPut(folderPath) { FolderAggregate() }
        aggregate.itemCount += 1
        if (aggregate.thumbnailPath == null || dateModifiedMs > aggregate.latestDateModifiedMs) {
            aggregate.thumbnailPath = mediaPath
            aggregate.latestDateModifiedMs = dateModifiedMs
        }
    }

    private fun buildFolderQuery(
        folderPath: String,
        dataColumn: String,
        relativePathColumn: String
    ): FolderQuery {
        val relativePath = resolveRelativePathFromFolder(folderPath)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !relativePath.isNullOrBlank()) {
            FolderQuery(
                selection = "$relativePathColumn = ?",
                selectionArgs = arrayOf(relativePath)
            )
        } else {
            FolderQuery(
                selection = "$dataColumn LIKE ?",
                selectionArgs = arrayOf("$folderPath%")
            )
        }
    }

    private fun resolveRelativePathFromFolder(folderPath: String): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        val root = primaryExternalRootPath ?: return null

        val normalizedRoot = root.trimEnd(File.separatorChar) + File.separator
        val normalizedFolder = folderPath.trimEnd(File.separatorChar) + File.separator
        if (!normalizedFolder.startsWith(normalizedRoot)) return null

        val relative = normalizedFolder.removePrefix(normalizedRoot)
        return relative.ifBlank { null }
    }

    private fun canUseDeepFileSystemFallback(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return true
        return Environment.isExternalStorageManager()
    }

    private fun resolveAbsolutePathFromRelative(relativePath: String?, displayName: String?): String? {
        if (relativePath.isNullOrBlank() || displayName.isNullOrBlank()) return null
        val root = primaryExternalRootPath ?: return null
        return File(File(root, relativePath), displayName).absolutePath
    }

    private fun resolveCursorMediaPath(
        cursor: Cursor,
        dataColumn: Int,
        relativePathColumn: Int,
        nameColumn: Int,
        idColumn: Int,
        fallbackContentUri: Uri
    ): String? {
        if (dataColumn >= 0) {
            val dataPath = cursor.getString(dataColumn)
            if (!dataPath.isNullOrBlank()) return dataPath
        }

        val relativePath = if (relativePathColumn >= 0) cursor.getString(relativePathColumn) else null
        val displayName = if (nameColumn >= 0) cursor.getString(nameColumn) else null
        resolveAbsolutePathFromRelative(relativePath, displayName)?.let { return it }

        if (idColumn >= 0) {
            val id = cursor.getLong(idColumn)
            if (id > 0L) {
                return ContentUris.withAppendedId(fallbackContentUri, id).toString()
            }
        }

        return null
    }

    private fun deriveFolderPathFromCursor(
        cursor: Cursor,
        mediaPath: String,
        relativePathColumn: Int
    ): String? {
        if (!mediaPath.startsWith("content://")) {
            return File(mediaPath).parent
        }

        if (relativePathColumn < 0) return null
        val relativePath = cursor.getString(relativePathColumn) ?: return null
        val root = primaryExternalRootPath ?: return null
        return File(root, relativePath).absolutePath.trimEnd(File.separatorChar)
    }

    private fun isTrashedName(fileName: String?): Boolean {
        return fileName?.startsWith(TrashBinStore.TRASH_FILE_PREFIX) == true
    }
    
    private fun scanImagesInFolder(
        folderPath: String,
        items: MutableList<MediaItem>,
        includeDeferredMetadata: Boolean
    ) {
        val projection = buildImageFolderProjection(includeDeferredMetadata)

        val folderQuery = buildFolderQuery(
            folderPath = folderPath,
            dataColumn = MediaStore.Images.Media.DATA,
            relativePathColumn = MediaStore.Images.Media.RELATIVE_PATH
        )
        
        val cursor: Cursor? = context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            folderQuery.selection,
            folderQuery.selectionArgs,
            MediaStore.Images.Media.DATE_MODIFIED + " DESC"
        )
        
        cursor?.use {
            val idColumn = it.getColumnIndex(MediaStore.Images.Media._ID)
            val nameColumn = it.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME)
            val relativePathColumn = it.getColumnIndex(MediaStore.Images.Media.RELATIVE_PATH)
            val dataColumn = it.getColumnIndex(MediaStore.Images.Media.DATA)
            val dateColumn = it.getColumnIndex(MediaStore.Images.Media.DATE_MODIFIED)
            val mimeColumn = it.getColumnIndex(MediaStore.Images.Media.MIME_TYPE)
            val sizeColumn = if (includeDeferredMetadata) {
                it.getColumnIndex(MediaStore.Images.Media.SIZE)
            } else {
                -1
            }
            val widthColumn = if (includeDeferredMetadata) {
                it.getColumnIndex(MediaStore.Images.Media.WIDTH)
            } else {
                -1
            }
            val heightColumn = if (includeDeferredMetadata) {
                it.getColumnIndex(MediaStore.Images.Media.HEIGHT)
            } else {
                -1
            }
            
            while (it.moveToNext()) {
                val fileName = if (nameColumn >= 0) it.getString(nameColumn) else null
                if (isTrashedName(fileName)) continue

                val path = resolveCursorMediaPath(
                    cursor = it,
                    dataColumn = dataColumn,
                    relativePathColumn = relativePathColumn,
                    nameColumn = nameColumn,
                    idColumn = idColumn,
                    fallbackContentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                ) ?: continue

                if (!path.startsWith("content://")) {
                    val file = File(path)
                    if (file.parent != folderPath || isTrashedFile(file)) continue
                }
                
                val mediaItem = MediaItem(
                    name = fileName ?: File(path).name,
                    path = path,
                    dateModified = if (dateColumn >= 0) it.getLong(dateColumn) * 1000 else 0L,
                    size = if (includeDeferredMetadata) {
                        if (sizeColumn >= 0) it.getLong(sizeColumn).coerceAtLeast(0L) else 0L
                    } else {
                        // Keep folder scan lightweight; load size/resolution only when needed.
                        0
                    },
                    mimeType = if (mimeColumn >= 0) it.getString(mimeColumn) ?: "image/*" else "image/*",
                    width = if (includeDeferredMetadata && widthColumn >= 0) it.getInt(widthColumn).coerceAtLeast(0) else 0,
                    height = if (includeDeferredMetadata && heightColumn >= 0) it.getInt(heightColumn).coerceAtLeast(0) else 0
                )
                
                items.add(mediaItem)
            }
        }
    }
    
    private fun scanVideosInFolder(
        folderPath: String,
        items: MutableList<MediaItem>,
        includeDeferredMetadata: Boolean,
        includeVideoDuration: Boolean
    ) {
        val projection = buildVideoFolderProjection(
            includeDeferredMetadata = includeDeferredMetadata,
            includeVideoDuration = includeVideoDuration
        )
        
        val folderQuery = buildFolderQuery(
            folderPath = folderPath,
            dataColumn = MediaStore.Video.Media.DATA,
            relativePathColumn = MediaStore.Video.Media.RELATIVE_PATH
        )
        
        val cursor: Cursor? = context.contentResolver.query(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            projection,
            folderQuery.selection,
            folderQuery.selectionArgs,
            MediaStore.Video.Media.DATE_MODIFIED + " DESC"
        )
        
        cursor?.use {
            val idColumn = it.getColumnIndex(MediaStore.Video.Media._ID)
            val nameColumn = it.getColumnIndex(MediaStore.Video.Media.DISPLAY_NAME)
            val relativePathColumn = it.getColumnIndex(MediaStore.Video.Media.RELATIVE_PATH)
            val dataColumn = it.getColumnIndex(MediaStore.Video.Media.DATA)
            val dateColumn = it.getColumnIndex(MediaStore.Video.Media.DATE_MODIFIED)
            val mimeColumn = it.getColumnIndex(MediaStore.Video.Media.MIME_TYPE)
            val durationColumn = if (includeVideoDuration) {
                it.getColumnIndex(MediaStore.Video.Media.DURATION)
            } else {
                -1
            }
            val sizeColumn = if (includeDeferredMetadata) {
                it.getColumnIndex(MediaStore.Video.Media.SIZE)
            } else {
                -1
            }
            val widthColumn = if (includeDeferredMetadata) {
                it.getColumnIndex(MediaStore.Video.Media.WIDTH)
            } else {
                -1
            }
            val heightColumn = if (includeDeferredMetadata) {
                it.getColumnIndex(MediaStore.Video.Media.HEIGHT)
            } else {
                -1
            }
            
            while (it.moveToNext()) {
                val fileName = if (nameColumn >= 0) it.getString(nameColumn) else null
                if (isTrashedName(fileName)) continue

                val path = resolveCursorMediaPath(
                    cursor = it,
                    dataColumn = dataColumn,
                    relativePathColumn = relativePathColumn,
                    nameColumn = nameColumn,
                    idColumn = idColumn,
                    fallbackContentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                ) ?: continue

                if (!path.startsWith("content://")) {
                    val file = File(path)
                    if (file.parent != folderPath || isTrashedFile(file)) continue
                }
                
                val mediaItem = MediaItem(
                    name = fileName ?: File(path).name,
                    path = path,
                    dateModified = if (dateColumn >= 0) it.getLong(dateColumn) * 1000 else 0L,
                    size = if (includeDeferredMetadata) {
                        if (sizeColumn >= 0) it.getLong(sizeColumn).coerceAtLeast(0L) else 0L
                    } else {
                        // Keep folder scan lightweight; load size/resolution only when needed.
                        0
                    },
                    mimeType = if (mimeColumn >= 0) it.getString(mimeColumn) ?: "video/*" else "video/*",
                    duration = if (includeVideoDuration && durationColumn >= 0) it.getLong(durationColumn).coerceAtLeast(0L) else 0L,
                    width = if (includeDeferredMetadata && widthColumn >= 0) it.getInt(widthColumn).coerceAtLeast(0) else 0,
                    height = if (includeDeferredMetadata && heightColumn >= 0) it.getInt(heightColumn).coerceAtLeast(0) else 0
                )
                
                items.add(mediaItem)
            }
        }
    }

    private fun needsDeferredMetadata(item: MediaItem): Boolean {
        return item.size <= 0L || item.width <= 0 || item.height <= 0
    }

    private fun enrichImageMetadataInFolder(
        folderPath: String,
        indexByPath: Map<String, Int>,
        items: MutableList<MediaItem>
    ): Boolean {
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.RELATIVE_PATH,
            MediaStore.Images.Media.DATA,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT
        )
        val folderQuery = buildFolderQuery(
            folderPath = folderPath,
            dataColumn = MediaStore.Images.Media.DATA,
            relativePathColumn = MediaStore.Images.Media.RELATIVE_PATH
        )

        var changed = false
        val cursor: Cursor? = context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            folderQuery.selection,
            folderQuery.selectionArgs,
            null
        )

        cursor?.use {
            val idColumn = it.getColumnIndex(MediaStore.Images.Media._ID)
            val nameColumn = it.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME)
            val relativePathColumn = it.getColumnIndex(MediaStore.Images.Media.RELATIVE_PATH)
            val dataColumn = it.getColumnIndex(MediaStore.Images.Media.DATA)
            val sizeColumn = it.getColumnIndex(MediaStore.Images.Media.SIZE)
            val widthColumn = it.getColumnIndex(MediaStore.Images.Media.WIDTH)
            val heightColumn = it.getColumnIndex(MediaStore.Images.Media.HEIGHT)

            while (it.moveToNext()) {
                val fileName = if (nameColumn >= 0) it.getString(nameColumn) else null
                if (isTrashedName(fileName)) continue

                val path = resolveCursorMediaPath(
                    cursor = it,
                    dataColumn = dataColumn,
                    relativePathColumn = relativePathColumn,
                    nameColumn = nameColumn,
                    idColumn = idColumn,
                    fallbackContentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                ) ?: continue
                if (path.startsWith("content://")) continue

                val file = File(path)
                if (file.parent != folderPath || isTrashedFile(file)) continue

                val index = indexByPath[path] ?: continue
                val current = items[index]
                if (current.isVideo || !needsDeferredMetadata(current)) continue

                val updated = current.copy(
                    size = if (current.size > 0L || sizeColumn < 0) current.size else it.getLong(sizeColumn).coerceAtLeast(0L),
                    width = if (current.width > 0 || widthColumn < 0) current.width else it.getInt(widthColumn).coerceAtLeast(0),
                    height = if (current.height > 0 || heightColumn < 0) current.height else it.getInt(heightColumn).coerceAtLeast(0)
                )

                if (updated != current) {
                    items[index] = updated
                    changed = true
                }
            }
        }

        return changed
    }

    private fun enrichVideoMetadataInFolder(
        folderPath: String,
        indexByPath: Map<String, Int>,
        items: MutableList<MediaItem>
    ): Boolean {
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.RELATIVE_PATH,
            MediaStore.Video.Media.DATA,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.HEIGHT,
            MediaStore.Video.Media.DURATION
        )
        val folderQuery = buildFolderQuery(
            folderPath = folderPath,
            dataColumn = MediaStore.Video.Media.DATA,
            relativePathColumn = MediaStore.Video.Media.RELATIVE_PATH
        )

        var changed = false
        val cursor: Cursor? = context.contentResolver.query(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            projection,
            folderQuery.selection,
            folderQuery.selectionArgs,
            null
        )

        cursor?.use {
            val idColumn = it.getColumnIndex(MediaStore.Video.Media._ID)
            val nameColumn = it.getColumnIndex(MediaStore.Video.Media.DISPLAY_NAME)
            val relativePathColumn = it.getColumnIndex(MediaStore.Video.Media.RELATIVE_PATH)
            val dataColumn = it.getColumnIndex(MediaStore.Video.Media.DATA)
            val sizeColumn = it.getColumnIndex(MediaStore.Video.Media.SIZE)
            val widthColumn = it.getColumnIndex(MediaStore.Video.Media.WIDTH)
            val heightColumn = it.getColumnIndex(MediaStore.Video.Media.HEIGHT)
            val durationColumn = it.getColumnIndex(MediaStore.Video.Media.DURATION)

            while (it.moveToNext()) {
                val fileName = if (nameColumn >= 0) it.getString(nameColumn) else null
                if (isTrashedName(fileName)) continue

                val path = resolveCursorMediaPath(
                    cursor = it,
                    dataColumn = dataColumn,
                    relativePathColumn = relativePathColumn,
                    nameColumn = nameColumn,
                    idColumn = idColumn,
                    fallbackContentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                ) ?: continue
                if (path.startsWith("content://")) continue

                val file = File(path)
                if (file.parent != folderPath || isTrashedFile(file)) continue

                val index = indexByPath[path] ?: continue
                val current = items[index]
                if (!current.isVideo || !needsDeferredMetadata(current)) continue

                val updated = current.copy(
                    size = if (current.size > 0L || sizeColumn < 0) current.size else it.getLong(sizeColumn).coerceAtLeast(0L),
                    width = if (current.width > 0 || widthColumn < 0) current.width else it.getInt(widthColumn).coerceAtLeast(0),
                    height = if (current.height > 0 || heightColumn < 0) current.height else it.getInt(heightColumn).coerceAtLeast(0),
                    duration = if (current.duration > 0L || durationColumn < 0) current.duration else it.getLong(durationColumn).coerceAtLeast(0L)
                )

                if (updated != current) {
                    items[index] = updated
                    changed = true
                }
            }
        }

        return changed
    }

    private fun enrichItemFromMediaStore(item: MediaItem): MediaItem {
        if (item.path.startsWith("content://")) return item

        val itemFile = File(item.path)
        val itemFolderPath = itemFile.parent
        val itemName = itemFile.name

        return if (item.isVideo) {
            val projection = arrayOf(
                MediaStore.Video.Media.SIZE,
                MediaStore.Video.Media.WIDTH,
                MediaStore.Video.Media.HEIGHT,
                MediaStore.Video.Media.DURATION
            )
            val relativeFolderPath = itemFolderPath?.let { resolveRelativePathFromFolder(it) }
            val usesScopedSelection = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !relativeFolderPath.isNullOrBlank()
            val selection = if (usesScopedSelection) {
                "${MediaStore.Video.Media.RELATIVE_PATH} = ? AND ${MediaStore.Video.Media.DISPLAY_NAME} = ?"
            } else {
                "${MediaStore.Video.Media.DATA} = ?"
            }
            val selectionArgs = if (usesScopedSelection) {
                arrayOf(relativeFolderPath!!, itemName)
            } else {
                arrayOf(item.path)
            }
            val cursor: Cursor? = context.contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                null
            )

            cursor?.use {
                if (!it.moveToFirst()) return@use item
                val sizeColumn = it.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                val widthColumn = it.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH)
                val heightColumn = it.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT)
                val durationColumn = it.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)

                item.copy(
                    size = if (item.size > 0L) item.size else it.getLong(sizeColumn).coerceAtLeast(0L),
                    width = if (item.width > 0) item.width else it.getInt(widthColumn).coerceAtLeast(0),
                    height = if (item.height > 0) item.height else it.getInt(heightColumn).coerceAtLeast(0),
                    duration = if (item.duration > 0L) item.duration else it.getLong(durationColumn).coerceAtLeast(0L)
                )
            } ?: item
        } else {
            val projection = arrayOf(
                MediaStore.Images.Media.SIZE,
                MediaStore.Images.Media.WIDTH,
                MediaStore.Images.Media.HEIGHT
            )
            val relativeFolderPath = itemFolderPath?.let { resolveRelativePathFromFolder(it) }
            val usesScopedSelection = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !relativeFolderPath.isNullOrBlank()
            val selection = if (usesScopedSelection) {
                "${MediaStore.Images.Media.RELATIVE_PATH} = ? AND ${MediaStore.Images.Media.DISPLAY_NAME} = ?"
            } else {
                "${MediaStore.Images.Media.DATA} = ?"
            }
            val selectionArgs = if (usesScopedSelection) {
                arrayOf(relativeFolderPath!!, itemName)
            } else {
                arrayOf(item.path)
            }
            val cursor: Cursor? = context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                null
            )

            cursor?.use {
                if (!it.moveToFirst()) return@use item
                val sizeColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
                val widthColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
                val heightColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)

                item.copy(
                    size = if (item.size > 0L) item.size else it.getLong(sizeColumn).coerceAtLeast(0L),
                    width = if (item.width > 0) item.width else it.getInt(widthColumn).coerceAtLeast(0),
                    height = if (item.height > 0) item.height else it.getInt(heightColumn).coerceAtLeast(0)
                )
            } ?: item
        }
    }

    private fun fillMissingMetadataFromSource(item: MediaItem): MediaItem {
        return if (item.path.startsWith("content://")) {
            fillMissingMetadataFromUri(item, Uri.parse(item.path))
        } else {
            fillMissingMetadataFromFile(item)
        }
    }

    private fun fillMissingMetadataFromFile(item: MediaItem): MediaItem {
        val file = File(item.path)
        var updated = item

        if (updated.size <= 0L && file.exists()) {
            updated = updated.copy(size = file.length().coerceAtLeast(0L))
        }

        if (updated.width <= 0 || updated.height <= 0) {
            updated = if (updated.isVideo) {
                val metadata = getVideoMetadataFromPath(item.path)
                updated.copy(
                    width = if (updated.width > 0) updated.width else metadata.width,
                    height = if (updated.height > 0) updated.height else metadata.height,
                    duration = if (updated.duration > 0L) updated.duration else metadata.duration
                )
            } else {
                val (width, height) = getImageDimensionsFromPath(item.path)
                updated.copy(
                    width = if (updated.width > 0) updated.width else width,
                    height = if (updated.height > 0) updated.height else height
                )
            }
        }

        return updated
    }

    private fun fillMissingMetadataFromUri(item: MediaItem, uri: Uri): MediaItem {
        var updated = item

        if (updated.size <= 0L) {
            val uriSize = try {
                context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { afd ->
                    afd.length.takeIf { it > 0L } ?: -1L
                } ?: -1L
            } catch (_: Exception) {
                -1L
            }
            if (uriSize > 0L) {
                updated = updated.copy(size = uriSize)
            }
        }

        if (updated.width <= 0 || updated.height <= 0) {
            updated = if (updated.isVideo) {
                val metadata = getVideoMetadataFromUri(uri)
                updated.copy(
                    width = if (updated.width > 0) updated.width else metadata.width,
                    height = if (updated.height > 0) updated.height else metadata.height,
                    duration = if (updated.duration > 0L) updated.duration else metadata.duration
                )
            } else {
                val (width, height) = getImageDimensionsFromUri(uri)
                updated.copy(
                    width = if (updated.width > 0) updated.width else width,
                    height = if (updated.height > 0) updated.height else height
                )
            }
        }

        return updated
    }

    private data class VideoMetadata(
        val width: Int = 0,
        val height: Int = 0,
        val duration: Long = 0L
    )

    private fun getImageDimensionsFromPath(path: String): Pair<Int, Int> {
        return try {
            val options = BitmapFactory.Options()
            options.inJustDecodeBounds = true
            BitmapFactory.decodeFile(path, options)
            Pair(options.outWidth.coerceAtLeast(0), options.outHeight.coerceAtLeast(0))
        } catch (_: Exception) {
            Pair(0, 0)
        }
    }

    private fun getImageDimensionsFromUri(uri: Uri): Pair<Int, Int> {
        return try {
            val options = BitmapFactory.Options()
            options.inJustDecodeBounds = true
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BitmapFactory.decodeStream(inputStream, null, options)
            }
            Pair(options.outWidth.coerceAtLeast(0), options.outHeight.coerceAtLeast(0))
        } catch (_: Exception) {
            Pair(0, 0)
        }
    }

    private fun getVideoMetadataFromPath(path: String): VideoMetadata {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(path)
            VideoMetadata(
                width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull()?.coerceAtLeast(0) ?: 0,
                height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull()?.coerceAtLeast(0) ?: 0,
                duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?.coerceAtLeast(0L) ?: 0L
            )
        } catch (_: Exception) {
            VideoMetadata()
        } finally {
            retriever.release()
        }
    }

    private fun getVideoMetadataFromUri(uri: Uri): VideoMetadata {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            VideoMetadata(
                width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull()?.coerceAtLeast(0) ?: 0,
                height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull()?.coerceAtLeast(0) ?: 0,
                duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?.coerceAtLeast(0L) ?: 0L
            )
        } catch (_: Exception) {
            VideoMetadata()
        } finally {
            retriever.release()
        }
    }
}
