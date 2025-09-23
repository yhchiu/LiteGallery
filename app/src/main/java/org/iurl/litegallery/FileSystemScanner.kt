package org.iurl.litegallery

import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class FileSystemScanner(private val context: Context) {
    
    private val imageExtensions = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif")
    private val videoExtensions = setOf("mp4", "avi", "mov", "mkv", "3gp", "webm", "m4v", "flv")
    
    suspend fun scanFolderForMedia(folderPath: String, ignoreNomedia: Boolean = false): List<MediaItem> = 
        withContext(Dispatchers.IO) {
            val mediaItems = mutableListOf<MediaItem>()
            val folder = File(folderPath)
            
            if (!folder.exists() || !folder.isDirectory) {
                return@withContext emptyList()
            }
            
            // Check for .nomedia file unless we're ignoring it
            if (!ignoreNomedia && hasNomediaFile(folder)) {
                return@withContext emptyList()
            }
            
            try {
                folder.listFiles()?.forEach { file ->
                    if (file.isFile && isMediaFile(file)) {
                        val mediaItem = createMediaItemFromFile(file)
                        mediaItems.add(mediaItem)
                    }
                }
            } catch (e: SecurityException) {
                // Handle permission denied
                return@withContext emptyList()
            }
            
            mediaItems.sortedByDescending { it.dateModified }
        }
    
    suspend fun scanAllFoldersForMedia(ignoreNomedia: Boolean = false): List<MediaFolder> = 
        withContext(Dispatchers.IO) {
            val folders = mutableMapOf<String, MutableList<MediaItem>>()
            
            // Get external storage directories
            val externalDirs = getAccessibleStoragePaths()
            
            for (rootPath in externalDirs) {
                scanDirectoryRecursively(File(rootPath), folders, ignoreNomedia)
            }
            
            // Convert to MediaFolder objects
            folders.map { (path, items) ->
                val folderFile = File(path)
                MediaFolder(
                    name = folderFile.name,
                    path = path,
                    itemCount = items.size,
                    thumbnail = items.firstOrNull()?.path
                )
            }.sortedBy { it.name }
        }
    
    private fun getAccessibleStoragePaths(): List<String> {
        val paths = mutableListOf<String>()
        
        // Primary external storage
        android.os.Environment.getExternalStorageDirectory()?.let { primaryStorage ->
            if (primaryStorage.exists() && primaryStorage.canRead()) {
                paths.add(primaryStorage.absolutePath)
            }
        }
        
        // Secondary external storage (SD cards)
        context.getExternalFilesDirs(null)?.forEach { dir ->
            dir?.let { externalDir ->
                // Navigate up to get the root of external storage
                var parent = externalDir.parentFile
                while (parent != null && parent.name != "Android") {
                    val tempParent = parent.parentFile
                    if (tempParent == null) break
                    parent = tempParent
                }
                parent?.let { root ->
                    if (root.exists() && root.canRead() && !paths.contains(root.absolutePath)) {
                        paths.add(root.absolutePath)
                    }
                }
            }
        }
        
        return paths
    }
    
    private fun scanDirectoryRecursively(
        directory: File, 
        folders: MutableMap<String, MutableList<MediaItem>>,
        ignoreNomedia: Boolean,
        maxDepth: Int = 5,
        currentDepth: Int = 0
    ) {
        if (currentDepth >= maxDepth) return
        
        try {
            // Check for .nomedia file unless we're ignoring it
            if (!ignoreNomedia && hasNomediaFile(directory)) {
                return
            }
            
            val mediaItems = mutableListOf<MediaItem>()
            
            directory.listFiles()?.forEach { file ->
                when {
                    file.isFile && isMediaFile(file) -> {
                        mediaItems.add(createMediaItemFromFile(file))
                    }
                    file.isDirectory && !file.name.startsWith(".") -> {
                        // Recursively scan subdirectories
                        scanDirectoryRecursively(file, folders, ignoreNomedia, maxDepth, currentDepth + 1)
                    }
                }
            }
            
            // Only add folders that contain media files
            if (mediaItems.isNotEmpty()) {
                folders[directory.absolutePath] = mediaItems
            }
            
        } catch (e: SecurityException) {
            // Skip directories we don't have permission to read
        } catch (e: Exception) {
            // Skip problematic directories
        }
    }
    
    private fun hasNomediaFile(directory: File): Boolean {
        return try {
            File(directory, ".nomedia").exists()
        } catch (e: Exception) {
            false
        }
    }
    
    private fun isMediaFile(file: File): Boolean {
        val extension = file.extension.lowercase()
        return imageExtensions.contains(extension) || videoExtensions.contains(extension)
    }
    
    private fun createMediaItemFromFile(file: File): MediaItem {
        val extension = file.extension.lowercase()
        val isVideo = videoExtensions.contains(extension)
        val dimensions = getMediaDimensions(file, isVideo)
        
        return MediaItem(
            name = file.name,
            path = file.absolutePath,
            dateModified = file.lastModified(),
            size = file.length(),
            mimeType = getMimeTypeFromExtension(extension, isVideo),
            duration = 0, // File system scan can't get video duration efficiently
            width = dimensions.first,
            height = dimensions.second
        )
    }
    
    private fun getMediaDimensions(file: File, isVideo: Boolean): Pair<Int, Int> {
        return try {
            if (isVideo) {
                getVideoDimensions(file)
            } else {
                getImageDimensions(file)
            }
        } catch (e: Exception) {
            // Return 0x0 if we can't get dimensions
            Pair(0, 0)
        }
    }
    
    private fun getImageDimensions(file: File): Pair<Int, Int> {
        val options = BitmapFactory.Options()
        options.inJustDecodeBounds = true
        BitmapFactory.decodeFile(file.absolutePath, options)
        return Pair(options.outWidth, options.outHeight)
    }
    
    private fun getVideoDimensions(file: File): Pair<Int, Int> {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            Pair(width, height)
        } finally {
            retriever.release()
        }
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
}