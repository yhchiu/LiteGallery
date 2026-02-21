package org.iurl.litegallery

import android.provider.MediaStore
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaScannerProjectionTest {

    @Test
    fun imageProjection_lightweightExcludesDeferredMetadataColumns() {
        val projection = MediaScanner.buildImageFolderProjection(includeDeferredMetadata = false).toSet()

        assertTrue(projection.contains(MediaStore.Images.Media.DATA))
        assertFalse(projection.contains(MediaStore.Images.Media.SIZE))
        assertFalse(projection.contains(MediaStore.Images.Media.WIDTH))
        assertFalse(projection.contains(MediaStore.Images.Media.HEIGHT))
    }

    @Test
    fun imageProjection_deferredIncludesMetadataColumns() {
        val projection = MediaScanner.buildImageFolderProjection(includeDeferredMetadata = true).toSet()

        assertTrue(projection.contains(MediaStore.Images.Media.SIZE))
        assertTrue(projection.contains(MediaStore.Images.Media.WIDTH))
        assertTrue(projection.contains(MediaStore.Images.Media.HEIGHT))
    }

    @Test
    fun videoProjection_lightweightExcludesDurationAndDeferredMetadataColumns() {
        val projection = MediaScanner.buildVideoFolderProjection(
            includeDeferredMetadata = false,
            includeVideoDuration = false
        ).toSet()

        assertFalse(projection.contains(MediaStore.Video.Media.DURATION))
        assertFalse(projection.contains(MediaStore.Video.Media.SIZE))
        assertFalse(projection.contains(MediaStore.Video.Media.WIDTH))
        assertFalse(projection.contains(MediaStore.Video.Media.HEIGHT))
    }

    @Test
    fun videoProjection_includesRequestedColumns() {
        val projection = MediaScanner.buildVideoFolderProjection(
            includeDeferredMetadata = true,
            includeVideoDuration = true
        ).toSet()

        assertTrue(projection.contains(MediaStore.Video.Media.DURATION))
        assertTrue(projection.contains(MediaStore.Video.Media.SIZE))
        assertTrue(projection.contains(MediaStore.Video.Media.WIDTH))
        assertTrue(projection.contains(MediaStore.Video.Media.HEIGHT))
    }
}
