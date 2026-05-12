package org.iurl.litegallery

internal object MediaMetadataPolicy {
    fun needsDetailedMetadata(item: MediaItem): Boolean {
        return item.size <= 0L ||
            item.width <= 0 ||
            item.height <= 0 ||
            (item.isVideo && item.duration <= 0L)
    }
}
