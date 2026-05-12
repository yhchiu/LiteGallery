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
    val sortedMediaItems: List<MediaItemSkeleton>,
    val displayItems: List<FolderDisplayItem>,
    val fastScrollSections: List<FastScrollSection>,
    val isGrouped: Boolean
)

object FolderDisplayBuilder {
    private const val TARGET_SIZE_BUCKETS = 5

    fun build(
        items: List<MediaItemSkeleton>,
        sortOrder: String,
        groupBy: FolderGroupBy,
        labels: FolderDisplayLabels
    ): FolderDisplayResult {
        val sortedItems = sortMediaItems(items, sortOrder)
        if (groupBy == FolderGroupBy.NONE || sortedItems.isEmpty()) {
            val flatSections = if (sortedItems.isNotEmpty()) buildIndexForFlatList(sortedItems, sortOrder, labels) else emptyList()
            return FolderDisplayResult(
                sortedMediaItems = sortedItems,
                displayItems = emptyList(),
                fastScrollSections = flatSections,
                isGrouped = false
            )
        }

        val groupedDisplay = buildDisplayItems(sortedItems, groupBy, labels)
        return FolderDisplayResult(
            sortedMediaItems = sortedItems,
            displayItems = groupedDisplay.displayItems,
            fastScrollSections = groupedDisplay.fastScrollSections,
            isGrouped = true
        )
    }

    private fun buildIndexForFlatList(
        sortedItems: List<MediaItemSkeleton>,
        sortOrder: String,
        labels: FolderDisplayLabels
    ): List<FastScrollSection> {
        val sections = mutableListOf<FastScrollSection>()
        var currentLabel = ""
        
        // Use a dummy group selector that matches the sort order
        val selector = when (sortOrder) {
            "name_asc", "name_desc" -> createGroupSelector(sortedItems, FolderGroupBy.NAME, labels)
            "size_asc", "size_desc" -> createGroupSelector(sortedItems, FolderGroupBy.SIZE, labels)
            else -> createGroupSelector(sortedItems, FolderGroupBy.DATE, labels) // Default to DATE
        }
        
        sortedItems.forEachIndexed { i, item ->
            val groupInfo = selector(item)
            val label = groupInfo.title
            if (label != currentLabel) {
                sections.add(FastScrollSection(adapterPosition = i, title = label))
                currentLabel = label
            }
        }
        return sections
    }

    fun sortMediaItems(items: List<MediaItemSkeleton>, sortOrder: String): List<MediaItemSkeleton> {
        return when (sortOrder) {
            "date_desc" -> items.sortedByDescending { it.dateModified }
            "date_asc" -> items.sortedBy { it.dateModified }
            "name_asc" -> items.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
            "name_desc" -> items.sortedWith(compareByDescending(String.CASE_INSENSITIVE_ORDER) { it.name })
            "size_desc" -> items.sortedWith(sizeDescendingComparator)
            "size_asc" -> items.sortedWith(sizeAscendingComparator)
            else -> items.sortedByDescending { it.dateModified }
        }
    }

    private val sizeDescendingComparator = compareBy<MediaItemSkeleton> { it.size <= 0L }
        .thenByDescending { it.size }

    private val sizeAscendingComparator = compareBy<MediaItemSkeleton> { it.size <= 0L }
        .thenBy { it.size }

    private fun buildDisplayItems(
        sortedItems: List<MediaItemSkeleton>,
        groupBy: FolderGroupBy,
        labels: FolderDisplayLabels
    ): GroupedDisplayResult {
        val groupSelector = createGroupSelector(sortedItems, groupBy, labels)
        val groups = linkedMapOf<GroupInfo, MutableList<FolderDisplayItem.Media>>()
        sortedItems.forEachIndexed { index, item ->
            val group = groupSelector(item)
            groups.getOrPut(group) { mutableListOf() }
                .add(FolderDisplayItem.Media(item, mediaIndex = index))
        }

        val displayItems = ArrayList<FolderDisplayItem>(sortedItems.size + groups.size)
        val fastScrollSections = ArrayList<FastScrollSection>(groups.size)
        groups.forEach { (group, media) ->
            fastScrollSections.add(
                FastScrollSection(
                    adapterPosition = displayItems.size,
                    title = group.title
                )
            )
            displayItems.add(FolderDisplayItem.Header(group.key, group.title, media.size))
            displayItems.addAll(media)
        }
        return GroupedDisplayResult(displayItems, fastScrollSections)
    }

    private fun createGroupSelector(
        items: List<MediaItemSkeleton>,
        groupBy: FolderGroupBy,
        labels: FolderDisplayLabels
    ): (MediaItemSkeleton) -> GroupInfo {
        return when (groupBy) {
            FolderGroupBy.DATE -> createDateGroupSelector(labels)
            FolderGroupBy.NAME -> createNameGroupSelector()
            FolderGroupBy.SIZE -> createSizeGroupSelector(items, labels)
            FolderGroupBy.TYPE -> createTypeGroupSelector(labels)
            FolderGroupBy.NONE -> createNoneGroupSelector()
        }
    }

    private fun createNoneGroupSelector(): (MediaItemSkeleton) -> GroupInfo {
        val noneGroup = GroupInfo("none", "")
        return { noneGroup }
    }

    private fun createDateGroupSelector(labels: FolderDisplayLabels): (MediaItemSkeleton) -> GroupInfo {
        val calendar = Calendar.getInstance()
        val groupCache = HashMap<Int, GroupInfo>()
        val unknownGroup = GroupInfo("date:unknown", labels.unknownDate)
        return { item ->
            if (item.dateModified <= 0L) {
                unknownGroup
            } else {
                calendar.timeInMillis = item.dateModified
                val year = calendar.get(Calendar.YEAR)
                val month = calendar.get(Calendar.MONTH) + 1
                val cacheKey = (year * 100) + month
                groupCache.getOrPut(cacheKey) {
                    val monthText = twoDigitMonth(month)
                    GroupInfo(
                        key = "date:$year$monthText",
                        title = "$year/$monthText"
                    )
                }
            }
        }
    }

    private fun createNameGroupSelector(): (MediaItemSkeleton) -> GroupInfo {
        val groupCache = HashMap<String, GroupInfo>()
        val fallbackGroup = GroupInfo("name:#", "#")
        return { item ->
            if (item.name.isEmpty()) {
                fallbackGroup
            } else {
                val title = item.name.substring(0, item.name.offsetByCodePoints(0, 1))
                    .uppercase(Locale.getDefault())
                groupCache.getOrPut(title) {
                    GroupInfo("name:$title", title)
                }
            }
        }
    }

    private fun createSizeGroupSelector(
        items: List<MediaItemSkeleton>,
        labels: FolderDisplayLabels
    ): (MediaItemSkeleton) -> GroupInfo {
        var minSize = Long.MAX_VALUE
        var maxSize = Long.MIN_VALUE
        var hasPositiveSize = false
        items.forEach { item ->
            val size = item.size
            if (size > 0L) {
                hasPositiveSize = true
                if (size < minSize) minSize = size
                if (size > maxSize) maxSize = size
            }
        }

        val unknownGroup = GroupInfo("size:unknown", labels.unknownSize)
        if (!hasPositiveSize) {
            return { unknownGroup }
        }

        if (minSize == maxSize) {
            val title = labels.formatSize(minSize)
            val singleSizeGroup = GroupInfo("size:$minSize-$maxSize", title)
            return { item ->
                if (item.size <= 0L) {
                    unknownGroup
                } else {
                    singleSizeGroup
                }
            }
        }

        val step = niceRoundUp(ceil((maxSize - minSize).toDouble() / TARGET_SIZE_BUCKETS).toLong())
            .coerceAtLeast(1L)
        val bucketCount = (((maxSize - minSize) / step) + 1).coerceAtMost(TARGET_SIZE_BUCKETS.toLong()).toInt()
        val bucketGroups = Array<GroupInfo?>(bucketCount) { null }

        return { item ->
            if (item.size <= 0L) {
                unknownGroup
            } else {
                val rawIndex = ((item.size - minSize) / step).toInt()
                val index = rawIndex.coerceIn(0, bucketCount - 1)
                bucketGroups[index] ?: run {
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
                    GroupInfo("size:$start-$end", title).also { bucketGroups[index] = it }
                }
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

    private fun createTypeGroupSelector(labels: FolderDisplayLabels): (MediaItemSkeleton) -> GroupInfo {
        val imageGroup = GroupInfo("type:image", labels.imageType)
        val videoGroup = GroupInfo("type:video", labels.videoType)
        return { item ->
            if (item.isVideo) videoGroup else imageGroup
        }
    }

    private fun twoDigitMonth(month: Int): String {
        return if (month < 10) "0$month" else month.toString()
    }

    private data class GroupInfo(
        val key: String,
        val title: String
    )

    private data class GroupedDisplayResult(
        val displayItems: List<FolderDisplayItem>,
        val fastScrollSections: List<FastScrollSection>
    )
}
