package org.iurl.litegallery

/**
 * Single source of truth for mapping a file extension (or path / file name) to a MIME type.
 *
 * Extracted so filesystem scanning, the trash bin and the media viewer all agree on standard
 * types. In particular `.jpg` / `.jpeg` map to the standard `image/jpeg`, never the
 * non-standard `image/jpg` that some external share / edit targets mishandle.
 */
object MediaMimeTypes {

    private val videoExtensions = setOf("mp4", "avi", "mov", "mkv", "3gp", "webm", "m4v", "flv")

    /** MIME type for a bare file [extension] (a leading dot and letter case are ignored). */
    fun fromExtension(extension: String): String {
        val ext = extension.removePrefix(".").lowercase()
        return when (ext) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "bmp" -> "image/bmp"
            "heic" -> "image/heic"
            "heif" -> "image/heif"
            "mp4" -> "video/mp4"
            "avi" -> "video/x-msvideo"
            "mov" -> "video/quicktime"
            "mkv" -> "video/x-matroska"
            "3gp" -> "video/3gpp"
            "webm" -> "video/webm"
            else -> if (ext in videoExtensions) "video/*" else "image/*"
        }
    }

    /** MIME type inferred from the extension of a path or file name. */
    fun fromPath(path: String): String = fromExtension(path.substringAfterLast('.', ""))
}
