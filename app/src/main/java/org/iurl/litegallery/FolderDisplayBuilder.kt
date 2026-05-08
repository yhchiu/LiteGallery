package org.iurl.litegallery

import java.util.Calendar
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

data class FolderDisplayLabels(
    val unknownDate: String,
    val unknownSize: String,
    val imageType: String,
    val videoType: String,
    val otherType: String,
    val formatSize: (Long) -> String
)

data class FolderDisplayResult(
    val sortedMediaItems: List<MediaItem>,
    val displayItems: List<FolderDisplayItem>,
    val isGrouped: Boolean
)

object FolderDisplayBuilder {
    private const val TARGET_SIZE_BUCKETS = 5

    fun build(
        items: List<MediaItem>,
        sortOrder: String,
        groupBy: FolderGroupBy,
        labels: FolderDisplayLabels
    ): FolderDisplayResult {
        val sortedItems = sortMediaItems(items, sortOrder)
        if (groupBy == FolderGroupBy.NONE || sortedItems.isEmpty()) {
            return FolderDisplayResult(
                sortedMediaItems = sortedItems,
                displayItems = emptyList(),
                isGrouped = false
            )
        }

        return FolderDisplayResult(
            sortedMediaItems = sortedItems,
            displayItems = buildDisplayItems(sortedItems, groupBy, labels),
            isGrouped = true
        )
    }

    fun sortMediaItems(items: List<MediaItem>, sortOrder: String): List<MediaItem> {
        return when (sortOrder) {
            "date_desc" -> items.sortedByDescending { it.dateModified }
            "date_asc" -> items.sortedBy { it.dateModified }
            "name_asc" -> items.sortedBy { it.name.lowercase(Locale.getDefault()) }
            "name_desc" -> items.sortedByDescending { it.name.lowercase(Locale.getDefault()) }
            "size_desc" -> items.sortedWith(sizeDescendingComparator)
            "size_asc" -> items.sortedWith(sizeAscendingComparator)
            else -> items.sortedByDescending { it.dateModified }
        }
    }

    private val sizeDescendingComparator = compareBy<MediaItem> { it.size <= 0L }
        .thenByDescending { it.size }

    private val sizeAscendingComparator = compareBy<MediaItem> { it.size <= 0L }
        .thenBy { it.size }

    private fun buildDisplayItems(
        sortedItems: List<MediaItem>,
        groupBy: FolderGroupBy,
        labels: FolderDisplayLabels
    ): List<FolderDisplayItem> {
        val groupSelector = createGroupSelector(sortedItems, groupBy, labels)
        val groups = linkedMapOf<GroupInfo, MutableList<FolderDisplayItem.Media>>()
        sortedItems.forEachIndexed { index, item ->
            val group = groupSelector(item)
            groups.getOrPut(group) { mutableListOf() }
                .add(FolderDisplayItem.Media(item, mediaIndex = index))
        }

        val displayItems = ArrayList<FolderDisplayItem>(sortedItems.size + groups.size)
        groups.forEach { (group, media) ->
            displayItems.add(FolderDisplayItem.Header(group.key, group.title, media.size))
            displayItems.addAll(media)
        }
        return displayItems
    }

    private fun createGroupSelector(
        items: List<MediaItem>,
        groupBy: FolderGroupBy,
        labels: FolderDisplayLabels
    ): (MediaItem) -> GroupInfo {
        return when (groupBy) {
            FolderGroupBy.DATE -> createDateGroupSelector(labels)
            FolderGroupBy.NAME -> { item -> nameGroup(item) }
            FolderGroupBy.SIZE -> createSizeGroupSelector(items, labels)
            FolderGroupBy.TYPE -> { item -> typeGroup(item, labels) }
            FolderGroupBy.NONE -> { _ -> GroupInfo("none", "") }
        }
    }

    private fun createDateGroupSelector(labels: FolderDisplayLabels): (MediaItem) -> GroupInfo {
        val calendar = Calendar.getInstance()
        return { item ->
            if (item.dateModified <= 0L) {
                GroupInfo("date:unknown", labels.unknownDate)
            } else {
                calendar.timeInMillis = item.dateModified
                val year = calendar.get(Calendar.YEAR)
                val month = calendar.get(Calendar.MONTH) + 1
                GroupInfo(
                    key = "date:%04d%02d".format(Locale.ROOT, year, month),
                    title = "%04d/%02d".format(Locale.ROOT, year, month)
                )
            }
        }
    }

    private fun nameGroup(item: MediaItem): GroupInfo {
        val title = if (item.name.isNotEmpty()) {
            item.name.substring(0, item.name.offsetByCodePoints(0, 1))
                .uppercase(Locale.getDefault())
        } else {
            "#"
        }
        return GroupInfo("name:$title", title)
    }

    private fun createSizeGroupSelector(
        items: List<MediaItem>,
        labels: FolderDisplayLabels
    ): (MediaItem) -> GroupInfo {
        val positiveSizes = items.asSequence()
            .map { it.size }
            .filter { it > 0L }
            .toList()

        if (positiveSizes.isEmpty()) {
            return { GroupInfo("size:unknown", labels.unknownSize) }
        }

        val minSize = positiveSizes.minOrNull() ?: 0L
        val maxSize = positiveSizes.maxOrNull() ?: 0L
        if (minSize == maxSize) {
            val title = labels.formatSize(minSize)
            return { item ->
                if (item.size <= 0L) {
                    GroupInfo("size:unknown", labels.unknownSize)
                } else {
                    GroupInfo("size:$minSize-$maxSize", title)
                }
            }
        }

        val step = niceRoundUp(ceil((maxSize - minSize).toDouble() / TARGET_SIZE_BUCKETS).toLong())
            .coerceAtLeast(1L)
        val bucketCount = (((maxSize - minSize) / step) + 1).coerceAtMost(TARGET_SIZE_BUCKETS.toLong()).toInt()

        return { item ->
            if (item.size <= 0L) {
                GroupInfo("size:unknown", labels.unknownSize)
            } else {
                val rawIndex = ((item.size - minSize) / step).toInt()
                val index = rawIndex.coerceIn(0, bucketCount - 1)
                val start = minSize + (index * step)
                val end = if (index == bucketCount - 1) {
                    maxSize
                } else {
                    (start + step - 1).coerceAtMost(maxSize)
                }
                val title = if (start == end) {
                    labels.formatSize(start)
                } else {
                    "${labels.formatSize(start)} - ${labels.formatSize(end)}"
                }
                GroupInfo("size:$start-$end", title)
            }
        }
    }

    private fun niceRoundUp(value: Long): Long {
        if (value <= 1L) return 1L
        val exponent = floor(log10(value.toDouble())).toInt()
        val base = 10.0.pow(exponent).toLong().coerceAtLeast(1L)
        val scaled = value.toDouble() / base
        val multiplier = when {
            scaled <= 1.0 -> 1L
            scaled <= 2.0 -> 2L
            scaled <= 5.0 -> 5L
            else -> 10L
        }
        return multiplier * base
    }

    private fun typeGroup(item: MediaItem, labels: FolderDisplayLabels): GroupInfo {
        return when {
            item.mimeType.startsWith("image/") -> GroupInfo("type:image", labels.imageType)
            item.mimeType.startsWith("video/") -> GroupInfo("type:video", labels.videoType)
            else -> GroupInfo("type:other", labels.otherType)
        }
    }

    private data class GroupInfo(
        val key: String,
        val title: String
    )
}
