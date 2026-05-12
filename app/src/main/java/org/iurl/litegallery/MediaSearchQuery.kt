package org.iurl.litegallery

data class TimeRange(
    val startMsInclusive: Long,
    val endMsExclusive: Long
) {
    init {
        require(endMsExclusive > startMsInclusive) {
            "TimeRange must be non-empty: [$startMsInclusive, $endMsExclusive)"
        }
    }

    operator fun contains(ms: Long): Boolean =
        ms >= startMsInclusive && ms < endMsExclusive
}

enum class MediaTypeFilter {
    ALL,
    IMAGES,
    VIDEOS
}

data class MediaSearchQuery(
    val normalizedNameQuery: String = "",
    val nameMatcher: NameMatcher? = null,
    val typeFilter: MediaTypeFilter = MediaTypeFilter.ALL,
    val dateRange: TimeRange? = null,
    val sizeRangeBytes: LongRange? = null
) {
    init {
        require(normalizedNameQuery.isEmpty() == (nameMatcher == null)) {
            "normalizedNameQuery and nameMatcher must agree on empty/non-empty"
        }
    }

    val isEmpty: Boolean
        get() = nameMatcher == null &&
            typeFilter == MediaTypeFilter.ALL &&
            dateRange == null &&
            sizeRangeBytes == null

    fun matches(item: MediaItemSkeleton): Boolean {
        when (typeFilter) {
            MediaTypeFilter.IMAGES -> if (item.isVideo) return false
            MediaTypeFilter.VIDEOS -> if (!item.isVideo) return false
            MediaTypeFilter.ALL -> {}
        }

        sizeRangeBytes?.let { range ->
            if (item.size <= 0L) return false
            if (item.size !in range) return false
        }

        dateRange?.let { range ->
            if (item.dateModified !in range) return false
        }

        nameMatcher?.let { matcher ->
            if (!matcher.matches(item.name)) return false
        }

        return true
    }
}
