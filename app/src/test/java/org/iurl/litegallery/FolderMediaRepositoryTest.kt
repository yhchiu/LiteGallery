package org.iurl.litegallery

import org.junit.After
import org.junit.Assert.assertEquals
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

    private fun item(
        name: String = "a.jpg",
        path: String = "/photos/a.jpg",
        mimeType: String = "image/jpeg"
    ): MediaItem {
        return MediaItem(
            name = name,
            path = path,
            dateModified = 1L,
            size = 100L,
            mimeType = mimeType
        )
    }
}
