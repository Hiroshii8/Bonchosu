package com.hiroshi.moneyapp.core

import com.hiroshi.moneyapp.core.BillSplit.Charge
import com.hiroshi.moneyapp.core.BillSplit.Item
import org.junit.Assert.assertEquals
import org.junit.Test

class BillSplitTest {
    private val budi = 1L
    private val sari = 2L
    private val rina = 3L

    @Test
    fun `order sized shares of tax and service`() {
        val s = BillSplit.split(
            listOf(budi, sari),
            listOf(
                Item(1, 50_000, mapOf(budi to 1)),
                Item(2, 30_000, mapOf(sari to 1)),
                Item(3, 20_000, mapOf(budi to 1, sari to 1)),
            ),
            listOf(Charge(5_000, equally = false), Charge(10_500, equally = false)),
        )
        assertEquals(69_300L, s.getValue(budi).total)
        assertEquals(46_200L, s.getValue(sari).total)
        assertEquals(60_000L, s.getValue(budi).itemsTotal)
    }

    @Test
    fun `rounding always adds up to the bill`() {
        val people = listOf(budi, sari, rina)
        val s = BillSplit.split(
            people,
            listOf(Item(1, 100_000, people.associateWith { 1 }), Item(2, 10_001, mapOf(budi to 1))),
            listOf(Charge(10_007, equally = false), Charge(-3_333, equally = false), Charge(5_000, equally = true)),
        )
        assertEquals(100_000L + 10_001 + 10_007 - 3_333 + 5_000, s.values.sumOf { it.total })
    }

    @Test
    fun `leftover rupiah goes to the largest fraction`() {
        assertEquals(mapOf(budi to 33L, sari to 67L), BillSplit.allocate(100, mapOf(budi to 1L, sari to 2L)))
    }

    @Test
    fun `discount lowers shares by order size`() {
        val s = BillSplit.split(
            listOf(budi, sari),
            listOf(Item(1, 75_000, mapOf(budi to 1)), Item(2, 25_000, mapOf(sari to 1))),
            listOf(Charge(-10_000, equally = false)),
        )
        assertEquals(67_500L, s.getValue(budi).total)
        assertEquals(22_500L, s.getValue(sari).total)
    }

    @Test
    fun `split quantities follow the portions eaten`() {
        val s = BillSplit.split(listOf(budi, sari), listOf(Item(1, 90_000, mapOf(budi to 2, sari to 1))), emptyList())
        assertEquals(60_000L, s.getValue(budi).total)
        assertEquals(30_000L, s.getValue(sari).total)
    }

    @Test
    fun `order costs carry their part of the fees and add up to the share`() {
        val share = BillSplit.split(
            listOf(budi, sari),
            listOf(
                Item(1, 50_000, mapOf(budi to 1)),
                Item(2, 30_000, mapOf(sari to 1)),
                Item(3, 20_000, mapOf(budi to 1, sari to 1)),
            ),
            listOf(Charge(15_500, equally = false)),
        ).getValue(budi)
        val costs = BillSplit.orderCosts(share)
        assertEquals(57_750L, costs[1L])
        assertEquals(11_550L, costs[3L])
        assertEquals(share.total, costs.values.sum())
    }

    @Test
    fun `equal fees skip people who ordered nothing`() {
        val s = BillSplit.split(
            listOf(budi, sari, rina),
            listOf(Item(1, 70_000, mapOf(budi to 1)), Item(2, 10_000, mapOf(sari to 1))),
            listOf(Charge(10_000, equally = true)),
        )
        assertEquals(75_000L, s.getValue(budi).total)
        assertEquals(15_000L, s.getValue(sari).total)
        assertEquals(0L, s.getValue(rina).total)
    }

    @Test
    fun `person with no items pays no proportional fees`() {
        val s = BillSplit.split(listOf(budi, rina), listOf(Item(1, 50_000, mapOf(budi to 1))), listOf(Charge(5_000, equally = false)))
        assertEquals(55_000L, s.getValue(budi).total)
        assertEquals(0L, s.getValue(rina).total)
    }
}
