package com.hiroshi.moneyapp.core

import com.hiroshi.moneyapp.data.settings.HomeLayout
import com.hiroshi.moneyapp.data.settings.HomeSection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
        val layout = HomeLayout(listOf(HomeSection.RECENT, HomeSection.BUDGETS, HomeSection.REVIEW, HomeSection.SPLIT_BILL, HomeSection.IMPORT_STATEMENT), setOf(HomeSection.BUDGETS))
        assertEquals(layout, HomeLayout.decode(layout.encode()))
        val old = HomeLayout.decode("RECENT,-BUDGETS")
        assertEquals(listOf(HomeSection.RECENT, HomeSection.BUDGETS, HomeSection.REVIEW, HomeSection.SPLIT_BILL, HomeSection.IMPORT_STATEMENT), old.order)
        assertEquals(setOf(HomeSection.BUDGETS, HomeSection.IMPORT_STATEMENT), old.hidden)
    }
}
