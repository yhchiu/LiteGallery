package org.iurl.litegallery

import org.junit.Assert.assertEquals
import org.junit.Test

class MediaMimeTypesTest {

    @Test
    fun jpgAndJpegMapToStandardJpeg() {
        assertEquals("image/jpeg", MediaMimeTypes.fromExtension("jpg"))
        assertEquals("image/jpeg", MediaMimeTypes.fromExtension("JPG"))
        assertEquals("image/jpeg", MediaMimeTypes.fromExtension("jpeg"))
        assertEquals("image/jpeg", MediaMimeTypes.fromPath("/sdcard/DCIM/IMG_0001.JPG"))
    }

    @Test
    fun commonImageAndVideoTypesResolve() {
        assertEquals("image/png", MediaMimeTypes.fromExtension("png"))
        assertEquals("image/webp", MediaMimeTypes.fromExtension("webp"))
        assertEquals("video/mp4", MediaMimeTypes.fromExtension("mp4"))
        assertEquals("video/x-matroska", MediaMimeTypes.fromPath("movie.mkv"))
        assertEquals("video/quicktime", MediaMimeTypes.fromPath("clip.MOV"))
    }

    @Test
    fun unknownExtensionsFallBackByMediaFamily() {
        assertEquals("video/*", MediaMimeTypes.fromExtension("m4v"))
        assertEquals("video/*", MediaMimeTypes.fromExtension("flv"))
        assertEquals("image/*", MediaMimeTypes.fromExtension("txt"))
        assertEquals("image/*", MediaMimeTypes.fromPath("no-extension"))
    }

    @Test
    fun leadingDotIsIgnored() {
        assertEquals("image/jpeg", MediaMimeTypes.fromExtension(".jpeg"))
    }
}
