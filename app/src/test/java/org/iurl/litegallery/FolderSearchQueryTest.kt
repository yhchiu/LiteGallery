package org.iurl.litegallery

import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class FolderSearchQueryTest {

    @Test
    fun nameContractRequiresMatcherAgreement() {
        assertThrows(IllegalArgumentException::class.java) {
            FolderSearchQuery(normalizedNameQuery = "camera", nameMatcher = null)
        }
        assertThrows(IllegalArgumentException::class.java) {
            FolderSearchQuery(normalizedNameQuery = "", nameMatcher = NameMatcher.Contains("camera"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            namedQuery("camera").copy(nameMatcher = null)
        }
    }

    @Test
    fun matchesNameDateAndSize() {
        val query = FolderSearchQuery(
            normalizedNameQuery = "cam*",
            nameMatcher = NameMatcher.compile("cam*"),
            dateRange = TimeRange(100L, 200L),
            sizeRangeBytes = 1_000L..2_000L
        )

        assertTrue(query.matches(folder("Camera", latestDateModifiedMs = 150L, totalSizeBytes = 1_500L)))
        assertFalse(query.matches(folder("Downloads", latestDateModifiedMs = 150L, totalSizeBytes = 1_500L)))
        assertFalse(query.matches(folder("Camera", latestDateModifiedMs = 250L, totalSizeBytes = 1_500L)))
        assertFalse(query.matches(folder("Camera", latestDateModifiedMs = 150L, totalSizeBytes = 3_000L)))
    }

    @Test
    fun sizeFilterExcludesUnknownFolderSizes() {
        assertFalse(FolderSearchQuery(sizeRangeBytes = 1L..10L).matches(folder("Camera", totalSizeBytes = 0L)))
        assertTrue(FolderSearchQuery().matches(folder("Camera", totalSizeBytes = 0L)))
    }

    private fun namedQuery(pattern: String): FolderSearchQuery {
        val normalized = NameMatcher.normalizePattern(pattern)
        return FolderSearchQuery(
            normalizedNameQuery = normalized,
            nameMatcher = NameMatcher.compile(pattern)
        )
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
