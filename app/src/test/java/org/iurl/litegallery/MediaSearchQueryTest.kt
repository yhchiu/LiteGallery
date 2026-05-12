package org.iurl.litegallery

import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaSearchQueryTest {

    @Test
    fun isEmptyReflectsAllFilters() {
        assertTrue(MediaSearchQuery().isEmpty)
        assertFalse(MediaSearchQuery(typeFilter = MediaTypeFilter.IMAGES).isEmpty)
        assertFalse(MediaSearchQuery(dateRange = TimeRange(100L, 200L)).isEmpty)
        assertFalse(MediaSearchQuery(sizeRangeBytes = 1L..10L).isEmpty)
        assertFalse(namedQuery("img").isEmpty)
    }

    @Test
    fun nameContractRequiresMatcherAgreement() {
        assertThrows(IllegalArgumentException::class.java) {
            MediaSearchQuery(normalizedNameQuery = "abc", nameMatcher = null)
        }
        assertThrows(IllegalArgumentException::class.java) {
            MediaSearchQuery(normalizedNameQuery = "", nameMatcher = NameMatcher.Contains("foo"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            namedQuery("abc").copy(nameMatcher = null)
        }
    }

    @Test
    fun typeFilterMatchesSkeletonVideoFlag() {
        val image = item("photo.jpg", isVideo = false)
        val video = item("clip.mp4", isVideo = true)

        assertTrue(MediaSearchQuery(typeFilter = MediaTypeFilter.IMAGES).matches(image))
        assertFalse(MediaSearchQuery(typeFilter = MediaTypeFilter.IMAGES).matches(video))
        assertTrue(MediaSearchQuery(typeFilter = MediaTypeFilter.VIDEOS).matches(video))
        assertFalse(MediaSearchQuery(typeFilter = MediaTypeFilter.VIDEOS).matches(image))
        assertTrue(MediaSearchQuery(typeFilter = MediaTypeFilter.ALL).matches(video))
    }

    @Test
    fun dateRangeIsHalfOpen() {
        val query = MediaSearchQuery(dateRange = TimeRange(100L, 200L))

        assertTrue(query.matches(item("start.jpg", dateModified = 100L)))
        assertTrue(query.matches(item("inside.jpg", dateModified = 199L)))
        assertFalse(query.matches(item("end.jpg", dateModified = 200L)))
        assertFalse(query.matches(item("before.jpg", dateModified = 99L)))
    }

    @Test
    fun sizeRangeExcludesUnknownSizesOnlyWhenFilterIsPresent() {
        assertFalse(MediaSearchQuery(sizeRangeBytes = 1L..10L).matches(item("unknown.jpg", size = 0L)))
        assertTrue(MediaSearchQuery().matches(item("unknown.jpg", size = 0L)))
        assertTrue(MediaSearchQuery(sizeRangeBytes = 1L..10L).matches(item("ok.jpg", size = 5L)))
        assertFalse(MediaSearchQuery(sizeRangeBytes = 1L..10L).matches(item("large.jpg", size = 50L)))
    }

    @Test
    fun filtersAreCombinedWithAnd() {
        val query = MediaSearchQuery(
            normalizedNameQuery = "IMG*",
            nameMatcher = NameMatcher.compile("IMG*"),
            typeFilter = MediaTypeFilter.IMAGES,
            dateRange = TimeRange(100L, 200L),
            sizeRangeBytes = 1L..10L
        )

        assertTrue(query.matches(item("IMG_001.jpg", isVideo = false, dateModified = 150L, size = 5L)))
        assertFalse(query.matches(item("IMG_001.mp4", isVideo = true, dateModified = 150L, size = 5L)))
        assertFalse(query.matches(item("photo.jpg", isVideo = false, dateModified = 150L, size = 5L)))
        assertFalse(query.matches(item("IMG_001.jpg", isVideo = false, dateModified = 250L, size = 5L)))
        assertFalse(query.matches(item("IMG_001.jpg", isVideo = false, dateModified = 150L, size = 50L)))
    }

    private fun namedQuery(pattern: String): MediaSearchQuery {
        val normalized = NameMatcher.normalizePattern(pattern)
        return MediaSearchQuery(
            normalizedNameQuery = normalized,
            nameMatcher = NameMatcher.compile(pattern)
        )
    }

    private fun item(
        name: String,
        isVideo: Boolean = false,
        dateModified: Long = 150L,
        size: Long = 5L
    ): MediaItemSkeleton =
        MediaItemSkeleton(
            id = name.hashCode().toLong(),
            path = "/media/$name",
            name = name,
            dateModified = dateModified,
            size = size,
            isVideo = isVideo
        )
}
