package org.iurl.litegallery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HomeSearchQueryFactoryTest {

    @Test
    fun globalMediaQueryReturnsNullOnlyWhenAllMediaFiltersAreEmpty() {
        assertNull(
            HomeSearchQueryFactory.buildGlobalMediaQuery(
                normalizedName = "",
                typeFilter = MediaTypeFilter.ALL,
                dateRange = null,
                sizeRangeBytes = null
            )
        )

        val typeOnlyQuery = HomeSearchQueryFactory.buildGlobalMediaQuery(
            normalizedName = "",
            typeFilter = MediaTypeFilter.VIDEOS,
            dateRange = null,
            sizeRangeBytes = null
        )

        assertEquals(MediaTypeFilter.VIDEOS, typeOnlyQuery?.typeFilter)
    }

    @Test
    fun globalMediaQueryCarriesNameDateAndSizeFilters() {
        val dateRange = TimeRange(100L, 200L)
        val query = HomeSearchQueryFactory.buildGlobalMediaQuery(
            normalizedName = "IMG*",
            typeFilter = MediaTypeFilter.IMAGES,
            dateRange = dateRange,
            sizeRangeBytes = 1L..10L
        )

        assertEquals("IMG*", query?.normalizedNameQuery)
        assertEquals(MediaTypeFilter.IMAGES, query?.typeFilter)
        assertEquals(dateRange, query?.dateRange)
        assertEquals(1L..10L, query?.sizeRangeBytes)
    }
}
