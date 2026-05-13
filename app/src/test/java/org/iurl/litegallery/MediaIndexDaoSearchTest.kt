package org.iurl.litegallery

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class MediaIndexDaoSearchTest {

    private lateinit var database: MediaIndexDatabase
    private lateinit var dao: MediaIndexDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, MediaIndexDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.mediaIndexDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun searchMediaFiltersByNameTypeDateAndSize() = runBlocking {
        dao.upsertMedia(
            listOf(
                entity(
                    id = 1L,
                    mediaType = MEDIA_INDEX_TYPE_IMAGE,
                    name = "IMG_001.jpg",
                    dateModifiedMs = 150L,
                    sizeBytes = 500L
                ),
                entity(
                    id = 2L,
                    mediaType = MEDIA_INDEX_TYPE_VIDEO,
                    name = "IMG_002.mp4",
                    dateModifiedMs = 150L,
                    sizeBytes = 500L
                ),
                entity(
                    id = 3L,
                    mediaType = MEDIA_INDEX_TYPE_IMAGE,
                    name = "IMG_OLD.jpg",
                    dateModifiedMs = 50L,
                    sizeBytes = 500L
                ),
                entity(
                    id = 4L,
                    mediaType = MEDIA_INDEX_TYPE_IMAGE,
                    name = "IMG_LARGE.jpg",
                    dateModifiedMs = 150L,
                    sizeBytes = 5_000L
                )
            )
        )

        val results = dao.searchMedia(
            nameLikePattern = MediaSearchSql.likePatternForNormalizedName("IMG*"),
            mediaType = MEDIA_INDEX_TYPE_IMAGE,
            dateStartMs = 100L,
            dateEndMs = 200L,
            sizeMinBytes = 1L,
            sizeMaxBytes = 1_000L,
            limit = 20
        )

        assertEquals(listOf("IMG_001.jpg"), results.map { it.name })
    }

    @Test
    fun searchMediaOrdersByNewestThenNameAndHonorsLimit() = runBlocking {
        dao.upsertMedia(
            listOf(
                entity(id = 1L, name = "b.jpg", dateModifiedMs = 200L),
                entity(id = 2L, name = "a.jpg", dateModifiedMs = 200L),
                entity(id = 3L, name = "new.jpg", dateModifiedMs = 300L)
            )
        )

        val results = dao.searchMedia(
            nameLikePattern = null,
            mediaType = null,
            dateStartMs = null,
            dateEndMs = null,
            sizeMinBytes = null,
            sizeMaxBytes = null,
            limit = 2
        )

        assertEquals(listOf("new.jpg", "a.jpg"), results.map { it.name })
    }

    @Test
    fun searchMediaWindowSupportsFilterOnlyQuery() = runBlocking {
        dao.upsertMedia(
            listOf(
                entity(id = 1L, mediaType = MEDIA_INDEX_TYPE_IMAGE, name = "photo.jpg"),
                entity(id = 2L, mediaType = MEDIA_INDEX_TYPE_VIDEO, name = "clip.mp4")
            )
        )

        val results = dao.searchMediaWindow(
            MediaIndexSearchSql.buildQuery(
                query = MediaSearchQuery(typeFilter = MediaTypeFilter.VIDEOS),
                sortOrder = "date_desc",
                limit = 20,
                offset = 0
            )
        )

        assertEquals(listOf("clip.mp4"), results.map { it.name })
    }

    @Test
    fun searchMediaWindowAppliesSortOrderAndOffset() = runBlocking {
        dao.upsertMedia(
            listOf(
                entity(id = 1L, name = "a.jpg", sizeBytes = 10L),
                entity(id = 2L, name = "b.jpg", sizeBytes = 30L),
                entity(id = 3L, name = "c.jpg", sizeBytes = 20L)
            )
        )

        val results = dao.searchMediaWindow(
            MediaIndexSearchSql.buildQuery(
                query = MediaSearchQuery(sizeRangeBytes = 1L..100L),
                sortOrder = "size_desc",
                limit = 2,
                offset = 1
            )
        )

        assertEquals(listOf("c.jpg", "a.jpg"), results.map { it.name })
    }

    @Test
    fun searchMediaWindowDoesNotCapResultsAtOneThousand() = runBlocking {
        val items = (1..1_205).map { index ->
            entity(
                id = index.toLong(),
                name = "IMG_$index.jpg",
                dateModifiedMs = index.toLong()
            )
        }
        dao.upsertMedia(items)

        val query = MediaSearchQuery(
            normalizedNameQuery = "IMG*",
            nameMatcher = NameMatcher.compile("IMG*")
        )
        val firstWindow = dao.searchMediaWindow(
            MediaIndexSearchSql.buildQuery(query, sortOrder = "date_asc", limit = 700, offset = 0)
        )
        val secondWindow = dao.searchMediaWindow(
            MediaIndexSearchSql.buildQuery(query, sortOrder = "date_asc", limit = 700, offset = 700)
        )

        assertEquals(700, firstWindow.size)
        assertEquals(505, secondWindow.size)
        assertEquals("IMG_1.jpg", firstWindow.first().name)
        assertEquals("IMG_1205.jpg", secondWindow.last().name)
    }

    private fun entity(
        id: Long,
        mediaType: String = MEDIA_INDEX_TYPE_IMAGE,
        name: String = "image.jpg",
        dateModifiedMs: Long = 100L,
        sizeBytes: Long = 100L
    ): MediaIndexEntity {
        val extension = if (mediaType == MEDIA_INDEX_TYPE_VIDEO) "mp4" else "jpg"
        return MediaIndexEntity(
            mediaType = mediaType,
            mediaStoreId = id,
            path = "/storage/emulated/0/DCIM/$name",
            folderPath = "/storage/emulated/0/DCIM",
            name = name,
            dateModifiedMs = dateModifiedMs,
            sizeBytes = sizeBytes,
            mimeType = if (mediaType == MEDIA_INDEX_TYPE_VIDEO) "video/$extension" else "image/$extension",
            durationMs = 0L,
            width = 0,
            height = 0,
            generationAdded = 0L,
            generationModified = 0L,
            lastSeenScanId = 1L,
            updatedAtMs = 1L
        )
    }
}
