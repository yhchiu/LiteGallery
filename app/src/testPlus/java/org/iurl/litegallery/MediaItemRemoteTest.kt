package org.iurl.litegallery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Remote-source behaviour of [MediaItem], exercised only in the `plus` flavor where
 * an SMB [MediaSource] is registered.
 */
class MediaItemRemoteTest {

    @Before
    fun registerSmbSource() {
        MediaSourceRegistry.register(SmbMediaSource)
    }

    @Test
    fun smbPathIsRemoteAndRejectsGetFile() {
        val smb = item(path = "smb://server/share/photo.jpg")

        assertTrue(smb.isRemote)
        assertThrows(UnsupportedOperationException::class.java) { smb.getFile() }
    }

    @Test
    fun localPathRemainsLocal() {
        val local = item(path = "/storage/emulated/0/DCIM/photo.jpg")

        assertEquals(
            "/storage/emulated/0/DCIM/photo.jpg",
            local.getFile().path.replace('\\', '/')
        )
    }

    private fun item(path: String): MediaItem = MediaItem(
        id = 1L,
        name = "photo.jpg",
        path = path,
        dateModified = 1L,
        size = 2L,
        mimeType = "image/jpeg"
    )
}
