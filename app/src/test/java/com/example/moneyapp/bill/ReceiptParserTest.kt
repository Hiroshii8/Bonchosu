package com.example.moneyapp.bill

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** The samples are made up to mimic common receipts. Add real ones, with personal details removed. */
class ReceiptParserTest {

    private fun parse(vararg lines: String) = ReceiptParser.parse(lines.toList())

    @Test
    fun `typical restaurant receipt`() {
        val r = parse(
            "WARUNG SENJA",
            "Jl. Merdeka No. 10",
            "Telp 021-5551234",
            "12/10/2026 19:45",
            "Meja 12 Kasir Ani",
            "Nasi Goreng 50.000",
            "Es Teh 30.000",
            "Sate Ayam 20.000",
            "Subtotal 100.000",
            "Service 5% 5.000",
            "PB1 10% 10.500",
            "Total 115.500",
            "Tunai 120.000",
            "Kembali 4.500",
            "Terima kasih",
        )
        assertEquals("WARUNG SENJA", r.merchant)
        assertEquals(LocalDate.of(2026, 10, 12), r.date)
        assertEquals(listOf("Nasi Goreng", "Es Teh", "Sate Ayam"), r.items.map { it.name })
        assertEquals(100_000L, r.items.sumOf { it.lineTotal })
        assertEquals(listOf(5_000L, 10_500L), r.charges.map { it.amount })
        assertEquals("Service", r.charges[0].name)
        assertEquals(115_500L, r.printedTotal)
        assertFalse(r.taxIncluded)
    }

    @Test
    fun `quantity shapes`() {
        val r = parse(
            "2 x 25.000 50.000",
            "Es Jeruk x2 40,000",
            "Ayam Bakar 2 30.000 60.000",
            "3 Kerupuk 15.000",
            "Subtotal 165.000",
        )
        assertEquals(listOf(2, 2, 3), r.items.drop(0).map { it.quantity }.let { listOf(it[0], it[1], it[2]) })
        assertEquals(listOf("Es Jeruk", "Ayam Bakar", "Kerupuk"), r.items.map { it.name })
    }

    @Test
    fun `item name on the line above its price`() {
        val r = parse("KOPI SORE", "Kopi Susu Gula Aren", "2 x 25.000 50.000", "Total 50.000")
        assertEquals(ReceiptItem("Kopi Susu Gula Aren", 2, 50_000), r.items.single())
    }

    @Test
    fun `leading quantity amount is a line total when the subtotal says so`() {
        val r = parse("2 Nasi Goreng 50.000", "Subtotal 50.000")
        assertEquals(ReceiptItem("Nasi Goreng", 2, 50_000), r.items.single())
    }

    @Test
    fun `leading quantity amount is a unit price when the subtotal says so`() {
        val r = parse("2 Nasi Goreng 25.000", "Es Teh 10.000", "Subtotal 60.000")
        assertEquals(50_000L, r.items[0].lineTotal)
    }

    @Test
    fun `discount rounding and delivery`() {
        val r = parse(
            "Burger 100.000",
            "Diskon -10.000",
            "Ongkir 15.000",
            "Pembulatan 500",
            "Total 105.500",
        )
        assertEquals(-10_000L, r.charges[0].amount)
        assertFalse(r.charges[0].equally)
        assertTrue(r.charges[1].equally)
        assertEquals(500L, r.charges[2].amount)
    }

    @Test
    fun `tax inside the prices is not added by note`() {
        val r = parse("Pizza 110.000", "PPN 10.000", "Total 110.000", "Harga sudah termasuk pajak")
        assertTrue(r.taxIncluded)
        assertTrue(r.charges.isEmpty())
    }

    @Test
    fun `tax inside the prices is not added by arithmetic`() {
        val r = parse("Pizza 110.000", "PPN 10.000", "Total 110.000")
        assertTrue(r.taxIncluded)
        assertTrue(r.charges.isEmpty())
    }

    @Test
    fun `english receipt and comma numbers`() {
        val r = parse("Burger 85,000.00", "Sub Total 85,000.00", "Tax 8,500.00", "Grand Total 93,500.00", "Change 6,500.00")
        assertEquals(85_000L, r.items.single().lineTotal)
        assertEquals(8_500L, r.charges.single().amount)
        assertEquals(93_500L, r.printedTotal)
    }

    @Test
    fun `letter O read inside a number`() {
        assertEquals(25_000L, parse("Teh Tarik 25.0O0").items.single().lineTotal)
    }

    @Test
    fun `spaces OCR drops into dates and amounts`() {
        val r = parse("12/10/ 2026 19:45", "Teh Tarik 25. 000", "PB1 1O% 2.500")
        assertEquals(LocalDate.of(2026, 10, 12), r.date)
        assertEquals(listOf("Teh Tarik"), r.items.map { it.name })
        assertEquals(25_000L, r.items.single().lineTotal)
        assertEquals(2_500L, r.charges.single().amount)
    }

    @Test
    fun `month name dates`() {
        assertEquals(LocalDate.of(2026, 8, 15), parse("15 Agustus 2026", "Soto 30.000").date)
        assertEquals(LocalDate.of(2026, 10, 12), parse("12-Okt-26 19:45", "Soto 30.000").date)
    }

    @Test
    fun `reference numbers phone and dates are not items`() {
        val r = parse("Order 123456", "Telp 08123456789", "01/11/2026", "Soto 30.000")
        assertEquals(listOf("Soto"), r.items.map { it.name })
    }

    @Test
    fun `nothing after the total counts`() {
        val r = parse("Soto 30.000", "Total 30.000", "Member Poin 1.000")
        assertEquals(1, r.items.size)
        assertTrue(r.charges.isEmpty())
    }
}
