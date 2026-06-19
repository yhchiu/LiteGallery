package org.iurl.litegallery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SmbPathTest {

    @Test
    fun parse_validFilePathExposesDerivedProperties() {
        val path = SmbPath.parse("smb://server.local/Photos/Trips/2026/file.name.jpg")

        requireNotNull(path)
        assertEquals("server.local", path.host)
        assertEquals("Photos", path.share)
        assertEquals("Trips/2026/file.name.jpg", path.path)
        assertEquals("smb://server.local/Photos/Trips/2026/file.name.jpg", path.fullPath)
        assertEquals("file.name.jpg", path.fileName)
        assertEquals("file.name", path.fileNameWithoutExtension)
        assertEquals("jpg", path.fileExtension)
        assertEquals("Trips/2026", path.parentPath)
        assertEquals("smb://server.local/Photos/Trips/2026", path.parentFolderPath)
        assertEquals("Trips\\2026\\file.name.jpg", path.smbjPath)
    }

    @Test
    fun parse_shareRootTrimsTrailingSlash() {
        val path = SmbPath.parse("smb://nas/media/")

        requireNotNull(path)
        assertEquals("nas", path.host)
        assertEquals("media", path.share)
        assertEquals("", path.path)
        assertEquals("smb://nas/media", path.fullPath)
        assertEquals("smb://nas/media", path.parentFolderPath)
    }

    @Test
    fun parse_rejectsUnsupportedOrIncompleteUrls() {
        assertNull(SmbPath.parse("https://server/share/file.jpg"))
        assertNull(SmbPath.parse("smb://server"))
        assertNull(SmbPath.parse("smb:///share/file.jpg"))
        assertNull(SmbPath.parse("smb://server//file.jpg"))
    }

    @Test
    fun isSmb_matchesOnlyLowercaseSmbScheme() {
        assertTrue(SmbPath.isSmb("smb://server/share"))
        assertFalse(SmbPath.isSmb("SMB://server/share"))
        assertFalse(SmbPath.isSmb("/storage/emulated/0/DCIM/file.jpg"))
    }
}
