package com.hiroshi.moneyapp.core

import com.hiroshi.moneyapp.data.db.BudgetPeriod
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** Minimal expense info needed for budgets (keeps this file free of Room types, so it's unit-testable). */
data class ExpensePoint(val amount: Long, val walletId: Long, val categoryId: Long?, val occurredAt: Long)

data class BudgetSpec(val id: Long, val categoryId: Long?, val walletId: Long?, val amount: Long, val period: BudgetPeriod)

data class BudgetProgress(
    val budgetId: Long,
    val limit: Long,
    val spent: Long,
    val range: DateRange,
    val daysLeft: Int,
) {
    val remaining: Long get() = limit - spent
    val fraction: Float get() = if (limit <= 0) 0f else (spent.toFloat() / limit)
    val overBudget: Boolean get() = spent > limit
    /** Close to the limit: worth a warning colour. */
    val nearLimit: Boolean get() = !overBudget && fraction >= 0.85f
    /** What you can still spend per day without going over ("safe to spend"). */
    val perDayLeft: Long get() = if (remaining <= 0) 0 else remaining / daysLeft
}

object BudgetMath {

    fun rangeFor(period: BudgetPeriod, today: LocalDate, zone: ZoneId = Periods.zone()): DateRange = when (period) {
        BudgetPeriod.MONTHLY -> Periods.month(YearMonth.from(today), zone)
        BudgetPeriod.WEEKLY -> Periods.week(today, zone)
    }

    /** Earliest start among the current periods, so one query covers every budget. */
    fun queryRange(today: LocalDate, zone: ZoneId = Periods.zone()): DateRange {
        val m = rangeFor(BudgetPeriod.MONTHLY, today, zone)
        val w = rangeFor(BudgetPeriod.WEEKLY, today, zone)
        return DateRange(minOf(m.startMs, w.startMs), maxOf(m.endMs, w.endMs))
    }

    fun progress(
        budgets: List<BudgetSpec>,
        expenses: List<ExpensePoint>,
        today: LocalDate,
        zone: ZoneId = Periods.zone(),
    ): List<BudgetProgress> = budgets.map { b ->
        val range = rangeFor(b.period, today, zone)
        val spent = expenses.asSequence()
            .filter { it.occurredAt in range }
            .filter { b.categoryId == null || it.categoryId == b.categoryId }
            .filter { b.walletId == null || it.walletId == b.walletId }
            .sumOf { it.amount }
        BudgetProgress(b.id, b.amount, spent, range, Periods.daysLeft(range, today, zone))
    }
}
