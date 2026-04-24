package org.iurl.litegallery

import java.io.File

data class MediaItem(
    val name: String,
    val path: String,
    val dateModified: Long,
    val size: Long,
    val mimeType: String,
    val duration: Long = 0, // Duration in milliseconds for videos
    val width: Int = 0, // Width in pixels
    val height: Int = 0, // Height in pixels
    val isVideo: Boolean = mimeType.startsWith("video/")
) {
    /** Whether this item is from an SMB share. */
    val isSmb: Boolean
        get() = SmbPath.isSmb(path)

    fun getFile(): File {
        if (isSmb) throw UnsupportedOperationException("Cannot create File from SMB path: $path")
        return File(path)
    }
    
    val extension: String
        get() = name.substringAfterLast('.', "")
    
    val nameWithoutExtension: String
        get() = name.substringBeforeLast('.', name)
    
    fun getFormattedDuration(): String {
        if (!isVideo || duration <= 0) return ""
        
        val seconds = duration / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        
        return if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes % 60, seconds % 60)
        } else {
            String.format("%d:%02d", minutes, seconds % 60)
        }
    }
}