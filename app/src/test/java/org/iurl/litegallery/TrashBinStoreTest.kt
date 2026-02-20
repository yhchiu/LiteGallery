package org.iurl.litegallery

import android.content.Context
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
import java.io.File

@RunWith(RobolectricTestRunner::class)
class TrashBinStoreTest {

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

        TrashBinStore.rememberTrashedFile(
            context = context,
            trashedPath = trashedFile.absolutePath,
            originalName = "image.jpg",
            trashedAtMs = nowMs
        )

        val cleanupResult = TrashBinStore.cleanupExpiredTrash(
            context = context,
            nowMs = nowMs + 24L * 60L * 60L * 1000L
        )

        assertTrue(cleanupResult.removedPaths.isEmpty())
        assertTrue(trashedFile.exists())
        assertTrue(TrashBinStore.getTrashedPaths(context).contains(trashedFile.absolutePath))
    }

    @Test
    fun cleanupExpiredTrash_deletesEntriesOlderThanRetentionByTrashedAt() {
        setRetentionDays(30)

        val nowMs = System.currentTimeMillis()
        val expiredTrashedAtMs = nowMs - 45L * 24L * 60L * 60L * 1000L
        val trashedFile = createTrashedFile("${TrashBinStore.TRASH_FILE_PREFIX}video.mp4", nowMs)

        TrashBinStore.rememberTrashedFile(
            context = context,
            trashedPath = trashedFile.absolutePath,
            originalName = "video.mp4",
            trashedAtMs = expiredTrashedAtMs
        )

        val cleanupResult = TrashBinStore.cleanupExpiredTrash(context, nowMs)

        assertTrue(cleanupResult.removedPaths.contains(trashedFile.absolutePath))
        assertFalse(trashedFile.exists())
        assertFalse(TrashBinStore.getTrashedPaths(context).contains(trashedFile.absolutePath))
    }

    @Test
    fun getTrashedPaths_migratesLegacyPrefsIntoDatabase() {
        val nowMs = System.currentTimeMillis()
        val trashedFile = createTrashedFile("${TrashBinStore.TRASH_FILE_PREFIX}legacy.jpg", nowMs)
        val legacyPath = trashedFile.absolutePath
        val legacyOriginalName = "legacy.jpg"

        val legacyPrefs = context.getSharedPreferences("trash_bin_store", Context.MODE_PRIVATE)
        legacyPrefs.edit()
            .putStringSet("trashed_paths", setOf(legacyPath))
            .putString("original_name::$legacyPath", legacyOriginalName)
            .putLong("trashed_at::$legacyPath", nowMs)
            .commit()

        val migratedPaths = TrashBinStore.getTrashedPaths(context)

        assertTrue(migratedPaths.contains(legacyPath))
        assertEquals(legacyOriginalName, TrashBinStore.resolveOriginalName(context, trashedFile))
        assertFalse(legacyPrefs.contains("trashed_paths"))
        assertFalse(legacyPrefs.contains("original_name::$legacyPath"))
        assertFalse(legacyPrefs.contains("trashed_at::$legacyPath"))
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
        TrashBinDatabase.getInstance(context).clearAllRecords()

        context.getSharedPreferences("trash_bin_store", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()

        PreferenceManager.getDefaultSharedPreferences(context)
            .edit()
            .clear()
            .commit()

        if (sandboxDir.exists()) {
            sandboxDir.deleteRecursively()
        }
        sandboxDir.mkdirs()
    }
}
