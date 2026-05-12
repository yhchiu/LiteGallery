package org.iurl.litegallery

import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

object SearchDateRangeConverter {
    fun pickerUtcMsToLocalStartOfDayMs(
        utcMs: Long,
        zoneId: ZoneId = ZoneId.systemDefault(),
        daysOffset: Long = 0L
    ): Long =
        Instant.ofEpochMilli(utcMs)
            .atZone(ZoneOffset.UTC)
            .toLocalDate()
            .plusDays(daysOffset)
            .atStartOfDay(zoneId)
            .toInstant()
            .toEpochMilli()

    fun toTimeRangeOrNull(
        startUtcMs: Long?,
        endUtcMs: Long?,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): TimeRange? {
        if (startUtcMs == null || endUtcMs == null || startUtcMs > endUtcMs) return null
        return TimeRange(
            startMsInclusive = pickerUtcMsToLocalStartOfDayMs(startUtcMs, zoneId),
            endMsExclusive = pickerUtcMsToLocalStartOfDayMs(endUtcMs, zoneId, daysOffset = 1L)
        )
    }
}
