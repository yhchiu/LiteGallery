package org.iurl.litegallery

import org.junit.Assert.assertEquals
import org.junit.Test

class MediaIndexEntityTest {

    @Test
    fun mediaIndexEntityMapsToMediaItem() {
        val entity = MediaIndexEntity(
            mediaType = MEDIA_INDEX_TYPE_VIDEO,
            mediaStoreId = 7L,
            path = "/photos/movie.mp4",
            folderPath = "/photos",
            name = "movie.mp4",
            dateModifiedMs = 123_000L,
            sizeBytes = 456L,
            mimeType = "video/mp4",
            durationMs = 10_000L,
            width = 1920,
            height = 1080,
            generationAdded = 1L,
            generationModified = 2L,
            lastSeenScanId = 3L,
            updatedAtMs = 4L
        )

        val item = entity.toMediaItem()

        assertEquals(7L, item.id)
        assertEquals("movie.mp4", item.name)
        assertEquals("/photos/movie.mp4", item.path)
        assertEquals(123_000L, item.dateModified)
        assertEquals(456L, item.size)
        assertEquals("video/mp4", item.mimeType)
        assertEquals(10_000L, item.duration)
        assertEquals(1920, item.width)
        assertEquals(1080, item.height)
    }

    @Test
    fun mediaIndexEntityMapsToSkeleton() {
        val entity = MediaIndexEntity(
            mediaType = MEDIA_INDEX_TYPE_VIDEO,
            mediaStoreId = 7L,
            path = "/photos/movie.mp4",
            folderPath = "/photos",
            name = "movie.mp4",
            dateModifiedMs = 123_000L,
            sizeBytes = 456L,
            mimeType = "video/mp4",
            durationMs = 10_000L,
            width = 1920,
            height = 1080,
            generationAdded = 1L,
            generationModified = 2L,
            lastSeenScanId = 3L,
            updatedAtMs = 4L
        )

        val skeleton = entity.toMediaItemSkeleton()

        assertEquals(7L, skeleton.id)
        assertEquals("movie.mp4", skeleton.name)
        assertEquals("/photos/movie.mp4", skeleton.path)
        assertEquals(123_000L, skeleton.dateModified)
        assertEquals(456L, skeleton.size)
        assertEquals(true, skeleton.isVideo)
    }

    @Test
    fun folderAggregateMapsToFolderIndexEntity() {
        val aggregate = FolderAggregateRow(
            path = "/storage/emulated/0/DCIM/Camera",
            itemCount = 3L,
            imageCount = 2L,
            videoCount = 1L,
            totalSizeBytes = 1024L,
            latestDateModifiedMs = 999L,
            thumbnail = "/storage/emulated/0/DCIM/Camera/a.jpg"
        )

        val entity = aggregate.toFolderIndexEntity(updatedAtMs = 100L)

        assertEquals("Camera", entity.name)
        assertEquals(3, entity.itemCount)
        assertEquals(2, entity.imageCount)
        assertEquals(1, entity.videoCount)
        assertEquals(1024L, entity.totalSizeBytes)
        assertEquals(999L, entity.latestDateModifiedMs)
        assertEquals("/storage/emulated/0/DCIM/Camera/a.jpg", entity.thumbnail)
    }
}
