package org.iurl.litegallery

import java.io.File

data class MediaFolder(
    val name: String,
    val path: String,
    val itemCount: Int,
    val thumbnail: String? = null,
    val imageCount: Int = 0,
    val videoCount: Int = 0,
    val totalSizeBytes: Long = 0L,
    val latestDateModifiedMs: Long = 0L
) {
    fun getFile(): File = File(path)
}
