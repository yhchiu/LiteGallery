package org.iurl.litegallery

import android.database.MatrixCursor
import android.net.Uri
import android.provider.MediaStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MediaUriPathResolverTest {

    @Test
    fun resolveRealPath_returnsFilePathForFileScheme() {
        val uri = Uri.parse("file:///storage/emulated/0/DCIM/Camera/photo.jpg")

        val resolved = MediaUriPathResolver.resolveRealPath(uri) { null }

        assertEquals("/storage/emulated/0/DCIM/Camera/photo.jpg", resolved)
    }

    @Test
    fun resolveRealPath_readsDataColumnWhenAvailable() {
        val uri = Uri.parse("content://media/external/images/media/123")
        val expectedPath = "/storage/emulated/0/Pictures/sample.jpg"

        val resolved = MediaUriPathResolver.resolveRealPath(uri) {
            MatrixCursor(arrayOf(MediaStore.MediaColumns.DATA)).apply {
                addRow(arrayOf(expectedPath))
            }
        }

        assertEquals(expectedPath, resolved)
    }

    @Test
    fun resolveRealPath_returnsNullWhenDataColumnMissing() {
        val uri = Uri.parse("content://com.example.provider/document/42")

        val resolved = MediaUriPathResolver.resolveRealPath(uri) {
            MatrixCursor(arrayOf("_id")).apply {
                addRow(arrayOf(42L))
            }
        }

        assertNull(resolved)
    }

    @Test
    fun resolveRealPath_returnsNullForUnsupportedScheme() {
        val uri = Uri.parse("https://example.com/file.jpg")

        val resolved = MediaUriPathResolver.resolveRealPath(uri) { null }

        assertNull(resolved)
    }
}
