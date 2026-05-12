package org.iurl.litegallery

sealed interface FolderDisplayItem {
    data class Header(
        val key: String,
        val title: String,
        val count: Int
    ) : FolderDisplayItem

    data class Media(
        val skeleton: MediaItemSkeleton,
        val mediaIndex: Int
    ) : FolderDisplayItem
}
