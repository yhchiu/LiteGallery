package org.iurl.litegallery

import android.content.Context
import android.database.Cursor
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class MediaScanner(private val context: Context) {
    
    private val fileSystemScanner = FileSystemScanner(context)

    private data class FolderAggregate(
        var itemCount: Int = 0,
        var thumbnailPath: String? = null,
        var latestDateModifiedMs: Long = Long.MIN_VALUE
    )
    
    suspend fun scanMediaFolders(): List<MediaFolder> = withContext(Dispatchers.IO) {
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

        // Fallback: only do expensive full file-system scan when MediaStore has no results.
        fileSystemScanner.scanAllFoldersForMedia(ignoreNomedia = false)
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
        
        if (mergeFileSystemFallback) {
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
            MediaStore.Images.Media.DATA,
            MediaStore.Images.Media.DATE_MODIFIED
        )
        
        val cursor: Cursor? = context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            MediaStore.Images.Media.DATE_MODIFIED + " DESC"
        )
        
        cursor?.use {
            val dataColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
            val dateColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED)
            
            while (it.moveToNext()) {
                val path = it.getString(dataColumn)
                val file = File(path)
                val folderPath = file.parent ?: continue
                
                if (!file.exists() || isTrashedFile(file)) continue

                val dateModifiedMs = it.getLong(dateColumn) * 1000
                updateFolderAggregate(folders, folderPath, path, dateModifiedMs)
            }
        }
    }
    
    private fun scanVideosForFolderList(folders: MutableMap<String, FolderAggregate>) {
        val projection = arrayOf(
            MediaStore.Video.Media.DATA,
            MediaStore.Video.Media.DATE_MODIFIED
        )
        
        val cursor: Cursor? = context.contentResolver.query(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            MediaStore.Video.Media.DATE_MODIFIED + " DESC"
        )
        
        cursor?.use {
            val dataColumn = it.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
            val dateColumn = it.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_MODIFIED)
            
            while (it.moveToNext()) {
                val path = it.getString(dataColumn)
                val file = File(path)
                val folderPath = file.parent ?: continue
                
                if (!file.exists() || isTrashedFile(file)) continue

                val dateModifiedMs = it.getLong(dateColumn) * 1000
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
    
    private fun scanImagesInFolder(
        folderPath: String,
        items: MutableList<MediaItem>,
        includeDeferredMetadata: Boolean
    ) {
        val projection = if (includeDeferredMetadata) {
            arrayOf(
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.DATA,
                MediaStore.Images.Media.DATE_MODIFIED,
                MediaStore.Images.Media.MIME_TYPE,
                MediaStore.Images.Media.SIZE,
                MediaStore.Images.Media.WIDTH,
                MediaStore.Images.Media.HEIGHT
            )
        } else {
            arrayOf(
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.DATA,
                MediaStore.Images.Media.DATE_MODIFIED,
                MediaStore.Images.Media.MIME_TYPE
            )
        }
        
        val selection = "${MediaStore.Images.Media.DATA} LIKE ?"
        val selectionArgs = arrayOf("$folderPath%")
        
        val cursor: Cursor? = context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            MediaStore.Images.Media.DATE_MODIFIED + " DESC"
        )
        
        cursor?.use {
            val nameColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val dataColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
            val dateColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED)
            val mimeColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)
            val sizeColumn = if (includeDeferredMetadata) {
                it.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
            } else {
                -1
            }
            val widthColumn = if (includeDeferredMetadata) {
                it.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
            } else {
                -1
            }
            val heightColumn = if (includeDeferredMetadata) {
                it.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)
            } else {
                -1
            }
            
            while (it.moveToNext()) {
                val path = it.getString(dataColumn)
                val file = File(path)
                
                if (!file.exists() || file.parent != folderPath || isTrashedFile(file)) continue
                
                val mediaItem = MediaItem(
                    name = it.getString(nameColumn),
                    path = path,
                    dateModified = it.getLong(dateColumn) * 1000,
                    size = if (includeDeferredMetadata) {
                        it.getLong(sizeColumn).coerceAtLeast(0L)
                    } else {
                        // Keep folder scan lightweight; load size/resolution only when needed.
                        0
                    },
                    mimeType = it.getString(mimeColumn) ?: "image/*",
                    width = if (includeDeferredMetadata) it.getInt(widthColumn).coerceAtLeast(0) else 0,
                    height = if (includeDeferredMetadata) it.getInt(heightColumn).coerceAtLeast(0) else 0
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
        val projectionList = mutableListOf(
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DATA,
            MediaStore.Video.Media.DATE_MODIFIED,
            MediaStore.Video.Media.MIME_TYPE
        )
        if (includeVideoDuration) {
            projectionList.add(MediaStore.Video.Media.DURATION)
        }
        if (includeDeferredMetadata) {
            projectionList.add(MediaStore.Video.Media.SIZE)
            projectionList.add(MediaStore.Video.Media.WIDTH)
            projectionList.add(MediaStore.Video.Media.HEIGHT)
        }
        val projection = projectionList.toTypedArray()
        
        val selection = "${MediaStore.Video.Media.DATA} LIKE ?"
        val selectionArgs = arrayOf("$folderPath%")
        
        val cursor: Cursor? = context.contentResolver.query(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            MediaStore.Video.Media.DATE_MODIFIED + " DESC"
        )
        
        cursor?.use {
            val nameColumn = it.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val dataColumn = it.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
            val dateColumn = it.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_MODIFIED)
            val mimeColumn = it.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE)
            val durationColumn = if (includeVideoDuration) {
                it.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
            } else {
                -1
            }
            val sizeColumn = if (includeDeferredMetadata) {
                it.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
            } else {
                -1
            }
            val widthColumn = if (includeDeferredMetadata) {
                it.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH)
            } else {
                -1
            }
            val heightColumn = if (includeDeferredMetadata) {
                it.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT)
            } else {
                -1
            }
            
            while (it.moveToNext()) {
                val path = it.getString(dataColumn)
                val file = File(path)
                
                if (!file.exists() || file.parent != folderPath || isTrashedFile(file)) continue
                
                val mediaItem = MediaItem(
                    name = it.getString(nameColumn),
                    path = path,
                    dateModified = it.getLong(dateColumn) * 1000,
                    size = if (includeDeferredMetadata) {
                        it.getLong(sizeColumn).coerceAtLeast(0L)
                    } else {
                        // Keep folder scan lightweight; load size/resolution only when needed.
                        0
                    },
                    mimeType = it.getString(mimeColumn) ?: "video/*",
                    duration = if (includeVideoDuration) it.getLong(durationColumn).coerceAtLeast(0L) else 0L,
                    width = if (includeDeferredMetadata) it.getInt(widthColumn).coerceAtLeast(0) else 0,
                    height = if (includeDeferredMetadata) it.getInt(heightColumn).coerceAtLeast(0) else 0
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
            MediaStore.Images.Media.DATA,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT
        )
        val selection = "${MediaStore.Images.Media.DATA} LIKE ?"
        val selectionArgs = arrayOf("$folderPath%")

        var changed = false
        val cursor: Cursor? = context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            null
        )

        cursor?.use {
            val dataColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
            val sizeColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
            val widthColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
            val heightColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)

            while (it.moveToNext()) {
                val path = it.getString(dataColumn)
                val file = File(path)
                if (file.parent != folderPath || isTrashedFile(file)) continue

                val index = indexByPath[path] ?: continue
                val current = items[index]
                if (current.isVideo || !needsDeferredMetadata(current)) continue

                val updated = current.copy(
                    size = if (current.size > 0L) current.size else it.getLong(sizeColumn).coerceAtLeast(0L),
                    width = if (current.width > 0) current.width else it.getInt(widthColumn).coerceAtLeast(0),
                    height = if (current.height > 0) current.height else it.getInt(heightColumn).coerceAtLeast(0)
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
            MediaStore.Video.Media.DATA,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.HEIGHT,
            MediaStore.Video.Media.DURATION
        )
        val selection = "${MediaStore.Video.Media.DATA} LIKE ?"
        val selectionArgs = arrayOf("$folderPath%")

        var changed = false
        val cursor: Cursor? = context.contentResolver.query(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            null
        )

        cursor?.use {
            val dataColumn = it.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
            val sizeColumn = it.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
            val widthColumn = it.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH)
            val heightColumn = it.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT)
            val durationColumn = it.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)

            while (it.moveToNext()) {
                val path = it.getString(dataColumn)
                val file = File(path)
                if (file.parent != folderPath || isTrashedFile(file)) continue

                val index = indexByPath[path] ?: continue
                val current = items[index]
                if (!current.isVideo || !needsDeferredMetadata(current)) continue

                val updated = current.copy(
                    size = if (current.size > 0L) current.size else it.getLong(sizeColumn).coerceAtLeast(0L),
                    width = if (current.width > 0) current.width else it.getInt(widthColumn).coerceAtLeast(0),
                    height = if (current.height > 0) current.height else it.getInt(heightColumn).coerceAtLeast(0),
                    duration = if (current.duration > 0L) current.duration else it.getLong(durationColumn).coerceAtLeast(0L)
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

        return if (item.isVideo) {
            val projection = arrayOf(
                MediaStore.Video.Media.SIZE,
                MediaStore.Video.Media.WIDTH,
                MediaStore.Video.Media.HEIGHT,
                MediaStore.Video.Media.DURATION
            )
            val selection = "${MediaStore.Video.Media.DATA} = ?"
            val selectionArgs = arrayOf(item.path)
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
            val selection = "${MediaStore.Images.Media.DATA} = ?"
            val selectionArgs = arrayOf(item.path)
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
