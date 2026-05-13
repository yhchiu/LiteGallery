package org.iurl.litegallery

import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery

object MediaIndexSearchSql {
    fun buildQuery(
        query: MediaSearchQuery,
        sortOrder: String,
        limit: Int,
        offset: Int
    ): SupportSQLiteQuery {
        val sql = StringBuilder("SELECT * FROM media_items")
        val args = mutableListOf<Any>()
        val where = mutableListOf<String>()

        MediaSearchSql.likePatternForNormalizedName(query.normalizedNameQuery)?.let { pattern ->
            where += "name COLLATE NOCASE LIKE ? ESCAPE '\\'"
            args += pattern
        }

        mediaTypeFor(query.typeFilter)?.let { mediaType ->
            where += "mediaType = ?"
            args += mediaType
        }

        query.dateRange?.let { range ->
            where += "dateModifiedMs >= ?"
            args += range.startMsInclusive
            where += "dateModifiedMs < ?"
            args += range.endMsExclusive
        }

        query.sizeRangeBytes?.let { range ->
            where += "sizeBytes > 0"
            where += "sizeBytes >= ?"
            args += range.first
            where += "sizeBytes <= ?"
            args += range.last
        }

        if (where.isNotEmpty()) {
            sql.append(" WHERE ")
            sql.append(where.joinToString(" AND "))
        }

        sql.append(" ORDER BY ")
        sql.append(orderByClause(sortOrder))
        sql.append(" LIMIT ? OFFSET ?")
        args += limit.coerceAtLeast(0)
        args += offset.coerceAtLeast(0)

        return SimpleSQLiteQuery(sql.toString(), args.toTypedArray())
    }

    private fun mediaTypeFor(filter: MediaTypeFilter): String? {
        return when (filter) {
            MediaTypeFilter.IMAGES -> MEDIA_INDEX_TYPE_IMAGE
            MediaTypeFilter.VIDEOS -> MEDIA_INDEX_TYPE_VIDEO
            MediaTypeFilter.ALL -> null
        }
    }

    private fun orderByClause(sortOrder: String): String {
        return when (sortOrder) {
            "date_asc" -> "dateModifiedMs ASC, name COLLATE NOCASE ASC, path ASC"
            "name_asc" -> "name COLLATE NOCASE ASC, dateModifiedMs DESC, path ASC"
            "name_desc" -> "name COLLATE NOCASE DESC, dateModifiedMs DESC, path ASC"
            "size_desc" -> "CASE WHEN sizeBytes > 0 THEN 0 ELSE 1 END ASC, sizeBytes DESC, name COLLATE NOCASE ASC, path ASC"
            "size_asc" -> "CASE WHEN sizeBytes > 0 THEN 0 ELSE 1 END ASC, sizeBytes ASC, name COLLATE NOCASE ASC, path ASC"
            else -> "dateModifiedMs DESC, name COLLATE NOCASE ASC, path ASC"
        }
    }
}
