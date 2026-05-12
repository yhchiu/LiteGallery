package org.iurl.litegallery

import android.database.MatrixCursor
import android.provider.MediaStore
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class MediaScannerStreamedLoadTest {

    @Test
    fun streamSkeletonLoadEventsEmitsFirstScreenDeltasAndFinalSignal() = runBlocking {
        val itemCount = 50_000
        val cursor = MatrixCursor(
            arrayOf(
                MediaStore.Files.FileColumns._ID,
                MediaStore.Files.FileColumns.DATA,
                MediaStore.Files.FileColumns.DISPLAY_NAME,
                MediaStore.Files.FileColumns.DATE_MODIFIED,
                MediaStore.Files.FileColumns.SIZE,
                MediaStore.Files.FileColumns.MEDIA_TYPE,
                MediaStore.Files.FileColumns.MIME_TYPE
            )
        )
        repeat(itemCount) { index ->
            val id = index + 1L
            val isVideo = index % 10 == 0
            cursor.addRow(
                arrayOf(
                    id,
                    "/storage/emulated/0/DCIM/item_$id.${if (isVideo) "mp4" else "jpg"}",
                    "item_$id.${if (isVideo) "mp4" else "jpg"}",
                    1_700_000_000L + index,
                    1024L + index,
                    if (isVideo) {
                        MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO
                    } else {
                        MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE
                    },
                    if (isVideo) "video/mp4" else "image/jpeg"
                )
            )
        }

        val events = MediaScanner.streamSkeletonLoadEvents(cursor).toList()
        val firstScreen = events.first() as LoadEvent.FirstScreen
        val progressEvents = events.filterIsInstance<LoadEvent.Progress>()
        val loadedItems = firstScreen.items + progressEvents.flatMap { it.deltaItems }
        val expectedProgressEvents =
            ((itemCount - MediaScanner.FIRST_SCREEN_LIMIT).coerceAtLeast(0) + MediaScanner.CHUNK_SIZE - 1) /
                MediaScanner.CHUNK_SIZE + 1

        assertEquals(MediaScanner.FIRST_SCREEN_LIMIT, firstScreen.items.size)
        assertEquals(expectedProgressEvents, progressEvents.size)
        assertTrue(progressEvents.last().isFinal)
        assertTrue(progressEvents.last().deltaItems.isEmpty())
        assertEquals(itemCount, progressEvents.last().totalLoaded)
        assertEquals(itemCount, loadedItems.size)
        assertEquals((1L..itemCount.toLong()).toList(), loadedItems.map { it.id })
    }
}
