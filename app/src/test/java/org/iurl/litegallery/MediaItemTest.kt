package org.iurl.litegallery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaItemTest {

    @Test
    fun derivedFileNamePropertiesUseLastDot() {
        val item = imageItem(name = "holiday.final.jpg")

        assertEquals("jpg", item.extension)
        assertEquals("holiday.final", item.nameWithoutExtension)
    }

    @Test
    fun getFormattedDuration_returnsBlankForImagesAndZeroLengthVideos() {
        assertEquals("", imageItem().getFormattedDuration())
        assertEquals("", videoItem(duration = 0L).getFormattedDuration())
    }

    @Test
    fun getFormattedDuration_formatsMinutesAndHours() {
        assertEquals("1:05", videoItem(duration = 65_000L).getFormattedDuration())
        assertEquals("1:02:03", videoItem(duration = 3_723_000L).getFormattedDuration())
    }

    @Test
    fun getFile_returnsLocalFileButRejectsSmbPaths() {
        val local = imageItem(path = "C:/media/photo.jpg")
        val smb = imageItem(path = "smb://server/share/photo.jpg")

        assertEquals("C:/media/photo.jpg", local.getFile().path.replace('\\', '/'))
        assertTrue(smb.isSmb)
        assertThrows(UnsupportedOperationException::class.java) {
            smb.getFile()
        }
    }

    @Test
    fun isVideoDefaultsFromMimeTypeButCanBeOverridden() {
        assertTrue(videoItem().isVideo)
        assertFalse(imageItem().isVideo)
        assertFalse(videoItem(isVideo = false).isVideo)
    }

    private fun imageItem(
        name: String = "photo.jpg",
        path: String = "/storage/emulated/0/DCIM/photo.jpg",
        isVideo: Boolean = false
    ): MediaItem = MediaItem(
        id = 1L,
        name = name,
        path = path,
        dateModified = 1L,
        size = 2L,
        mimeType = "image/jpeg",
        isVideo = isVideo
    )

    private fun videoItem(
        duration: Long = 1_000L,
        isVideo: Boolean = true
    ): MediaItem = MediaItem(
        id = 2L,
        name = "clip.mp4",
        path = "/storage/emulated/0/DCIM/clip.mp4",
        dateModified = 1L,
        size = 2L,
        mimeType = "video/mp4",
        duration = duration,
        isVideo = isVideo
    )
}
