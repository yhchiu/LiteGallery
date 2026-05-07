package org.iurl.litegallery

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeFolderSorterTest {
    @Test
    fun sortByRecent_ordersFoldersByLatestMediaDateDescending() {
        val folders = listOf(
            folder(name = "Camera", path = "/storage/DCIM/Camera", latestDateModifiedMs = 1_000L),
            folder(name = "Screenshots", path = "/storage/Pictures/Screenshots", latestDateModifiedMs = 3_000L),
            folder(name = "Download", path = "/storage/Download", latestDateModifiedMs = 2_000L)
        )

        val sorted = HomeFolderSorter.sortByRecent(folders)

        assertEquals(
            listOf("Screenshots", "Download", "Camera"),
            sorted.map { it.name }
        )
    }

    @Test
    fun sortByRecent_placesUndatedFoldersAfterDatedFolders() {
        val folders = listOf(
            folder(name = "No Date", path = "/storage/no-date", latestDateModifiedMs = 0L),
            folder(name = "Recent", path = "/storage/recent", latestDateModifiedMs = 10L),
            folder(name = "Unknown", path = "/storage/unknown", latestDateModifiedMs = -1L)
        )

        val sorted = HomeFolderSorter.sortByRecent(folders)

        assertEquals(
            listOf("Recent", "No Date", "Unknown"),
            sorted.map { it.name }
        )
    }

    @Test
    fun sortByRecent_keepsSmbFolderAtEnd() {
        val folders = listOf(
            folder(name = "SMB", path = "smb://", latestDateModifiedMs = Long.MAX_VALUE),
            folder(name = "Camera", path = "/storage/DCIM/Camera", latestDateModifiedMs = 1_000L)
        )

        val sorted = HomeFolderSorter.sortByRecent(folders)

        assertEquals(
            listOf("Camera", "SMB"),
            sorted.map { it.name }
        )
    }

    @Test
    fun sort_ordersFoldersByDateAscending() {
        val folders = listOf(
            folder(name = "Recent", path = "/storage/recent", latestDateModifiedMs = 3_000L),
            folder(name = "Old", path = "/storage/old", latestDateModifiedMs = 1_000L),
            folder(name = "No Date", path = "/storage/no-date", latestDateModifiedMs = 0L)
        )

        val sorted = HomeFolderSorter.sort(folders, HomeFolderSorter.SORT_DATE_ASC)

        assertEquals(
            listOf("Old", "Recent", "No Date"),
            sorted.map { it.name }
        )
    }

    @Test
    fun sort_ordersFoldersByName() {
        val folders = listOf(
            folder(name = "Pictures", path = "/storage/Pictures", latestDateModifiedMs = 1_000L),
            folder(name = "Camera", path = "/storage/DCIM/Camera", latestDateModifiedMs = 3_000L),
            folder(name = "Download", path = "/storage/Download", latestDateModifiedMs = 2_000L)
        )

        val ascending = HomeFolderSorter.sort(folders, HomeFolderSorter.SORT_NAME_ASC)
        val descending = HomeFolderSorter.sort(folders, HomeFolderSorter.SORT_NAME_DESC)

        assertEquals(
            listOf("Camera", "Download", "Pictures"),
            ascending.map { it.name }
        )
        assertEquals(
            listOf("Pictures", "Download", "Camera"),
            descending.map { it.name }
        )
    }

    @Test
    fun sort_ordersFoldersBySize() {
        val folders = listOf(
            folder(name = "Small", path = "/storage/small", latestDateModifiedMs = 1_000L, totalSizeBytes = 100L),
            folder(name = "Unknown", path = "/storage/unknown", latestDateModifiedMs = 2_000L, totalSizeBytes = 0L),
            folder(name = "Large", path = "/storage/large", latestDateModifiedMs = 3_000L, totalSizeBytes = 300L)
        )

        val descending = HomeFolderSorter.sort(folders, HomeFolderSorter.SORT_SIZE_DESC)
        val ascending = HomeFolderSorter.sort(folders, HomeFolderSorter.SORT_SIZE_ASC)

        assertEquals(
            listOf("Large", "Small", "Unknown"),
            descending.map { it.name }
        )
        assertEquals(
            listOf("Small", "Large", "Unknown"),
            ascending.map { it.name }
        )
    }

    @Test
    fun parseSortOrder_fallsBackToDefaultForUnknownValues() {
        assertEquals(
            HomeFolderSorter.DEFAULT_SORT_ORDER,
            HomeFolderSorter.parseSortOrder("unsupported")
        )
    }

    private fun folder(
        name: String,
        path: String,
        latestDateModifiedMs: Long,
        totalSizeBytes: Long = 0L
    ): MediaFolder {
        return MediaFolder(
            name = name,
            path = path,
            itemCount = 1,
            totalSizeBytes = totalSizeBytes,
            latestDateModifiedMs = latestDateModifiedMs
        )
    }
}
