package com.hiroshi.bonchosu.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BudgetAlertsTest {
    private val november = DateRange(1_000, 2_000)
    private val december = DateRange(3_000, 4_000)

    private fun input(spent: Long, range: DateRange = november, levels: List<Int> = BudgetAlerts.DEFAULT_LEVELS, id: Long = 1) =
        BudgetAlerts.Input(id, levels, BudgetProgress(id, limit = 1_000_000, spent = spent, range = range, daysLeft = 10))

    @Test
    fun `nothing below the first level`() {
        val r = BudgetAlerts.check(listOf(input(499_999)), emptySet())
        assertTrue(r.alerts.isEmpty())
        assertTrue(r.sent.isEmpty())
    }

    @Test
    fun `each level fires once`() {
        val first = BudgetAlerts.check(listOf(input(500_000)), emptySet())
        assertEquals(listOf(50), first.alerts.map { it.level })
        val again = BudgetAlerts.check(listOf(input(600_000)), first.sent)
        assertTrue(again.alerts.isEmpty())
        val next = BudgetAlerts.check(listOf(input(760_000)), again.sent)
        assertEquals(listOf(75), next.alerts.map { it.level })
    }

    @Test
    fun `jumping past several levels shows only the highest`() {
        val r = BudgetAlerts.check(listOf(input(1_050_000)), emptySet())
        assertEquals(listOf(100), r.alerts.map { it.level })
        assertEquals(3, r.sent.size)
        assertTrue(BudgetAlerts.check(listOf(input(1_100_000)), r.sent).alerts.isEmpty())
    }

    @Test
    fun `a new period starts fresh`() {
        val nov = BudgetAlerts.check(listOf(input(800_000)), emptySet())
        val dec = BudgetAlerts.check(listOf(input(500_000, december)), nov.sent)
        assertEquals(listOf(50), dec.alerts.map { it.level })
        assertTrue(dec.sent.none { it.contains(":${november.startMs}:") })
    }

    @Test
    fun `levels above 100 percent work and each budget is separate`() {
        val r = BudgetAlerts.check(
            listOf(input(1_250_000, levels = listOf(100, 120)), input(500_000, id = 2)),
            setOf("1:${november.startMs}:100"),
        )
        assertEquals(listOf(1L to 120, 2L to 50), r.alerts.map { it.budgetId to it.level })
    }

    @Test
    fun `levels parse and format`() {
        assertEquals(listOf(50, 90, 120), BudgetAlerts.parseLevels("120, 50,abc,90,50,0"))
        assertEquals("50,75,100", BudgetAlerts.formatLevels(listOf(100, 50, 75)))
    }
}
