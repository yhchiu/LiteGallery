package org.iurl.litegallery

object HomeSearchQueryFactory {
    fun buildGlobalMediaQuery(
        normalizedName: String,
        typeFilter: MediaTypeFilter,
        dateRange: TimeRange?,
        sizeRangeBytes: LongRange?
    ): MediaSearchQuery? {
        val query = MediaSearchQuery(
            normalizedNameQuery = normalizedName,
            nameMatcher = NameMatcher.compile(normalizedName),
            typeFilter = typeFilter,
            dateRange = dateRange,
            sizeRangeBytes = sizeRangeBytes
        )
        return query.takeUnless { it.isEmpty }
    }
}
