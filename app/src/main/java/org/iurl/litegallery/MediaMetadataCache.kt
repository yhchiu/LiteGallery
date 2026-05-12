package org.iurl.litegallery

import android.util.LruCache

object MediaMetadataCache {
    private const val MAX_ENTRIES = 1_000
    private val cache = LruCache<Long, MediaItem>(MAX_ENTRIES)

    @Synchronized
    fun put(item: MediaItem) {
        if (item.id <= MediaItem.NO_MEDIASTORE_ID) return
        cache.put(item.id, item)
    }

    @Synchronized
    fun get(skeleton: MediaItemSkeleton): MediaItem? {
        if (skeleton.id <= MediaItem.NO_MEDIASTORE_ID) return null
        return cache.get(skeleton.id)
    }

    @Synchronized
    fun get(id: Long): MediaItem? {
        if (id <= MediaItem.NO_MEDIASTORE_ID) return null
        return cache.get(id)
    }

    @Synchronized
    fun remove(id: Long) {
        if (id <= MediaItem.NO_MEDIASTORE_ID) return
        cache.remove(id)
    }

    @Synchronized
    fun updatePath(id: Long, item: MediaItem) {
        if (id <= MediaItem.NO_MEDIASTORE_ID) return
        cache.put(id, item)
    }

    @Synchronized
    fun clear() {
        cache.evictAll()
    }
}
