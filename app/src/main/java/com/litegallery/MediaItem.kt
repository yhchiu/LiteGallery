package com.litegallery

import java.io.File

data class MediaItem(
    val name: String,
    val path: String,
    val dateModified: Long,
    val size: Long,
    val mimeType: String,
    val duration: Long = 0, // Duration in milliseconds for videos
    val isVideo: Boolean = mimeType.startsWith("video/")
) {
    fun getFile(): File = File(path)
    
    val extension: String
        get() = File(path).extension
    
    val nameWithoutExtension: String
        get() = File(path).nameWithoutExtension
    
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