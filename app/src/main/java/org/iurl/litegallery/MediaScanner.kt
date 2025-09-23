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
        
        // Also scan file system for non-media folders
        val fileSystemFolders = fileSystemScanner.scanAllFoldersForMedia(ignoreNomedia = false)
        
        // Merge MediaStore results with file system results
        val mergedFolders = mutableMapOf<String, MediaFolder>()
        
        // Add MediaStore results
        folders.forEach { (path, items) ->
            val folderFile = File(path)
            mergedFolders[path] = MediaFolder(
                name = folderFile.name,
                path = path,
                itemCount = items.size,
                thumbnail = items.firstOrNull()?.path
            )
        }

        // Add file system results and merge with existing folders
        fileSystemFolders.forEach { folder ->
            val existingFolder = mergedFolders[folder.path]
            if (existingFolder != null) {
                // Get the actual count by scanning the folder again to include all files
                val actualCount = getActualFileCount(folder.path)
                mergedFolders[folder.path] = existingFolder.copy(itemCount = actualCount)
            } else {
                // Add new folder from file system scan
                mergedFolders[folder.path] = folder
            }
        }
        
        mergedFolders.values.sortedBy { it.name }
    }

    private fun getActualFileCount(folderPath: String): Int {
        return try {
            val folder = File(folderPath)
            if (!folder.exists() || !folder.isDirectory) return 0

            var count = 0
            folder.listFiles()?.forEach { file ->
                if (file.isFile && isMediaFile(file)) {
                    count++
                }
            }
            count
        } catch (e: Exception) {
            0
        }
    }

    private fun isMediaFile(file: File): Boolean {
        val extension = file.extension.lowercase()
        val imageExtensions = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif")
        val videoExtensions = setOf("mp4", "avi", "mov", "mkv", "3gp", "webm", "m4v", "flv")
        return imageExtensions.contains(extension) || videoExtensions.contains(extension)
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
                
                if (!file.exists()) continue
                
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
                
                if (!file.exists()) continue
                
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
                
                if (!file.exists() || file.parent != folderPath) continue
                
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
                
                if (!file.exists() || file.parent != folderPath) continue
                
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