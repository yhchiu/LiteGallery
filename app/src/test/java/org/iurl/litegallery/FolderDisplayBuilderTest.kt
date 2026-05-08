package org.iurl.litegallery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FolderDisplayBuilderTest {

    @Test
    fun noneReturnsSortedMediaWithoutDisplayItems() {
        val result = FolderDisplayBuilder.build(
            items = listOf(item("old.jpg", dateModified = 1L), item("new.jpg", dateModified = 3L)),
            sortOrder = "date_desc",
            groupBy = FolderGroupBy.NONE,
            labels = labels
        )

        assertFalse(result.isGrouped)
        assertEquals(listOf("new.jpg", "old.jpg"), result.sortedMediaItems.map { it.name })
        assertTrue(result.displayItems.isEmpty())
        assertTrue(result.fastScrollSections.isEmpty())
    }

    @Test
    fun dateGroupingHandlesUnknownAndFollowsDateSortDirection() {
        val items = listOf(
            item("unknown.jpg", dateModified = 0L),
            item("feb.jpg", dateModified = millis(2024, 2, 10)),
            item("jan.jpg", dateModified = millis(2024, 1, 10))
        )

        val desc = FolderDisplayBuilder.build(items, "date_desc", FolderGroupBy.DATE, labels)
        assertEquals(
            listOf("2024/02", "2024/01", "Unknown Date"),
            desc.headers().map { it.title }
        )

        val asc = FolderDisplayBuilder.build(items, "date_asc", FolderGroupBy.DATE, labels)
        assertEquals(
            listOf("Unknown Date", "2024/01", "2024/02"),
            asc.headers().map { it.title }
        )
    }

    @Test
    fun nameGroupingUsesFirstCharacterOnly() {
        val result = FolderDisplayBuilder.build(
            items = listOf(
                item("alpha.jpg"),
                item("中文.jpg"),
                item("")
            ),
            sortOrder = "name_asc",
            groupBy = FolderGroupBy.NAME,
            labels = labels
        )

        assertEquals(listOf("#", "A", "中"), result.headers().map { it.title })
    }

    @Test
    fun sizeGroupingHandlesUnknownSingleSizeAndBoundedBuckets() {
        val single = FolderDisplayBuilder.build(
            items = listOf(
                item("unknown.jpg", size = 0L),
                item("a.jpg", size = 100L),
                item("b.jpg", size = 100L)
            ),
            sortOrder = "size_asc",
            groupBy = FolderGroupBy.SIZE,
            labels = labels
        )

        assertEquals(listOf("100 B", "Unknown Size"), single.headers().map { it.title })

        val spread = FolderDisplayBuilder.build(
            items = listOf(
                item("a.jpg", size = 1L),
                item("b.jpg", size = 2L),
                item("c.jpg", size = 10L),
                item("d.jpg", size = 200L),
                item("e.jpg", size = 10_000L),
                item("f.jpg", size = 1_000_000L),
                item("unknown.jpg", size = 0L)
            ),
            sortOrder = "size_asc",
            groupBy = FolderGroupBy.SIZE,
            labels = labels
        )

        val positiveHeaders = spread.headers().filterNot { it.title == "Unknown Size" }
        assertTrue(positiveHeaders.size <= 5)
        assertTrue(spread.headers().any { it.title == "Unknown Size" })
    }

    @Test
    fun typeGroupingUsesMajorMimeTypeOnly() {
        val result = FolderDisplayBuilder.build(
            items = listOf(
                item("photo.jpg", mimeType = "image/jpeg"),
                item("clip.mp4", mimeType = "video/mp4"),
                item("doc.bin", mimeType = "application/octet-stream")
            ),
            sortOrder = "name_asc",
            groupBy = FolderGroupBy.TYPE,
            labels = labels
        )

        assertEquals(listOf("Video", "Other", "Image"), result.headers().map { it.title })
    }

    @Test
    fun groupedMediaItemsKeepIndexInSortedMediaList() {
        val result = FolderDisplayBuilder.build(
            items = listOf(
                item("b.jpg", dateModified = 2L),
                item("a.jpg", dateModified = 1L),
                item("c.jpg", dateModified = 3L)
            ),
            sortOrder = "name_asc",
            groupBy = FolderGroupBy.NAME,
            labels = labels
        )

        val mediaItems = result.displayItems.filterIsInstance<FolderDisplayItem.Media>()
        assertEquals(listOf("a.jpg", "b.jpg", "c.jpg"), result.sortedMediaItems.map { it.name })
        assertEquals(listOf(0, 1, 2), mediaItems.map { it.mediaIndex })
    }

    @Test
    fun groupedResultCreatesFastScrollSectionsFromHeaders() {
        val result = FolderDisplayBuilder.build(
            items = listOf(
                item("b.jpg"),
                item("a.jpg"),
                item("c.jpg")
            ),
            sortOrder = "name_asc",
            groupBy = FolderGroupBy.NAME,
            labels = labels
        )

        assertEquals(
            listOf(
                FastScrollSection(adapterPosition = 0, title = "A"),
                FastScrollSection(adapterPosition = 2, title = "B"),
                FastScrollSection(adapterPosition = 4, title = "C")
            ),
            result.fastScrollSections
        )
        assertEquals(result.headers().map { it.title }, result.fastScrollSections.map { it.title })
    }

    @Test
    fun invalidGroupPreferenceFallsBackToNone() {
        assertEquals(FolderGroupBy.NONE, FolderGroupBy.fromPreference("bad"))
        assertEquals(FolderGroupBy.NONE, FolderGroupBy.fromPreference(null))
        assertEquals(FolderGroupBy.TYPE, FolderGroupBy.fromPreference("type"))
    }

    private fun FolderDisplayResult.headers(): List<FolderDisplayItem.Header> =
        displayItems.filterIsInstance<FolderDisplayItem.Header>()

    private fun millis(year: Int, month: Int, day: Int): Long {
        val calendar = java.util.Calendar.getInstance().apply {
            clear()
            set(year, month - 1, day)
        }
        return calendar.timeInMillis
    }

    private fun item(
        name: String,
        dateModified: Long = 1L,
        size: Long = 1L,
        mimeType: String = "image/jpeg"
    ): MediaItem = MediaItem(
        name = name,
        path = "/media/$name",
        dateModified = dateModified,
        size = size,
        mimeType = mimeType
    )

    private val labels = FolderDisplayLabels(
        unknownDate = "Unknown Date",
        unknownSize = "Unknown Size",
        imageType = "Image",
        videoType = "Video",
        otherType = "Other",
        formatSize = { "$it B" }
    )
}
