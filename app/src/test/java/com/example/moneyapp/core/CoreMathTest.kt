package com.hiroshi.moneyapp.core

import com.hiroshi.moneyapp.data.db.BudgetPeriod
import com.hiroshi.moneyapp.data.db.Frequency
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class CoreMathTest {

    private val zone = ZoneId.of("Asia/Jakarta")

    @Test
    fun `rupiah formatting and parsing`() {
        assertEquals("Rp 12.480.500", Money.format(12_480_500))
        assertEquals("−Rp 35.000", Money.format(-35_000))
        assertEquals("+Rp 9.500.000", Money.formatSigned(9_500_000))
        assertEquals("1,2 jt", Money.formatShort(1_215_300))
        assertEquals("850 rb", Money.formatShort(850_000))
        assertEquals(1_250_000L, Money.parseRupiah("1.250.000,00"))
        assertEquals(1_250_000L, Money.parseRupiah("1,250,000.00"))
        assertEquals(50_000L, Money.parseRupiah("50.000."))
        assertEquals(1_500L, Money.parseRupiah("1.500"))
    }

    @Test
    fun `monthly recurrence keeps day and clamps short months`() {
        val start = LocalDate.of(2026, 1, 31)
        val feb = Recurrence.next(start, start, Frequency.MONTHLY, 1)
        assertEquals(LocalDate.of(2026, 2, 28), feb)
        assertEquals(LocalDate.of(2026, 3, 31), Recurrence.next(feb, start, Frequency.MONTHLY, 1))
    }

    @Test
    fun `missed occurrences are caught up`() {
        val start = LocalDate.of(2026, 10, 1)
        val due = Recurrence.dueDates(start, start, Frequency.WEEKLY, 1, LocalDate.of(2026, 10, 18), null)
        assertEquals(listOf(1, 8, 15), due.map { it.dayOfMonth })
    }

    @Test
    fun `recurrence stops at end date`() {
        val start = LocalDate.of(2026, 10, 1)
        val due = Recurrence.dueDates(start, start, Frequency.DAILY, 1, LocalDate.of(2026, 10, 18), LocalDate.of(2026, 10, 3))
        assertEquals(3, due.size)
    }

    @Test
    fun `budget progress per category and safe-to-spend`() {
        val today = LocalDate.of(2026, 10, 5)
        val inOct = Periods.startOfDay(LocalDate.of(2026, 10, 2), zone) + 1000
        val inSep = Periods.startOfDay(LocalDate.of(2026, 9, 30), zone) + 1000
        val expenses = listOf(
            ExpensePoint(540_000, 1, 3, inOct),
            ExpensePoint(100_000, 1, 5, inOct),
            ExpensePoint(999_000, 1, 3, inSep), // last month: ignored
        )
        val budgets = listOf(
            BudgetSpec(1, 3, null, 600_000, BudgetPeriod.MONTHLY),
            BudgetSpec(2, null, null, 6_000_000, BudgetPeriod.MONTHLY),
        )
        val (shopping, total) = BudgetMath.progress(budgets, expenses, today, zone)
        assertEquals(540_000L, shopping.spent)
        assertTrue(shopping.nearLimit)
        assertEquals(640_000L, total.spent)
        assertEquals(27, total.daysLeft) // 5..31 Oct
        assertEquals((6_000_000L - 640_000L) / 27, total.perDayLeft)
    }
}
