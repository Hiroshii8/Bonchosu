package com.example.moneyapp.statement

import com.example.moneyapp.data.db.TxType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * The samples below are made up to mimic each layout, not copied from real statements.
 * Add real ones (with names and account numbers removed) as you import your own.
 */
class StatementParserTest {

    private val today = LocalDate.of(2026, 11, 20)
    private fun parse(vararg lines: String) = StatementParser.parse(lines.toList(), today)

    @Test
    fun `DB marker layout with continuation lines and no year`() {
        val s = parse(
            "PERIODE : OKTOBER 2026",
            "TANGGAL KETERANGAN CBG MUTASI SALDO",
            "01/10 SALDO AWAL 1,000,000.00",
            "02/10 TRSF E-BANKING DB 0210/FTSCY/WS95011 50,000.00 DB 950,000.00",
            "BUDI SANTOSO",
            "03/10 TRSF E-BANKING CR 0310/FTSCY/WS95011 200,000.00 1,150,000.00",
            "PT MAJU JAYA",
            "SALDO AKHIR 1,150,000.00",
            "MUTASI DB 50,000.00",
        )
        assertEquals(2, s.rows.size)
        assertEquals(LocalDate.of(2026, 10, 2), s.rows[0].date)
        assertEquals(50_000L, s.rows[0].amount)
        assertEquals(TxType.EXPENSE, s.rows[0].type)
        assertEquals("BUDI SANTOSO", s.rows[0].description)
        assertEquals(TxType.INCOME, s.rows[1].type)
        assertEquals("PT MAJU JAYA", s.rows[1].description)
        assertEquals(true, s.balancesMatch)
    }

    @Test
    fun `debit credit balance columns with indonesian number format`() {
        val s = parse(
            "Tanggal Uraian Debet Kredit Saldo",
            "01/11/2026 Pembayaran Listrik 150.000,00 0,00 850.000,00",
            "02/11/2026 Gaji Bulanan 0,00 5.000.000,00 5.850.000,00",
        )
        assertEquals(150_000L, s.rows[0].amount)
        assertEquals(TxType.EXPENSE, s.rows[0].type)
        assertEquals("Pembayaran Listrik", s.rows[0].description)
        assertEquals(5_000_000L, s.rows[1].amount)
        assertEquals(TxType.INCOME, s.rows[1].type)
    }

    @Test
    fun `column order follows the header when credit comes first`() {
        val s = parse(
            "Date Description Credit Debit Balance",
            "01/11/2026 Something 150,000.00 0.00 850,000.00",
        )
        assertEquals(TxType.INCOME, s.rows.single().type)
    }

    @Test
    fun `running balance decides when there is no other signal`() {
        val s = parse(
            "Saldo Awal 1.000.000,00",
            "05-11-2026 ABC 100.000,00 900.000,00",
            "06-11-2026 DEF 300.000,00 1.200.000,00",
        )
        assertEquals(TxType.EXPENSE, s.rows[0].type)
        assertEquals(TxType.INCOME, s.rows[1].type)
        assertTrue(s.rows.all { it.typeCertain })
    }

    @Test
    fun `running balance beats misleading wording`() {
        val s = parse(
            "Saldo Awal 1.000.000,00",
            "05-11-2026 Pembayaran dari Klien 100.000,00 1.100.000,00",
        )
        assertEquals(TxType.INCOME, s.rows.single().type)
    }

    @Test
    fun `single letter D and K markers`() {
        val s = parse(
            "01/11/2026 Tarik ATM 500.000,00 D",
            "02/11/2026 Setoran 750.000,00 K",
        )
        assertEquals(TxType.EXPENSE, s.rows[0].type)
        assertEquals(TxType.INCOME, s.rows[1].type)
    }

    @Test
    fun `named month dates in indonesian and english`() {
        val s = parse(
            "15 Agustus 2026 Pembayaran Toko 25.000,00 D",
            "16 Aug 2026 Payment Shop 26,000.00 D",
            "17-Okt-26 Pembayaran Warung 27.000,00 D",
        )
        assertEquals(LocalDate.of(2026, 8, 15), s.rows[0].date)
        assertEquals(LocalDate.of(2026, 8, 16), s.rows[1].date)
        assertEquals(LocalDate.of(2026, 10, 17), s.rows[2].date)
    }

    @Test
    fun `year rolls back for a january statement listing december`() {
        val s = parse(
            "Periode: Januari 2027",
            "30/12 Pembayaran 10,000.00 DB",
            "02/01 Pembayaran 20,000.00 DB",
        )
        assertEquals(LocalDate.of(2026, 12, 30), s.rows[0].date)
        assertEquals(LocalDate.of(2027, 1, 2), s.rows[1].date)
    }

    @Test
    fun `no period assumes the most recent past date`() {
        val s = parse("15/12 Pembayaran 10,000.00 DB")
        assertEquals(LocalDate.of(2025, 12, 15), s.rows.single().date)
    }

    @Test
    fun `app history screenshot with date headings and signed amounts`() {
        val s = parse(
            "Riwayat Transaksi",
            "1 Nov 2026",
            "Transfer ke Budi",
            "-Rp50.000",
            "Uang masuk dari Sari +Rp1.250.000",
            "2 Nov 2026",
            "Pembayaran QRIS Kopi -Rp 18.000",
        )
        assertEquals(3, s.rows.size)
        assertEquals(LocalDate.of(2026, 11, 1), s.rows[0].date)
        assertEquals("Transfer ke Budi", s.rows[0].description)
        assertEquals(50_000L, s.rows[0].amount)
        assertEquals(TxType.EXPENSE, s.rows[0].type)
        assertEquals(TxType.INCOME, s.rows[1].type)
        assertEquals(1_250_000L, s.rows[1].amount)
        assertEquals(LocalDate.of(2026, 11, 2), s.rows[2].date)
        assertEquals(18_000L, s.rows[2].amount)
    }

    @Test
    fun `amount on the line after the date`() {
        val s = parse("01/11/2026 Pembayaran Toko", "75.000,00 D")
        assertEquals(75_000L, s.rows.single().amount)
        assertEquals(TxType.EXPENSE, s.rows.single().type)
    }

    @Test
    fun `no signal means an expense marked uncertain`() {
        val row = parse("01/11/2026 ABCDEF 75.000,00").rows.single()
        assertEquals(TxType.EXPENSE, row.type)
        assertFalse(row.typeCertain)
    }

    @Test
    fun `reference numbers and dates are not amounts`() {
        val row = parse("01/11/2026 REF 123456789 2026/11/01 75.000,00 D").rows.single()
        assertEquals(75_000L, row.amount)
    }

    @Test
    fun `footers and summaries are not transactions`() {
        val s = parse(
            "01/11/2026 Pembayaran Toko 75.000,00 D",
            "Halaman 1 dari 3",
            "Total Debet 75.000,00",
            "Bank terdaftar dan diawasi oleh Otoritas Jasa Keuangan",
        )
        assertEquals(1, s.rows.size)
        assertEquals("Pembayaran Toko", s.rows[0].description)
    }

    @Test
    fun `balance check fails when a row was misread`() {
        val s = parse(
            "Saldo Awal 1.000.000,00",
            "01/11/2026 Pembayaran 100.000,00 D",
            "Saldo Akhir 800.000,00",
        )
        assertEquals(false, s.balancesMatch)
    }

    @Test
    fun `OCR boxes are grouped into lines left to right`() {
        val lines = StatementParser.groupIntoLines(
            listOf(
                StatementParser.TextBox("75.000,00", left = 500, centerY = 102, height = 20),
                StatementParser.TextBox("01/11/2026", left = 10, centerY = 100, height = 20),
                StatementParser.TextBox("Toko", left = 200, centerY = 98, height = 20),
                StatementParser.TextBox("Next", left = 10, centerY = 140, height = 20),
            )
        )
        assertEquals(listOf("01/11/2026 Toko 75.000,00", "Next"), lines)
    }
}
