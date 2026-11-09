package com.hiroshi.bonchosu.core

import com.hiroshi.bonchosu.data.settings.HomeLayout
import com.hiroshi.bonchosu.data.settings.HomeSection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class MonthSummaryTest {
    private val today = LocalDate.of(2026, 11, 10)

    @Test
    fun `this month averages over the days so far`() {
        val s = MonthSummary.of(YearMonth.of(2026, 11), income = 5_000_000, spent = 1_000_000, previousSpent = 1_500_000, today = today)
        assertEquals(100_000L, s.dailyAverage)
        assertEquals(4_000_000L, s.net)
        assertEquals(-500_000L, s.vsLastMonth)
    }

    @Test
    fun `past month averages over the whole month and future has none`() {
        assertEquals(10_000L, MonthSummary.of(YearMonth.of(2026, 9), 0, 300_000, 0, today).dailyAverage)
        assertNull(MonthSummary.of(YearMonth.of(2026, 12), 0, 0, 0, today).dailyAverage)
    }

    @Test
    fun `home layout round trips and adds sections it didn't know`() {
        val layout = HomeLayout(HomeSection.entries.reversed(), setOf(HomeSection.BUDGETS))
        assertEquals(layout, HomeLayout.decode(layout.encode()))

        // Missing sections go right after the section they follow by default, hidden only if hidden by default.
        val old = HomeLayout.decode("RECENT,-BUDGETS")
        assertEquals(
            listOf(
                HomeSection.REVIEW, HomeSection.RECENT, HomeSection.SPLIT_BILL, HomeSection.SCAN_RECEIPTS,
                HomeSection.IMPORT_STATEMENT, HomeSection.BUDGETS, HomeSection.QUICK_ACTIONS,
            ),
            old.order,
        )
        assertEquals(setOf(HomeSection.BUDGETS, HomeSection.SCAN_RECEIPTS, HomeSection.IMPORT_STATEMENT), old.hidden)

        // A layout saved before quick actions existed gets them under the budget card, shown.
        val beforeQuick = HomeLayout.decode("REVIEW,SCAN_RECEIPTS,SPLIT_BILL,-IMPORT_STATEMENT,BUDGETS,RECENT")
        assertEquals(beforeQuick.order.indexOf(HomeSection.BUDGETS) + 1, beforeQuick.order.indexOf(HomeSection.QUICK_ACTIONS))
        assertTrue(HomeSection.QUICK_ACTIONS in beforeQuick.visible)
        assertTrue(HomeSection.SCAN_RECEIPTS in beforeQuick.visible)
    }
}
