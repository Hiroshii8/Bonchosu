package com.hiroshi.bonchosu.bill

import com.hiroshi.bonchosu.data.db.TxType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ReceiptScanTest {

    private val photoDay = LocalDate.of(2026, 11, 8)

    @Test
    fun `a receipt becomes one expense with its printed total`() {
        val r = ReceiptScan.read(
            listOf("KOPI SENJA", "12/10/2026 14:05", "Kopi Susu 2 x 25.000 50.000", "Croissant 30.000", "PB1 10% 8.000", "TOTAL 88.000", "TUNAI 100.000", "KEMBALI 12.000"),
            photoDay,
        )!!
        assertEquals(88_000L, r.row.amount)
        assertEquals(TxType.EXPENSE, r.row.type)
        assertEquals("KOPI SENJA", r.row.description)
        assertEquals(LocalDate.of(2026, 10, 12), r.row.date)
        assertTrue(r.dateFound)
        assertTrue("item names help the category guess", "Kopi Susu" in r.row.rawText)
    }

    @Test
    fun `no date on the receipt uses the photo date`() {
        val r = ReceiptScan.read(listOf("WARUNG BU SRI", "Nasi Campur 25.000", "Es Teh 5.000", "Total 30.000"), photoDay)!!
        assertEquals(photoDay, r.row.date)
        assertFalse(r.dateFound)
    }

    @Test
    fun `no printed total adds up the items`() {
        val r = ReceiptScan.read(listOf("TOKO MAJU", "Sabun 12.000", "Sampo 23.500"), photoDay)!!
        assertEquals(35_500L, r.row.amount)
    }

    @Test
    fun `photos that are not receipts are skipped`() {
        assertNull(ReceiptScan.read(listOf("Selamat ulang tahun!", "Semoga sehat selalu"), photoDay))
        assertNull(ReceiptScan.read(listOf("Meeting notes", "Budget 2027 50.000"), photoDay))
        assertNull(ReceiptScan.read(emptyList(), photoDay))
    }

    @Test
    fun `the same receipt twice in a batch is flagged`() {
        val a = ReceiptScan.read(listOf("WARUNG BU SRI", "Nasi Campur 25.000", "Total 25.000"), photoDay)!!.row
        val b = ReceiptScan.read(listOf("KOPI SENJA", "Kopi 20.000", "Total 20.000"), photoDay)!!.row
        assertEquals(setOf(2), ReceiptScan.repeats(listOf(a, b, a)))
    }
}
