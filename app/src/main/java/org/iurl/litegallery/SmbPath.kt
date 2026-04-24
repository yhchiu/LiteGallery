package org.iurl.litegallery

/**
 * Helper class for parsing and constructing SMB paths.
 * Format: smb://host/share/path/to/file.ext
 */
data class SmbPath(
    val host: String,
    val share: String,
    val path: String  // relative path within share, e.g., "folder/file.mp4"
) {
    /** Full smb:// URL */
    val fullPath: String
        get() = if (path.isEmpty()) "smb://$host/$share" else "smb://$host/$share/$path"

    /** File name extracted from path */
    val fileName: String
        get() = path.substringAfterLast('/')

    /** File name without extension */
    val fileNameWithoutExtension: String
        get() = fileName.substringBeforeLast('.', fileName)

    /** File extension */
    val fileExtension: String
        get() = fileName.substringAfterLast('.', "")

    /** Parent directory path within the share */
    val parentPath: String
        get() = path.substringBeforeLast('/', "")

    /** Full parent folder path as smb:// URL */
    val parentFolderPath: String
        get() = if (parentPath.isEmpty()) "smb://$host/$share" else "smb://$host/$share/$parentPath"

    /** SMB path used with smbj (backslash-separated, no leading backslash) */
    val smbjPath: String
        get() = path.replace('/', '\\')

    companion object {
        /**
         * Parse an smb:// URL string into SmbPath components.
         * Returns null if the URL format is invalid.
         */
        fun parse(smbUrl: String): SmbPath? {
            if (!isSmb(smbUrl)) return null
            val withoutScheme = smbUrl.removePrefix("smb://")
            val parts = withoutScheme.split("/", limit = 3)
            if (parts.size < 2 || parts[0].isBlank() || parts[1].isBlank()) return null
            return SmbPath(
                host = parts[0],
                share = parts[1],
                path = parts.getOrElse(2) { "" }.trimEnd('/')
            )
        }

        /**
         * Check if a path string is an SMB path.
         */
        fun isSmb(path: String): Boolean = path.startsWith("smb://")
    }
}
