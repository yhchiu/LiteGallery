package org.iurl.litegallery

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class FileSystemScannerTest {

    private lateinit var context: Context
    private lateinit var sandboxDir: File
    private lateinit var scanner: FileSystemScanner

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        sandboxDir = File(context.cacheDir, "file-system-scanner-test")
        sandboxDir.deleteRecursively()
        sandboxDir.mkdirs()
        scanner = FileSystemScanner(context)
    }

    @After
    fun tearDown() {
        sandboxDir.deleteRecursively()
    }

    @Test
    fun scanFolderForMedia_returnsSupportedMediaSortedByModifiedDate() = runBlocking {
        writeFile("photo.JPG", modifiedMs = 1_000L)
        writeFile("clip.mp4", modifiedMs = 2_000L)
        writeFile("notes.txt", modifiedMs = 3_000L)
        writeFile("${TrashBinStore.TRASH_FILE_PREFIX}old.jpg", modifiedMs = 4_000L)

        val items = scanner.scanFolderForMedia(sandboxDir.absolutePath)

        assertEquals(listOf("clip.mp4", "photo.JPG"), items.map { it.name })
        assertTrue(items[0].isVideo)
        assertEquals("video/mp4", items[0].mimeType)
        assertEquals("image/jpeg", items[1].mimeType)
        assertEquals(0L, items[0].size)
        assertEquals(0, items[0].width)
        assertEquals(0, items[0].height)
    }

    @Test
    fun scanFolderForMedia_honorsNomediaUnlessIgnored() = runBlocking {
        writeFile(".nomedia", modifiedMs = 1_000L)
        writeFile("photo.png", modifiedMs = 2_000L)

        assertTrue(scanner.scanFolderForMedia(sandboxDir.absolutePath).isEmpty())
        assertEquals(
            listOf("photo.png"),
            scanner.scanFolderForMedia(sandboxDir.absolutePath, ignoreNomedia = true).map { it.name }
        )
    }

    @Test
    fun scanFolderForMedia_returnsEmptyListForMissingOrNonDirectoryPath() = runBlocking {
        val regularFile = writeFile("photo.jpg", modifiedMs = 1_000L)

        assertTrue(scanner.scanFolderForMedia(File(sandboxDir, "missing").absolutePath).isEmpty())
        assertTrue(scanner.scanFolderForMedia(regularFile.absolutePath).isEmpty())
    }

    private fun writeFile(name: String, modifiedMs: Long): File {
        val file = File(sandboxDir, name)
        file.parentFile?.mkdirs()
        file.writeText("x")
        file.setLastModified(modifiedMs)
        return file
    }
}
