package org.iurl.litegallery

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class FolderMediaRepositoryTest {

    @After
    fun tearDown() {
        FolderMediaRepository.clear()
    }

    @Test
    fun getReturnsCachedItemsForFolderAndTarget() {
        val items = listOf(
            item(name = "b.jpg", path = "/photos/b.jpg"),
            item(name = "a.jpg", path = "/photos/a.jpg")
        )

        FolderMediaRepository.put(
            folderPath = "/photos",
            items = items,
            includesDeferredMetadata = true,
            sortOrder = "name_desc",
            groupBy = FolderGroupBy.DATE
        )

        val snapshot = FolderMediaRepository.get("/photos", "/photos/a.jpg")

        assertEquals(items, snapshot?.items)
        assertEquals(true, snapshot?.includesDeferredMetadata)
        assertEquals("name_desc", snapshot?.sortOrder)
        assertEquals(FolderGroupBy.DATE, snapshot?.groupBy)
    }

    @Test
    fun getReturnsNullWhenTargetIsNotInSnapshot() {
        FolderMediaRepository.put(
            folderPath = "/photos",
            items = listOf(item(path = "/photos/a.jpg"))
        )

        assertNull(FolderMediaRepository.get("/photos", "/photos/missing.jpg"))
    }

    @Test
    fun replaceItemsKeepsExistingDisplayMetadata() {
        FolderMediaRepository.put(
            folderPath = "/photos",
            items = listOf(item(path = "/photos/a.jpg")),
            includesDeferredMetadata = true,
            sortOrder = "date_asc",
            groupBy = FolderGroupBy.TYPE
        )

        FolderMediaRepository.replaceItems(
            folderPath = "/photos",
            items = listOf(item(name = "renamed.jpg", path = "/photos/renamed.jpg"))
        )

        val snapshot = FolderMediaRepository.get("/photos", "/photos/renamed.jpg")
        assertEquals(listOf("renamed.jpg"), snapshot?.items?.map { it.name })
        assertEquals(true, snapshot?.includesDeferredMetadata)
        assertEquals("date_asc", snapshot?.sortOrder)
        assertEquals(FolderGroupBy.TYPE, snapshot?.groupBy)
    }

    @Test
    fun replaceItemsUpdatesSkeletonSnapshotWithoutCreatingFullSnapshot() {
        FolderMediaRepository.putSkeleton(
            folderPath = "/photos",
            items = listOf(skeleton(id = 7L, name = "a.jpg", path = "/photos/a.jpg")),
            sortOrder = "name_asc",
            groupBy = FolderGroupBy.NONE
        )

        FolderMediaRepository.replaceItems(
            folderPath = "/photos",
            items = listOf(item(id = 7L, name = "renamed.jpg", path = "/photos/renamed.jpg"))
        )

        assertNull(FolderMediaRepository.get("/photos", "/photos/renamed.jpg"))
        val skeletonSnapshot = FolderMediaRepository.getSkeleton("/photos")
        assertEquals(listOf("renamed.jpg"), skeletonSnapshot?.items?.map { it.name })
        assertEquals("name_asc", skeletonSnapshot?.sortOrder)
        assertEquals(FolderGroupBy.NONE, skeletonSnapshot?.groupBy)
    }

    @Test
    fun getCompleteSkeletonReturnsNullForIncompleteSnapshot() {
        FolderMediaRepository.putSkeleton(
            folderPath = "/photos",
            items = listOf(skeleton(path = "/photos/a.jpg")),
            isComplete = false
        )

        assertNotNull(FolderMediaRepository.getSkeleton("/photos"))
        assertNull(FolderMediaRepository.getCompleteSkeleton("/photos", "/photos/a.jpg"))
    }

    @Test
    fun getCompleteSkeletonRequiresTargetPathWhenProvided() {
        FolderMediaRepository.putSkeleton(
            folderPath = "/photos",
            items = listOf(skeleton(path = "/photos/a.jpg")),
            isComplete = true
        )

        assertNotNull(FolderMediaRepository.getCompleteSkeleton("/photos", "/photos/a.jpg"))
        assertNull(FolderMediaRepository.getCompleteSkeleton("/photos", "/photos/missing.jpg"))
    }

    @Test
    fun replaceItemsPreservesIncompleteSkeletonSnapshot() {
        FolderMediaRepository.putSkeleton(
            folderPath = "/photos",
            items = listOf(skeleton(id = 7L, path = "/photos/a.jpg")),
            isComplete = false
        )

        FolderMediaRepository.replaceItems(
            folderPath = "/photos",
            items = listOf(item(id = 7L, name = "renamed.jpg", path = "/photos/renamed.jpg"))
        )

        val skeletonSnapshot = FolderMediaRepository.getSkeleton("/photos")
        assertEquals(listOf("renamed.jpg"), skeletonSnapshot?.items?.map { it.name })
        assertEquals(false, skeletonSnapshot?.isComplete)
        assertNull(FolderMediaRepository.getCompleteSkeleton("/photos", "/photos/renamed.jpg"))
    }

    private fun item(
        id: Long = 1L,
        name: String = "a.jpg",
        path: String = "/photos/a.jpg",
        mimeType: String = "image/jpeg"
    ): MediaItem {
        return MediaItem(
            id = id,
            name = name,
            path = path,
            dateModified = 1L,
            size = 100L,
            mimeType = mimeType
        )
    }

    private fun skeleton(
        id: Long = 1L,
        name: String = "a.jpg",
        path: String = "/photos/a.jpg"
    ): MediaItemSkeleton {
        return MediaItemSkeleton(
            id = id,
            name = name,
            path = path,
            dateModified = 1L,
            size = 100L,
            isVideo = false
        )
    }
}
