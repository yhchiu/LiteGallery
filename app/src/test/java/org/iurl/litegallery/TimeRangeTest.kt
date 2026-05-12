package org.iurl.litegallery

import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class TimeRangeTest {

    @Test
    fun containsUsesHalfOpenRange() {
        val range = TimeRange(100L, 200L)

        assertTrue(100L in range)
        assertTrue(199L in range)
        assertFalse(200L in range)
        assertFalse(99L in range)
    }

    @Test
    fun rejectsEmptyOrInvertedRanges() {
        assertThrows(IllegalArgumentException::class.java) {
            TimeRange(100L, 100L)
        }
        assertThrows(IllegalArgumentException::class.java) {
            TimeRange(200L, 100L)
        }
    }
}
