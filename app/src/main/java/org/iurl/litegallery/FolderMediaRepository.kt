package org.iurl.litegallery

object FolderMediaRepository {
    private const val MAX_SNAPSHOTS = 2
    private const val SNAPSHOT_TTL_MS = 5 * 60 * 1000L

    data class Snapshot(
        val folderPath: String,
        val items: List<MediaItem>,
        val includesDeferredMetadata: Boolean,
        val sortOrder: String?,
        val groupBy: FolderGroupBy?,
        val cachedAtMs: Long = System.currentTimeMillis()
    )

    private val snapshots = object : LinkedHashMap<String, Snapshot>(MAX_SNAPSHOTS, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Snapshot>?): Boolean {
            return size > MAX_SNAPSHOTS
        }
    }

    @Synchronized
    fun put(
        folderPath: String,
        items: List<MediaItem>,
        includesDeferredMetadata: Boolean = false,
        sortOrder: String? = null,
        groupBy: FolderGroupBy? = null
    ) {
        if (folderPath.isBlank()) return
        val nowMs = System.currentTimeMillis()
        removeExpiredSnapshots(nowMs)
        snapshots[folderPath] = Snapshot(
            folderPath = folderPath,
            items = items,
            includesDeferredMetadata = includesDeferredMetadata,
            sortOrder = sortOrder,
            groupBy = groupBy,
            cachedAtMs = nowMs
        )
    }

    @Synchronized
    fun get(folderPath: String, targetPath: String? = null): Snapshot? {
        val nowMs = System.currentTimeMillis()
        val snapshot = snapshots[folderPath] ?: return null
        if (snapshot.isExpired(nowMs)) {
            snapshots.remove(folderPath)
            return null
        }
        if (!targetPath.isNullOrBlank() && snapshot.items.none { it.path == targetPath }) {
            return null
        }
        return snapshot
    }

    @Synchronized
    fun replaceItems(folderPath: String?, items: List<MediaItem>) {
        if (folderPath.isNullOrBlank()) return
        val existing = snapshots[folderPath]
        put(
            folderPath = folderPath,
            items = items,
            includesDeferredMetadata = existing?.includesDeferredMetadata ?: false,
            sortOrder = existing?.sortOrder,
            groupBy = existing?.groupBy
        )
    }

    @Synchronized
    fun invalidate(folderPath: String?) {
        if (folderPath.isNullOrBlank()) return
        snapshots.remove(folderPath)
    }

    @Synchronized
    fun clear() {
        snapshots.clear()
    }

    private fun removeExpiredSnapshots(nowMs: Long) {
        val iterator = snapshots.entries.iterator()
        while (iterator.hasNext()) {
            if (iterator.next().value.isExpired(nowMs)) {
                iterator.remove()
            }
        }
    }

    private fun Snapshot.isExpired(nowMs: Long): Boolean {
        return nowMs - cachedAtMs > SNAPSHOT_TTL_MS
    }
}
