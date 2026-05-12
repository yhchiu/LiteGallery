package org.iurl.litegallery

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaMetadataPolicyTest {

    @Test
    fun completeImageDoesNotNeedDetailedMetadata() {
        val item = mediaItem(
            mimeType = "image/jpeg",
            size = 1024L,
            width = 100,
            height = 80
        )

        assertFalse(MediaMetadataPolicy.needsDetailedMetadata(item))
    }

    @Test
    fun imageWithoutDimensionsNeedsDetailedMetadata() {
        val item = mediaItem(
            mimeType = "image/jpeg",
            size = 1024L,
            width = 0,
            height = 80
        )

        assertTrue(MediaMetadataPolicy.needsDetailedMetadata(item))
    }

    @Test
    fun videoWithoutDurationNeedsDetailedMetadata() {
        val item = mediaItem(
            mimeType = "video/mp4",
            size = 2048L,
            width = 1920,
            height = 1080,
            duration = 0L
        )

        assertTrue(MediaMetadataPolicy.needsDetailedMetadata(item))
    }

    private fun mediaItem(
        mimeType: String,
        size: Long,
        width: Int,
        height: Int,
        duration: Long = 1L
    ): MediaItem {
        return MediaItem(
            name = "item",
            path = "/photos/item",
            dateModified = 1L,
            size = size,
            mimeType = mimeType,
            duration = duration,
            width = width,
            height = height
        )
    }
}
