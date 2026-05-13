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
        val cursor = MatrixCursor(filesProjection())
        repeat(itemCount) { index ->
            val id = index + 1L
            val isVideo = index % 10 == 0
            cursor.addMediaRow(
                id = id,
                path = "/storage/emulated/0/DCIM/item_$id.${if (isVideo) "mp4" else "jpg"}",
                name = "item_$id.${if (isVideo) "mp4" else "jpg"}",
                dateModifiedSeconds = 1_700_000_000L + index,
                size = 1024L + index,
                isVideo = isVideo
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

    @Test
    fun streamSkeletonLoadEventsFiltersToExactFolderPath() = runBlocking {
        val folderPath = "/storage/emulated/0/DCIM/Camera"
        val cursor = MatrixCursor(filesProjection()).apply {
            addMediaRow(
                id = 1L,
                path = "$folderPath/photo.jpg",
                name = "photo.jpg",
                isVideo = false
            )
            addMediaRow(
                id = 2L,
                path = "$folderPath/video.mp4",
                name = "video.mp4",
                isVideo = true
            )
            addMediaRow(
                id = 3L,
                path = "$folderPath/Sub/nested.jpg",
                name = "nested.jpg",
                isVideo = false
            )
            addMediaRow(
                id = 4L,
                path = "${folderPath}2/prefix-sibling.jpg",
                name = "prefix-sibling.jpg",
                isVideo = false
            )
        }

        val events = MediaScanner.streamSkeletonLoadEvents(cursor, exactFolderPath = folderPath).toList()
        val firstScreen = events.first() as LoadEvent.FirstScreen
        val finalProgress = events.last() as LoadEvent.Progress

        assertEquals(listOf(1L, 2L), firstScreen.items.map { it.id })
        assertEquals(2, finalProgress.totalLoaded)
        assertTrue(finalProgress.isFinal)
    }

    @Test
    fun streamFilesCursorOrFallbackUsesFallbackWhenFilesCursorIsEmpty() = runBlocking {
        val fallbackItem = MediaItemSkeleton(
            id = 42L,
            path = "/storage/emulated/0/DCIM/Camera/fallback.jpg",
            name = "fallback.jpg",
            dateModified = 1_700_000_000_000L,
            size = 4096L,
            isVideo = false
        )
        val emptyFilesCursor = MatrixCursor(filesProjection())

        val events = MediaScanner.streamFilesCursorOrFallback(
            cursor = emptyFilesCursor,
            exactFolderPath = "/storage/emulated/0/DCIM/Camera",
            fallbackSkeletons = { listOf(fallbackItem) }
        ).toList()
        val firstScreen = events.first() as LoadEvent.FirstScreen
        val finalProgress = events.last() as LoadEvent.Progress

        assertEquals(listOf(42L), firstScreen.items.map { it.id })
        assertEquals(1, finalProgress.totalLoaded)
        assertTrue(finalProgress.isFinal)
    }

    private fun filesProjection(): Array<String> {
        return arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DATA,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.DATE_MODIFIED,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.MEDIA_TYPE,
            MediaStore.Files.FileColumns.MIME_TYPE
        )
    }

    private fun MatrixCursor.addMediaRow(
        id: Long,
        path: String,
        name: String,
        dateModifiedSeconds: Long = 1_700_000_000L + id,
        size: Long = 1024L + id,
        isVideo: Boolean
    ) {
        addRow(
            arrayOf(
                id,
                path,
                name,
                dateModifiedSeconds,
                size,
                if (isVideo) {
                    MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO
                } else {
                    MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE
                },
                if (isVideo) "video/mp4" else "image/jpeg"
            )
        )
    }
}
