package org.iurl.litegallery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime

class SearchDateRangeConverterTest {

    @Test
    fun convertsPickerUtcMidnightToLocalDayInTaipei() {
        val utcMs = Instant.parse("2026-05-11T00:00:00Z").toEpochMilli()
        val zone = ZoneId.of("Asia/Taipei")

        val range = SearchDateRangeConverter.toTimeRangeOrNull(utcMs, utcMs, zone)!!

        assertEquals(epochMs(2026, 5, 11, zone), range.startMsInclusive)
        assertEquals(epochMs(2026, 5, 12, zone), range.endMsExclusive)
    }

    @Test
    fun utcZoneKeepsPickerMidnight() {
        val utcMs = Instant.parse("2026-05-11T00:00:00Z").toEpochMilli()

        val range = SearchDateRangeConverter.toTimeRangeOrNull(utcMs, utcMs, ZoneOffset.UTC)!!

        assertEquals(utcMs, range.startMsInclusive)
        assertEquals(Instant.parse("2026-05-12T00:00:00Z").toEpochMilli(), range.endMsExclusive)
    }

    @Test
    fun handlesDstEndAndStartByLocalCalendarDay() {
        val auckland = ZoneId.of("Pacific/Auckland")
        val aucklandDay = Instant.parse("2026-04-05T00:00:00Z").toEpochMilli()
        val aucklandRange = SearchDateRangeConverter.toTimeRangeOrNull(aucklandDay, aucklandDay, auckland)!!
        assertEquals(25L * 60L * 60L * 1000L, aucklandRange.endMsExclusive - aucklandRange.startMsInclusive)

        val newYork = ZoneId.of("America/New_York")
        val newYorkDay = Instant.parse("2026-03-08T00:00:00Z").toEpochMilli()
        val newYorkRange = SearchDateRangeConverter.toTimeRangeOrNull(newYorkDay, newYorkDay, newYork)!!
        assertEquals(23L * 60L * 60L * 1000L, newYorkRange.endMsExclusive - newYorkRange.startMsInclusive)
    }

    @Test
    fun invalidSelectionsReturnNull() {
        val may11 = Instant.parse("2026-05-11T00:00:00Z").toEpochMilli()
        val may10 = Instant.parse("2026-05-10T00:00:00Z").toEpochMilli()

        assertNull(SearchDateRangeConverter.toTimeRangeOrNull(null, may11))
        assertNull(SearchDateRangeConverter.toTimeRangeOrNull(may11, null))
        assertNull(SearchDateRangeConverter.toTimeRangeOrNull(may11, may10))
    }

    private fun epochMs(year: Int, month: Int, day: Int, zoneId: ZoneId): Long =
        ZonedDateTime.of(year, month, day, 0, 0, 0, 0, zoneId)
            .toInstant()
            .toEpochMilli()
}
