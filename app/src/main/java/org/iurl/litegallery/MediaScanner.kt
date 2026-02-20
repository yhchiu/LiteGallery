package org.iurl.litegallery

import android.content.Context
import android.database.Cursor
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class MediaScanner(private val context: Context) {
    
    private val fileSystemScanner = FileSystemScanner(context)
    
    suspend fun scanMediaFolders(): List<MediaFolder> = withContext(Dispatchers.IO) {
        val folders = mutableMapOf<String, MutableList<MediaItem>>()
        
        // Scan images
        scanImages(folders)
        
        // Scan videos
        scanVideos(folders)

        if (folders.isNotEmpty()) {
            return@withContext folders.map { (path, items) ->
                val folderFile = File(path)
                MediaFolder(
                    name = folderFile.name,
                    path = path,
                    itemCount = items.size,
                    thumbnail = items.firstOrNull()?.path
                )
            }.sortedBy { it.name }
        }

        // Fallback: only do expensive full file-system scan when MediaStore has no results.
        fileSystemScanner.scanAllFoldersForMedia(ignoreNomedia = false)
    }

    private fun isTrashedFile(file: File): Boolean {
        return file.name.startsWith(TrashBinStore.TRASH_FILE_PREFIX)
    }
    
    suspend fun scanMediaInFolder(folderPath: String): List<MediaItem> = withContext(Dispatchers.IO) {
        val items = mutableListOf<MediaItem>()
        
        // Scan images in folder
        scanImagesInFolder(folderPath, items)
        
        // Scan videos in folder
        scanVideosInFolder(folderPath, items)
        
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
        
        items.sortedByDescending { it.dateModified }
    }
    
    private fun scanImages(folders: MutableMap<String, MutableList<MediaItem>>) {
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATA,
            MediaStore.Images.Media.DATE_MODIFIED,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.MIME_TYPE,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT
        )
        
        val cursor: Cursor? = context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            MediaStore.Images.Media.DATE_MODIFIED + " DESC"
        )
        
        cursor?.use {
            val nameColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val dataColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
            val dateColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED)
            val sizeColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
            val mimeColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)
            val widthColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
            val heightColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)
            
            while (it.moveToNext()) {
                val path = it.getString(dataColumn)
                val file = File(path)
                val folderPath = file.parent ?: continue
                
                if (!file.exists() || isTrashedFile(file)) continue
                
                val mediaItem = MediaItem(
                    name = it.getString(nameColumn),
                    path = path,
                    dateModified = it.getLong(dateColumn) * 1000,
                    size = it.getLong(sizeColumn),
                    mimeType = it.getString(mimeColumn) ?: "image/*",
                    width = it.getInt(widthColumn),
                    height = it.getInt(heightColumn)
                )
                
                folders.getOrPut(folderPath) { mutableListOf() }.add(mediaItem)
            }
        }
    }
    
    private fun scanVideos(folders: MutableMap<String, MutableList<MediaItem>>) {
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DATA,
            MediaStore.Video.Media.DATE_MODIFIED,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.MIME_TYPE,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.HEIGHT
        )
        
        val cursor: Cursor? = context.contentResolver.query(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            MediaStore.Video.Media.DATE_MODIFIED + " DESC"
        )
        
        cursor?.use {
            val nameColumn = it.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val dataColumn = it.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
            val dateColumn = it.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_MODIFIED)
            val sizeColumn = it.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
            val mimeColumn = it.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE)
            val durationColumn = it.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
            val widthColumn = it.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH)
            val heightColumn = it.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT)
            
            while (it.moveToNext()) {
                val path = it.getString(dataColumn)
                val file = File(path)
                val folderPath = file.parent ?: continue
                
                if (!file.exists() || isTrashedFile(file)) continue
                
                val mediaItem = MediaItem(
                    name = it.getString(nameColumn),
                    path = path,
                    dateModified = it.getLong(dateColumn) * 1000,
                    size = it.getLong(sizeColumn),
                    mimeType = it.getString(mimeColumn) ?: "video/*",
                    duration = it.getLong(durationColumn),
                    width = it.getInt(widthColumn),
                    height = it.getInt(heightColumn)
                )
                
                folders.getOrPut(folderPath) { mutableListOf() }.add(mediaItem)
            }
        }
    }
    
    private fun scanImagesInFolder(folderPath: String, items: MutableList<MediaItem>) {
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATA,
            MediaStore.Images.Media.DATE_MODIFIED,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.MIME_TYPE,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT
        )
        
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
            val sizeColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
            val mimeColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)
            val widthColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
            val heightColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)
            
            while (it.moveToNext()) {
                val path = it.getString(dataColumn)
                val file = File(path)
                
                if (!file.exists() || file.parent != folderPath || isTrashedFile(file)) continue
                
                val mediaItem = MediaItem(
                    name = it.getString(nameColumn),
                    path = path,
                    dateModified = it.getLong(dateColumn) * 1000,
                    size = it.getLong(sizeColumn),
                    mimeType = it.getString(mimeColumn) ?: "image/*",
                    width = it.getInt(widthColumn),
                    height = it.getInt(heightColumn)
                )
                
                items.add(mediaItem)
            }
        }
    }
    
    private fun scanVideosInFolder(folderPath: String, items: MutableList<MediaItem>) {
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DATA,
            MediaStore.Video.Media.DATE_MODIFIED,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.MIME_TYPE,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.HEIGHT
        )
        
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
            val sizeColumn = it.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
            val mimeColumn = it.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE)
            val durationColumn = it.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
            val widthColumn = it.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH)
            val heightColumn = it.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT)
            
            while (it.moveToNext()) {
                val path = it.getString(dataColumn)
                val file = File(path)
                
                if (!file.exists() || file.parent != folderPath || isTrashedFile(file)) continue
                
                val mediaItem = MediaItem(
                    name = it.getString(nameColumn),
                    path = path,
                    dateModified = it.getLong(dateColumn) * 1000,
                    size = it.getLong(sizeColumn),
                    mimeType = it.getString(mimeColumn) ?: "video/*",
                    duration = it.getLong(durationColumn),
                    width = it.getInt(widthColumn),
                    height = it.getInt(heightColumn)
                )
                
                items.add(mediaItem)
            }
        }
    }
}
