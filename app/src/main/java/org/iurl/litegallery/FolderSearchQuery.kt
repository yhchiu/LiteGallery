package org.iurl.litegallery

data class FolderSearchQuery(
    val normalizedNameQuery: String = "",
    val nameMatcher: NameMatcher? = null,
    val dateRange: TimeRange? = null,
    val sizeRangeBytes: LongRange? = null
) {
    init {
        require(normalizedNameQuery.isEmpty() == (nameMatcher == null)) {
            "normalizedNameQuery and nameMatcher must agree on empty/non-empty"
        }
    }

    val isEmpty: Boolean
        get() = nameMatcher == null && dateRange == null && sizeRangeBytes == null

    fun matches(folder: MediaFolder): Boolean {
        sizeRangeBytes?.let { range ->
            if (folder.totalSizeBytes <= 0L) return false
            if (folder.totalSizeBytes !in range) return false
        }

        dateRange?.let { range ->
            if (folder.latestDateModifiedMs !in range) return false
        }

        nameMatcher?.let { matcher ->
            if (!matcher.matches(folder.name)) return false
        }

        return true
    }
}
