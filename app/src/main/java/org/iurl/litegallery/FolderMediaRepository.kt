package org.iurl.litegallery

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

sealed class LoadEvent {
    data class FirstScreen(val items: List<MediaItemSkeleton>) : LoadEvent()
    data class Progress(
        val deltaItems: List<MediaItemSkeleton>,
        val totalLoaded: Int,
        val isFinal: Boolean
    ) : LoadEvent()
    data class Failed(val error: Throwable) : LoadEvent()
}

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

    data class SkeletonSnapshot(
        val folderPath: String,
        val items: List<MediaItemSkeleton>,
        val sortOrder: String?,
        val groupBy: FolderGroupBy?,
        val cachedAtMs: Long = System.currentTimeMillis()
    )

    private val snapshots = object : LinkedHashMap<String, Snapshot>(MAX_SNAPSHOTS, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Snapshot>?): Boolean {
            return size > MAX_SNAPSHOTS
        }
    }

    private val skeletonSnapshots = object : LinkedHashMap<String, SkeletonSnapshot>(MAX_SNAPSHOTS, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, SkeletonSnapshot>?): Boolean {
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
    fun putSkeleton(
        folderPath: String,
        items: List<MediaItemSkeleton>,
        sortOrder: String? = null,
        groupBy: FolderGroupBy? = null
    ) {
        if (folderPath.isBlank()) return
        val nowMs = System.currentTimeMillis()
        removeExpiredSkeletonSnapshots(nowMs)
        skeletonSnapshots[folderPath] = SkeletonSnapshot(
            folderPath = folderPath,
            items = items,
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
    fun getSkeleton(folderPath: String): SkeletonSnapshot? {
        val nowMs = System.currentTimeMillis()
        val snapshot = skeletonSnapshots[folderPath] ?: return null
        if (nowMs - snapshot.cachedAtMs > SNAPSHOT_TTL_MS) {
            skeletonSnapshots.remove(folderPath)
            return null
        }
        return snapshot
    }

    @Synchronized
    fun replaceItems(folderPath: String?, items: List<MediaItem>) {
        if (folderPath.isNullOrBlank()) return
        val existing = snapshots[folderPath]
        if (existing != null) {
            put(
                folderPath = folderPath,
                items = items,
                includesDeferredMetadata = existing.includesDeferredMetadata,
                sortOrder = existing.sortOrder,
                groupBy = existing.groupBy
            )
        }
        val existingSkeleton = skeletonSnapshots[folderPath]
        if (existingSkeleton != null) {
            putSkeleton(
                folderPath = folderPath,
                items = items.map {
                    MediaItemSkeleton(
                        id = it.id,
                        path = it.path,
                        name = it.name,
                        dateModified = it.dateModified,
                        size = it.size,
                        isVideo = it.isVideo
                    )
                },
                sortOrder = existingSkeleton.sortOrder,
                groupBy = existingSkeleton.groupBy
            )
        }
    }

    @Synchronized
    fun replaceSkeletonItems(folderPath: String?, items: List<MediaItemSkeleton>) {
        if (folderPath.isNullOrBlank()) return
        val existing = skeletonSnapshots[folderPath]
        putSkeleton(
            folderPath = folderPath,
            items = items,
            sortOrder = existing?.sortOrder,
            groupBy = existing?.groupBy
        )
    }

    @Synchronized
    fun invalidate(folderPath: String?) {
        if (folderPath.isNullOrBlank()) return
        snapshots.remove(folderPath)
        skeletonSnapshots.remove(folderPath)
    }

    @Synchronized
    fun clear() {
        snapshots.clear()
        skeletonSnapshots.clear()
    }

    fun loadFolderStreamed(context: Context, folderPath: String): Flow<LoadEvent> = flow {
        try {
            if (SmbPath.isSmb(folderPath)) {
                val smbScanner = SmbMediaScanner(context)
                smbScanner.scanSmbMediaInFolderStreamed(folderPath).collect { emit(it) }
            } else {
                val mediaScanner = MediaScanner(context)
                mediaScanner.scanMediaInFolderStreamed(folderPath).collect { emit(it) }
            }
        } catch (e: Throwable) {
            emit(LoadEvent.Failed(e))
        }
    }.flowOn(Dispatchers.IO)

    private fun removeExpiredSnapshots(nowMs: Long) {
        val iterator = snapshots.entries.iterator()
        while (iterator.hasNext()) {
            if (iterator.next().value.isExpired(nowMs)) {
                iterator.remove()
            }
        }
    }

    private fun removeExpiredSkeletonSnapshots(nowMs: Long) {
        val iterator = skeletonSnapshots.entries.iterator()
        while (iterator.hasNext()) {
            if (nowMs - iterator.next().value.cachedAtMs > SNAPSHOT_TTL_MS) {
                iterator.remove()
            }
        }
    }

    private fun Snapshot.isExpired(nowMs: Long): Boolean {
        return nowMs - cachedAtMs > SNAPSHOT_TTL_MS
    }
}
