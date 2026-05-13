package org.iurl.litegallery

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

object SearchDateRangeConverter {
    private val inputFormatters = listOf(
        DateTimeFormatter.ofPattern("uuuu/M/d"),
        DateTimeFormatter.ISO_LOCAL_DATE,
        DateTimeFormatter.ofPattern("uuuu.M.d"),
        DateTimeFormatter.ofPattern("uuuuMMdd")
    )
    private val outputFormatter = DateTimeFormatter.ofPattern("uuuu/MM/dd")

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

    fun parseInputDateOrNull(text: String?): LocalDate? {
        val value = text?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return inputFormatters.firstNotNullOfOrNull { formatter ->
            runCatching { LocalDate.parse(value, formatter) }.getOrNull()
        }
    }

    fun formatInputDateDigits(text: String?): String {
        val digits = text?.filter(Char::isDigit)?.take(8).orEmpty()
        return when {
            digits.length < 4 -> digits
            digits.length == 4 -> "$digits/"
            digits.length < 6 -> "${digits.substring(0, 4)}/${digits.substring(4)}"
            digits.length == 6 -> "${digits.substring(0, 4)}/${digits.substring(4, 6)}/"
            else -> "${digits.substring(0, 4)}/${digits.substring(4, 6)}/${digits.substring(6)}"
        }
    }

    fun inputTextToTimeRangeOrNull(
        startText: String?,
        endText: String?,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): TimeRange? {
        val start = parseInputDateOrNull(startText) ?: return null
        val end = parseInputDateOrNull(endText) ?: return null
        return localDateRangeToTimeRange(start, end, zoneId)
    }

    fun localMsToInputDateText(
        localMs: Long,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): String =
        Instant.ofEpochMilli(localMs)
            .atZone(zoneId)
            .toLocalDate()
            .format(outputFormatter)

    fun localDayStartMsToPickerUtcMs(
        localMs: Long,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Long =
        Instant.ofEpochMilli(localMs)
            .atZone(zoneId)
            .toLocalDate()
            .atStartOfDay(ZoneOffset.UTC)
            .toInstant()
            .toEpochMilli()

    fun localDateRangeToTimeRange(
        startDateInclusive: LocalDate,
        endDateInclusive: LocalDate,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): TimeRange? {
        if (endDateInclusive.isBefore(startDateInclusive)) return null
        return TimeRange(
            startMsInclusive = startDateInclusive.atStartOfDay(zoneId).toInstant().toEpochMilli(),
            endMsExclusive = endDateInclusive.plusDays(1L).atStartOfDay(zoneId).toInstant().toEpochMilli()
        )
    }
}
