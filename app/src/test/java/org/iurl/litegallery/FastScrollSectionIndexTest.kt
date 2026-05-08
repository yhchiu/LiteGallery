package org.iurl.litegallery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FastScrollSectionIndexTest {

    @Test
    fun emptySectionsReturnNull() {
        assertNull(FastScrollSectionIndex.titleForPosition(emptyList(), 0))
    }

    @Test
    fun negativePositionReturnsNull() {
        assertNull(FastScrollSectionIndex.titleForPosition(sections, -1))
    }

    @Test
    fun positionBeforeFirstHeaderReturnsNull() {
        assertNull(FastScrollSectionIndex.titleForPosition(sections, 1))
    }

    @Test
    fun positionAtHeaderReturnsTitle() {
        assertEquals("A", FastScrollSectionIndex.titleForPosition(sections, 2))
        assertEquals("B", FastScrollSectionIndex.titleForPosition(sections, 7))
    }

    @Test
    fun positionBetweenHeadersReturnsPreviousTitle() {
        assertEquals("A", FastScrollSectionIndex.titleForPosition(sections, 3))
        assertEquals("B", FastScrollSectionIndex.titleForPosition(sections, 9))
    }

    @Test
    fun positionAfterLastHeaderReturnsLastTitle() {
        assertEquals("C", FastScrollSectionIndex.titleForPosition(sections, 50))
    }

    @Test
    fun singleSectionReturnsTitleAfterHeader() {
        val single = listOf(FastScrollSection(adapterPosition = 0, title = "Only"))

        assertEquals("Only", FastScrollSectionIndex.titleForPosition(single, 0))
        assertEquals("Only", FastScrollSectionIndex.titleForPosition(single, 20))
    }

    private val sections = listOf(
        FastScrollSection(adapterPosition = 2, title = "A"),
        FastScrollSection(adapterPosition = 7, title = "B"),
        FastScrollSection(adapterPosition = 12, title = "C")
    )
}
