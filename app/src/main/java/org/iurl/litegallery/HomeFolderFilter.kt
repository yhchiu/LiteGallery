package org.iurl.litegallery

object HomeFolderFilter {
    fun apply(folders: List<MediaFolder>, query: FolderSearchQuery?): List<MediaFolder> {
        if (query == null || query.isEmpty || folders.isEmpty()) return folders
        return folders.filter { query.matches(it) }
    }
}
