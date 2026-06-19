package org.iurl.litegallery

import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Remote-folder ordering, exercised only in the `plus` flavor where an SMB
 * [MediaSource] is registered (so `smb://` folders are recognised as remote).
 */
class HomeFolderSorterRemoteTest {

    @Before
    fun registerSmbSource() {
        MediaSourceRegistry.register(SmbMediaSource)
    }

    @Test
    fun sortByRecent_keepsRemoteFolderAtEnd() {
        val folders = listOf(
            folder(name = "SMB", path = "smb://", latestDateModifiedMs = Long.MAX_VALUE),
            folder(name = "Camera", path = "/storage/DCIM/Camera", latestDateModifiedMs = 1_000L)
        )

        val sorted = HomeFolderSorter.sortByRecent(folders)

        assertEquals(listOf("Camera", "SMB"), sorted.map { it.name })
    }

    private fun folder(name: String, path: String, latestDateModifiedMs: Long): MediaFolder =
        MediaFolder(
            name = name,
            path = path,
            itemCount = 1,
            latestDateModifiedMs = latestDateModifiedMs
        )
}
