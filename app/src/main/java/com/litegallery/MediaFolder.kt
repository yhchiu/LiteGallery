package com.litegallery

import java.io.File

data class MediaFolder(
    val name: String,
    val path: String,
    val itemCount: Int,
    val thumbnail: String? = null
) {
    fun getFile(): File = File(path)
}