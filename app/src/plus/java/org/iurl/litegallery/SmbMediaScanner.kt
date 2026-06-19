package org.iurl.litegallery

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext

/**
 * Scans SMB shares for media files and folders.
 */
class SmbMediaScanner(private val context: Context) {

    private val imageExtensions = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif")
    private val videoExtensions = setOf("mp4", "avi", "mov", "mkv", "3gp", "webm", "m4v", "flv")

    /**
     * Scan an SMB directory for media items (files only, not recursive).
     */
    suspend fun scanSmbMediaInFolder(folderSmbPath: String): List<MediaItem> = withContext(Dispatchers.IO) {
        val smbPath = SmbPath.parse(folderSmbPath) ?: return@withContext emptyList()
        val items = mutableListOf<MediaItem>()

        try {
            val files = SmbClient.listFiles(context, smbPath.host, smbPath.share, smbPath.path)

            for (file in files) {
                if (file.isDirectory || !file.isMedia) continue

                val fileSmbPath = SmbPath.toUrl(smbPath.host, smbPath.share, file.path)
                val ext = file.name.substringAfterLast('.', "").lowercase()

                items.add(
                    MediaItem(
                        id = MediaItem.NO_MEDIASTORE_ID,
                        name = file.name,
                        path = fileSmbPath,
                        dateModified = file.lastModified,
                        size = file.size,
                        mimeType = getMimeType(ext, file.isVideo),
                        duration = 0, // Cannot easily get duration from SMB without downloading
                        width = 0,
                        height = 0
                    )
                )
            }
        } catch (e: Exception) {
            android.util.Log.e("SmbMediaScanner", "Failed to scan SMB media in folder: $folderSmbPath", e)
        }

        items.sortedByDescending { it.dateModified }
    }

    /**
     * Scan an SMB directory directly to lightweight skeleton representations.
     */
    suspend fun scanSmbSkeletonsInFolder(folderSmbPath: String): List<MediaItemSkeleton> = withContext(Dispatchers.IO) {
        val fullItems = scanSmbMediaInFolder(folderSmbPath)
        fullItems.map { item ->
            MediaItemSkeleton(
                id = item.id,
                path = item.path,
                name = item.name,
                dateModified = item.dateModified,
                size = item.size,
                isVideo = item.isVideo
            )
        }
    }

    /**
     * Stream scanned SMB media items chunk by chunk using a Coroutine Flow.
     */
    fun scanSmbMediaInFolderStreamed(folderSmbPath: String): Flow<LoadEvent> = flow {
        val skeletons = scanSmbSkeletonsInFolder(folderSmbPath)
        MediaScanner.streamSkeletonListLoadEvents(skeletons).collect { emit(it) }
    }

    /**
     * List directories and media files in an SMB path (for browsing).
     */
    suspend fun listSmbDirectory(smbUrl: String): List<SmbClient.SmbFileInfo> = withContext(Dispatchers.IO) {
        val smbPath = SmbPath.parse(smbUrl) ?: return@withContext emptyList()
        try {
            SmbClient.listFiles(context, smbPath.host, smbPath.share, smbPath.path)
                .sortedWith(compareByDescending<SmbClient.SmbFileInfo> { it.isDirectory }.thenBy { it.name.lowercase() })
        } catch (e: Exception) {
            android.util.Log.e("SmbMediaScanner", "Failed to list directory: $smbUrl", e)
            emptyList()
        }
    }

    private fun getMimeType(extension: String, isVideo: Boolean): String {
        return if (isVideo) {
            when (extension) {
                "mp4" -> "video/mp4"
                "avi" -> "video/x-msvideo"
                "mov" -> "video/quicktime"
                "mkv" -> "video/x-matroska"
                "3gp" -> "video/3gpp"
                "webm" -> "video/webm"
                "m4v" -> "video/mp4"
                "flv" -> "video/x-flv"
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
