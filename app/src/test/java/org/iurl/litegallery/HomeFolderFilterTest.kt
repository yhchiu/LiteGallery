package org.iurl.litegallery

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeFolderFilterTest {

    @Test
    fun nullOrEmptyQueryReturnsOriginalList() {
        val folders = listOf(folder("Camera"), folder("Downloads"))

        assertEquals(folders, HomeFolderFilter.apply(folders, null))
        assertEquals(folders, HomeFolderFilter.apply(folders, FolderSearchQuery()))
    }

    @Test
    fun filtersFoldersWithQuery() {
        val query = FolderSearchQuery(
            normalizedNameQuery = "cam*",
            nameMatcher = NameMatcher.compile("cam*"),
            dateRange = TimeRange(100L, 200L),
            sizeRangeBytes = 1_000L..2_000L
        )
        val folders = listOf(
            folder("Camera", latestDateModifiedMs = 150L, totalSizeBytes = 1_500L),
            folder("Camera Old", latestDateModifiedMs = 250L, totalSizeBytes = 1_500L),
            folder("Downloads", latestDateModifiedMs = 150L, totalSizeBytes = 1_500L)
        )

        assertEquals(listOf("Camera"), HomeFolderFilter.apply(folders, query).map { it.name })
    }

    private fun folder(
        name: String,
        latestDateModifiedMs: Long = 150L,
        totalSizeBytes: Long = 1_500L
    ): MediaFolder =
        MediaFolder(
            name = name,
            path = "/media/$name",
            itemCount = 1,
            latestDateModifiedMs = latestDateModifiedMs,
            totalSizeBytes = totalSizeBytes
        )
}
