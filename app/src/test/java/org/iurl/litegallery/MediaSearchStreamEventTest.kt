package org.iurl.litegallery

import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaSearchStreamEventTest {

    @Test
    fun skeletonListStreamingUsesFirstScreenAndChunkEvents() = runBlocking {
        val items = (1..12_345).map { index ->
            MediaItemSkeleton(
                id = index.toLong(),
                name = "IMG_$index.jpg",
                path = "/media/IMG_$index.jpg",
                dateModified = index.toLong(),
                size = index.toLong(),
                isVideo = false
            )
        }
        val events = mutableListOf<LoadEvent>()

        MediaScanner.streamSkeletonListLoadEvents(items).collect { events.add(it) }

        assertTrue(events.first() is LoadEvent.FirstScreen)
        assertEquals(MediaScanner.FIRST_SCREEN_LIMIT, (events.first() as LoadEvent.FirstScreen).items.size)
        assertEquals(
            listOf(5_500, 10_500, 12_345, 12_345),
            events.drop(1).filterIsInstance<LoadEvent.Progress>().map { it.totalLoaded }
        )
        assertEquals(true, (events.last() as LoadEvent.Progress).isFinal)
    }
}
