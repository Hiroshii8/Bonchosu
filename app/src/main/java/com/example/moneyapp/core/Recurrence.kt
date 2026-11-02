package com.example.moneyapp.core

import com.example.moneyapp.data.db.Frequency
import java.time.LocalDate

object Recurrence {

    /**
     * The occurrence after [current]. Monthly/yearly rules keep the start date's day,
     * clamped to short months: a rule starting on the 31st runs on Feb 28/29, then Mar 31.
     */
    fun next(current: LocalDate, start: LocalDate, frequency: Frequency, everyN: Int): LocalDate {
        val n = everyN.coerceAtLeast(1).toLong()
        return when (frequency) {
            Frequency.DAILY -> current.plusDays(n)
            Frequency.WEEKLY -> current.plusWeeks(n)
            Frequency.MONTHLY -> {
                val m = current.withDayOfMonth(1).plusMonths(n)
                m.withDayOfMonth(minOf(start.dayOfMonth, m.lengthOfMonth()))
            }
            Frequency.YEARLY -> {
                val y = current.withDayOfMonth(1).plusYears(n)
                    .withMonth(start.monthValue)
                y.withDayOfMonth(minOf(start.dayOfMonth, y.lengthOfMonth()))
            }
        }
    }

    /** All occurrence dates from [nextRun] up to and including [today] (catch-up after the phone was off). */
    fun dueDates(
        nextRun: LocalDate,
        start: LocalDate,
        frequency: Frequency,
        everyN: Int,
        today: LocalDate,
        endDate: LocalDate?,
        maxCount: Int = 400,
    ): List<LocalDate> {
        val out = mutableListOf<LocalDate>()
        var d = nextRun
        while (!d.isAfter(today) && (endDate == null || !d.isAfter(endDate)) && out.size < maxCount) {
            out += d
            d = next(d, start, frequency, everyN)
        }
        return out
    }
}
