package org.iurl.litegallery

import androidx.room.Entity
import androidx.room.Index
import java.io.File

internal const val MEDIA_INDEX_TYPE_IMAGE = "image"
internal const val MEDIA_INDEX_TYPE_VIDEO = "video"

@Entity(
    tableName = "media_items",
    primaryKeys = ["mediaType", "mediaStoreId"],
    indices = [
        Index(value = ["path"], unique = true),
        Index(value = ["folderPath"]),
        Index(value = ["dateModifiedMs"]),
        Index(value = ["lastSeenScanId"])
    ]
)
data class MediaIndexEntity(
    val mediaType: String,
    val mediaStoreId: Long,
    val path: String,
    val folderPath: String,
    val name: String,
    val dateModifiedMs: Long,
    val sizeBytes: Long,
    val mimeType: String,
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val generationAdded: Long,
    val generationModified: Long,
    val lastSeenScanId: Long,
    val updatedAtMs: Long
)

@Entity(
    tableName = "folder_index",
    primaryKeys = ["path"],
    indices = [Index(value = ["latestDateModifiedMs"])]
)
data class FolderIndexEntity(
    val path: String,
    val name: String,
    val itemCount: Int,
    val thumbnail: String?,
    val imageCount: Int,
    val videoCount: Int,
    val totalSizeBytes: Long,
    val latestDateModifiedMs: Long,
    val updatedAtMs: Long
)

@Entity(tableName = "media_sync_state", primaryKeys = ["volumeName"])
data class MediaSyncStateEntity(
    val volumeName: String,
    val version: String?,
    val generation: Long,
    val syncedAtMs: Long
)

data class FolderAggregateRow(
    val path: String,
    val itemCount: Long,
    val imageCount: Long,
    val videoCount: Long,
    val totalSizeBytes: Long,
    val latestDateModifiedMs: Long,
    val thumbnail: String?
)

fun MediaIndexEntity.toMediaItem(): MediaItem {
    return MediaItem(
        id = mediaStoreId,
        name = name,
        path = path,
        dateModified = dateModifiedMs,
        size = sizeBytes,
        mimeType = mimeType,
        duration = durationMs,
        width = width,
        height = height
    )
}

fun FolderIndexEntity.toMediaFolder(): MediaFolder {
    return MediaFolder(
        name = name,
        path = path,
        itemCount = itemCount,
        thumbnail = thumbnail,
        imageCount = imageCount,
        videoCount = videoCount,
        totalSizeBytes = totalSizeBytes,
        latestDateModifiedMs = latestDateModifiedMs
    )
}

fun FolderAggregateRow.toFolderIndexEntity(updatedAtMs: Long): FolderIndexEntity {
    return FolderIndexEntity(
        path = path,
        name = File(path).name.ifBlank { path },
        itemCount = itemCount.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
        thumbnail = thumbnail,
        imageCount = imageCount.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
        videoCount = videoCount.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
        totalSizeBytes = totalSizeBytes,
        latestDateModifiedMs = latestDateModifiedMs.coerceAtLeast(0L),
        updatedAtMs = updatedAtMs
    )
}
