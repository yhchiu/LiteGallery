package org.iurl.litegallery

import android.content.ContentUris
import android.net.Uri
import android.provider.MediaStore

/**
 * A highly optimized, memory-efficient skeleton representation of a media item.
 * Designed to reside entirely in memory for massive folders (50,000+ items) to enable
 * instant sorting, multi-dimensional grouping, and absolute scrollbar positioning
 * without risking OutOfMemory errors or GC pauses.
 */
data class MediaItemSkeleton(
    val id: Long,
    val path: String,
    val name: String,
    val dateModified: Long,
    val size: Long,
    val isVideo: Boolean
) {
    /** Whether this item is from an SMB share. */
    val isSmb: Boolean
        get() = SmbPath.isSmb(path)

    fun toMediaItem(): MediaItem {
        return toBaselineMediaItem()
    }

    fun toBaselineMediaItem(): MediaItem {
        return MediaItem(
            id = id,
            name = name,
            path = path,
            dateModified = dateModified,
            size = size,
            mimeType = if (isVideo) "video/*" else "image/*",
            duration = 0L,
            width = 0,
            height = 0
        )
    }

    fun thumbnailModel(): Any {
        if (isSmb) return path
        if (path.startsWith("content://")) return Uri.parse(path)
        if (id > MediaItem.NO_MEDIASTORE_ID) {
            val baseUri = if (isVideo) {
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            } else {
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            }
            return ContentUris.withAppendedId(baseUri, id)
        }
        return path
    }
}
