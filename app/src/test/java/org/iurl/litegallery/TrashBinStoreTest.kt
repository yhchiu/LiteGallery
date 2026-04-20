package org.iurl.litegallery

import android.content.Context
import android.net.Uri
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class TrashBinStoreTest {
    companion object {
        private const val TRASH_DB_NAME = "trash_bin.db"
    }

    private lateinit var context: Context
    private lateinit var sandboxDir: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        sandboxDir = File(context.cacheDir, "trash-bin-store-test")
        resetState()
    }

    @After
    fun tearDown() {
        resetState()
    }

    @Test
    fun cleanupExpiredTrash_doesNotDeleteRecentlyTrashedOldFile() {
        setRetentionDays(30)

        val nowMs = System.currentTimeMillis()
        val oldModifiedMs = nowMs - 180L * 24L * 60L * 60L * 1000L
        val trashedFile = createTrashedFile("${TrashBinStore.TRASH_FILE_PREFIX}image.jpg", oldModifiedMs)
        val trashedUri = Uri.fromFile(trashedFile)

        TrashBinStore.rememberTrashedFile(
            context = context,
            trashedUri = trashedUri,
            originalUri = null,
            originalName = "image.jpg",
            trashedAtMs = nowMs
        )

        val cleanupResult = TrashBinStore.cleanupExpiredTrash(
            context = context,
            nowMs = nowMs + 24L * 60L * 60L * 1000L
        )

        assertTrue(cleanupResult.removedUris.isEmpty())
        assertTrue(cleanupResult.removedScannerPaths.isEmpty())
        assertTrue(trashedFile.exists())
        assertEquals(trashedUri.toString(), TrashBinStore.getTrashedRecords(context).single().trashedUri)
        assertEquals(trashedUri.toString(), TrashBinStore.getTrashedRecord(context, trashedUri.toString())?.trashedUri)
    }

    @Test
    fun cleanupExpiredTrash_deletesEntriesOlderThanRetentionByTrashedAt() {
        setRetentionDays(30)

        val nowMs = System.currentTimeMillis()
        val expiredTrashedAtMs = nowMs - 45L * 24L * 60L * 60L * 1000L
        val trashedFile = createTrashedFile("${TrashBinStore.TRASH_FILE_PREFIX}video.mp4", nowMs)
        val trashedUri = Uri.fromFile(trashedFile)

        TrashBinStore.rememberTrashedFile(
            context = context,
            trashedUri = trashedUri,
            originalUri = null,
            originalName = "video.mp4",
            trashedAtMs = expiredTrashedAtMs
        )

        val cleanupResult = TrashBinStore.cleanupExpiredTrash(context, nowMs)

        assertTrue(cleanupResult.removedUris.contains(trashedUri.toString()))
        assertTrue(cleanupResult.removedScannerPaths.contains(trashedFile.absolutePath))
        assertFalse(trashedFile.exists())
        assertEquals(emptyList<TrashBinDatabase.TrashRecord>(), TrashBinStore.getTrashedRecords(context))
        assertEquals(null, TrashBinStore.getTrashedRecord(context, trashedUri.toString()))
    }

    @Test
    fun rememberTrashedFile_storesFallbackOriginalNameWhenOriginalNameIsBlank() {
        val nowMs = System.currentTimeMillis()
        val trashedUri = Uri.parse("file:///storage/emulated/0/DCIM/${TrashBinStore.TRASH_FILE_PREFIX}12-legacy.jpg")

        TrashBinStore.rememberTrashedFile(
            context = context,
            trashedUri = trashedUri,
            originalUri = null,
            originalName = "",
            originalPathHint = "C:\\original\\legacy.jpg",
            trashedAtMs = nowMs
        )

        val storedRecord = TrashBinStore.getTrashedRecord(context, trashedUri.toString())

        assertEquals("legacy.jpg", storedRecord?.originalName)
        assertEquals("C:\\original\\legacy.jpg", storedRecord?.originalPathHint)
        assertEquals(trashedUri.toString(), storedRecord?.trashedUri)
    }

    @Test
    fun fallbackOriginalNameFromTrashedName_parsesKnownFormats() {
        assertEquals("photo.jpg", TrashBinStore.fallbackOriginalNameFromTrashedName(".trashed-photo.jpg"))
        assertEquals("photo.jpg", TrashBinStore.fallbackOriginalNameFromTrashedName(".trashed-12-photo.jpg"))
        assertEquals("plain.jpg", TrashBinStore.fallbackOriginalNameFromTrashedName("plain.jpg"))
    }

    private fun setRetentionDays(days: Int) {
        PreferenceManager.getDefaultSharedPreferences(context)
            .edit()
            .putString(TrashBinStore.TRASH_RETENTION_DAYS_KEY, days.toString())
            .commit()
    }

    private fun createTrashedFile(fileName: String, lastModifiedMs: Long): File {
        if (!sandboxDir.exists()) {
            sandboxDir.mkdirs()
        }
        val file = File(sandboxDir, fileName)
        file.writeText("test")
        file.setLastModified(lastModifiedMs)
        return file
    }

    private fun resetState() {
        resetDatabase()

        PreferenceManager.getDefaultSharedPreferences(context)
            .edit()
            .clear()
            .commit()

        if (sandboxDir.exists()) {
            sandboxDir.deleteRecursively()
        }
        sandboxDir.mkdirs()
    }

    private fun resetDatabase() {
        runCatching {
            val instanceField = TrashBinDatabase::class.java.getDeclaredField("INSTANCE")
            instanceField.isAccessible = true
            (instanceField.get(null) as? TrashBinDatabase)?.close()
            instanceField.set(null, null)
        }
        context.deleteDatabase(TRASH_DB_NAME)
    }
}
