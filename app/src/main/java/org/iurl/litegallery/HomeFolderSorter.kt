package org.iurl.litegallery

import java.util.Locale

object HomeFolderSorter {
    const val SORT_DATE_DESC = "date_desc"
    const val SORT_DATE_ASC = "date_asc"
    const val SORT_NAME_ASC = "name_asc"
    const val SORT_NAME_DESC = "name_desc"
    const val SORT_SIZE_DESC = "size_desc"
    const val SORT_SIZE_ASC = "size_asc"
    const val DEFAULT_SORT_ORDER = SORT_DATE_DESC

    fun parseSortOrder(sortOrder: String?): String {
        return when (sortOrder) {
            SORT_DATE_DESC,
            SORT_DATE_ASC,
            SORT_NAME_ASC,
            SORT_NAME_DESC,
            SORT_SIZE_DESC,
            SORT_SIZE_ASC -> sortOrder
            else -> DEFAULT_SORT_ORDER
        }
    }

    fun sort(folders: List<MediaFolder>, sortOrder: String = DEFAULT_SORT_ORDER): List<MediaFolder> {
        return folders.sortedWith(
            when (parseSortOrder(sortOrder)) {
                SORT_DATE_ASC -> recentComparator(ascending = true)
                SORT_NAME_ASC -> nameComparator(descending = false)
                SORT_NAME_DESC -> nameComparator(descending = true)
                SORT_SIZE_DESC -> sizeComparator(ascending = false)
                SORT_SIZE_ASC -> sizeComparator(ascending = true)
                else -> recentComparator(ascending = false)
            }
        )
    }

    fun sortByRecent(folders: List<MediaFolder>): List<MediaFolder> {
        return sort(folders, SORT_DATE_DESC)
    }

    private fun recentComparator(ascending: Boolean): Comparator<MediaFolder> {
        val comparator = compareBy<MediaFolder> { SmbPath.isSmb(it.path) }
            .thenBy { it.latestDateModifiedMs <= 0L }

        return if (ascending) {
            comparator
                .thenBy { it.latestDateModifiedMs }
                .thenBy { it.normalizedName() }
                .thenBy { it.normalizedPath() }
        } else {
            comparator
                .thenByDescending { it.latestDateModifiedMs }
                .thenBy { it.normalizedName() }
                .thenBy { it.normalizedPath() }
        }
    }

    private fun nameComparator(descending: Boolean): Comparator<MediaFolder> {
        val comparator = compareBy<MediaFolder> { SmbPath.isSmb(it.path) }

        return if (descending) {
            comparator
                .thenByDescending { it.normalizedName() }
                .thenBy { it.normalizedPath() }
        } else {
            comparator
                .thenBy { it.normalizedName() }
                .thenBy { it.normalizedPath() }
        }
    }

    private fun sizeComparator(ascending: Boolean): Comparator<MediaFolder> {
        val comparator = compareBy<MediaFolder> { SmbPath.isSmb(it.path) }
            .thenBy { it.totalSizeBytes <= 0L }

        return if (ascending) {
            comparator
                .thenBy { it.totalSizeBytes }
                .thenBy { it.normalizedName() }
                .thenBy { it.normalizedPath() }
        } else {
            comparator
                .thenByDescending { it.totalSizeBytes }
                .thenBy { it.normalizedName() }
                .thenBy { it.normalizedPath() }
        }
    }

    private fun MediaFolder.normalizedName(): String = name.lowercase(Locale.ROOT)

    private fun MediaFolder.normalizedPath(): String = path.lowercase(Locale.ROOT)
}
