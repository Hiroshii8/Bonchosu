package com.hiroshi.bonchosu.core

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/** A closed range of epoch millis [startMs, endMs]. */
data class DateRange(val startMs: Long, val endMs: Long) {
    operator fun contains(ms: Long) = ms in startMs..endMs
}

enum class PeriodKind { WEEK, MONTH, YEAR }

object Periods {

    fun zone(): ZoneId = ZoneId.systemDefault()

    fun startOfDay(date: LocalDate, zone: ZoneId = zone()): Long =
        date.atStartOfDay(zone).toInstant().toEpochMilli()

    fun endOfDay(date: LocalDate, zone: ZoneId = zone()): Long =
        date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1

    fun toDate(ms: Long, zone: ZoneId = zone()): LocalDate =
        Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()

    fun month(ym: YearMonth, zone: ZoneId = zone()) =
        DateRange(startOfDay(ym.atDay(1), zone), endOfDay(ym.atEndOfMonth(), zone))

    /** Weeks start on Monday (the usual convention in Indonesia). */
    fun week(containing: LocalDate, zone: ZoneId = zone()): DateRange {
        val start = containing.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        return DateRange(startOfDay(start, zone), endOfDay(start.plusDays(6), zone))
    }

    fun year(year: Int, zone: ZoneId = zone()) =
        DateRange(startOfDay(LocalDate.of(year, 1, 1), zone), endOfDay(LocalDate.of(year, 12, 31), zone))

    /** The period of [kind] that contains [anchor], shifted by [offset] periods. */
    fun of(kind: PeriodKind, anchor: LocalDate, offset: Int = 0, zone: ZoneId = zone()): DateRange = when (kind) {
        PeriodKind.WEEK -> week(anchor.plusWeeks(offset.toLong()), zone)
        PeriodKind.MONTH -> month(YearMonth.from(anchor).plusMonths(offset.toLong()), zone)
        PeriodKind.YEAR -> year(anchor.year + offset, zone)
    }

    /** Days left in the range counting today, at least 1. */
    fun daysLeft(range: DateRange, today: LocalDate, zone: ZoneId = zone()): Int {
        val end = toDate(range.endMs, zone)
        val days = java.time.temporal.ChronoUnit.DAYS.between(today, end).toInt() + 1
        return days.coerceAtLeast(1)
    }
}
