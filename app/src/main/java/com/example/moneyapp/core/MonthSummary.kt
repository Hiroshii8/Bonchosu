package com.hiroshi.moneyapp.core

import java.time.LocalDate
import java.time.YearMonth

/** Figures for one month on the Home summary card. Pure, so it's unit-tested. */
data class MonthSummary(
    val income: Long,
    val spent: Long,
    val previousSpent: Long,
    /** Spent per day so far (this month) or over the whole month (past months). Null for future months. */
    val dailyAverage: Long?,
) {
    val net get() = income - spent
    val vsLastMonth get() = spent - previousSpent

    companion object {
        fun of(month: YearMonth, income: Long, spent: Long, previousSpent: Long, today: LocalDate): MonthSummary {
            val current = YearMonth.from(today)
            val days = when {
                month == current -> today.dayOfMonth
                month.isBefore(current) -> month.lengthOfMonth()
                else -> 0
            }
            return MonthSummary(income, spent, previousSpent, if (days > 0) spent / days else null)
        }
    }
}
