package com.hiroshi.bonchosu.capture

import com.hiroshi.bonchosu.data.db.TxType
import com.hiroshi.bonchosu.data.db.WalletEntity
import com.hiroshi.bonchosu.data.db.WalletType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The samples below are made-up examples of typical wording.
 * Replace/add real ones from your phone (DEBUG_CAPTURE in TransactionListenerService) and keep them as fixtures.
 */
class NotificationParserTest {

    private val bank = KeywordParser("BCA", WalletType.BANK)
    private val ewallet = KeywordParser("GoPay", WalletType.EWALLET)
    private val superbank = SuperbankParser()

    @Test
    fun `debit purchase with merchant`() {
        val p = bank.parse("BCA", "Transaksi debit Rp 35.000 di KOPI SENJA berhasil.", 1L)
        assertNotNull(p)
        assertEquals(35_000L, p!!.amount)
        assertEquals(TxType.EXPENSE, p.type)
        assertEquals("KOPI SENJA", p.merchant)
        assertTrue(!p.transferCandidate)
    }

    @Test
    fun `incoming transfer is income`() {
        val p = bank.parse("Dana Masuk", "Anda menerima transfer Rp 9.500.000,00 dari PT MAJU JAYA.", 1L)
        assertEquals(TxType.INCOME, p!!.type)
        assertEquals(9_500_000L, p.amount)
    }

    @Test
    fun `qris payment`() {
        val p = ewallet.parse("Pembayaran berhasil", "Pembayaran QRIS Rp18.000 ke WARUNG BU SRI berhasil", 1L)
        assertEquals(TxType.EXPENSE, p!!.type)
        assertEquals(18_000L, p.amount)
        assertEquals("WARUNG BU SRI", p.merchant)
    }

    @Test
    fun `top up from bank is a transfer candidate going out`() {
        val p = bank.parse("BCA", "Top Up GoPay Rp 200.000 berhasil", 1L)
        assertEquals(TxType.EXPENSE, p!!.type)
        assertTrue(p.transferCandidate)
    }

    @Test
    fun `top up seen by the e-wallet is money in`() {
        val p = ewallet.parse("GoPay", "Top up Rp200.000 berhasil. Saldo kamu sekarang Rp350.000", 1L)
        assertEquals(TxType.INCOME, p!!.type)
        assertEquals(200_000L, p.amount) // first amount, not the balance
        assertTrue(p.transferCandidate)
    }

    @Test
    fun `international format amount`() {
        val p = bank.parse("Card", "Purchase IDR 1,250,000.00 at UNIQLO", 1L)
        assertEquals(1_250_000L, p!!.amount)
        assertEquals("UNIQLO", p.merchant)
    }

    @Test
    fun `otp and promos are ignored`() {
        assertNull(bank.parse("BCA", "Kode OTP 123456 untuk transaksi Rp 500.000. Jangan berikan ke siapapun.", 1L))
        assertNull(ewallet.parse("Promo", "Diskon 50% pembayaran pakai GoPay, maks Rp 20.000", 1L))
    }

    @Test
    fun `failed transactions are ignored`() {
        assertNull(bank.parse("BCA", "Transaksi Rp 100.000 gagal diproses", 1L))
    }

    @Test
    fun `no amount means no capture`() {
        assertNull(bank.parse("BCA", "Transaksi berhasil", 1L))
    }

    @Test
    fun `superbank transfer out is an expense to the title's recipient`() {
        val p = superbank.parse("Transfer ke Budi Santoso", "Transfer sebesar Rp.50,000.00 dari Tabungan kamu berhasil", 1L)
        assertEquals(TxType.EXPENSE, p!!.type)
        assertEquals(50_000L, p.amount)
        assertEquals("Budi Santoso", p.merchant)
        assertEquals("Superbank", p.source)
        assertEquals(WalletType.BANK, p.walletType)
    }

    @Test
    fun `superbank recipient containing masuk is still an expense`() {
        val p = superbank.parse("Transfer ke Masuk Jaya", "Transfer sebesar Rp.1,250,000.00 dari Tabungan kamu berhasil", 1L)
        assertEquals(TxType.EXPENSE, p!!.type)
        assertEquals(1_250_000L, p.amount)
    }

    @Test
    fun `superbank masuk is income`() {
        val inText = superbank.parse("Superbank", "Uang masuk sebesar Rp.750,000.00 ke Tabungan kamu", 1L)
        assertEquals(TxType.INCOME, inText!!.type)
        assertEquals(750_000L, inText.amount)

        val inTitle = superbank.parse("Dana Masuk", "Kamu dapat Rp.20,000.00", 1L)
        assertEquals(TxType.INCOME, inTitle!!.type)
    }

    @Test
    fun `superbank ignores failed transfers and otp`() {
        assertNull(superbank.parse("Transfer ke Budi", "Transfer sebesar Rp.50,000.00 dari Tabungan kamu gagal", 1L))
        assertNull(superbank.parse("Superbank", "Kode OTP 123456 untuk uang masuk Rp.50,000.00", 1L))
    }

    @Test
    fun `superbank package uses the superbank parser`() {
        val chosen = listOf(CaptureApp("id.co.bankfama.android", "Superbank", null))
        assertTrue(ParserRegistry.resolve("id.co.bankfama.android", chosen, null) is SuperbankParser)
    }

    @Test
    fun `seabank real time transfer is an expense`() {
        val chosen = listOf(CaptureApp("id.co.bankbkemobile.digitalbank", "SeaBank", null))
        val seabank = ParserRegistry.resolve("id.co.bankbkemobile.digitalbank", chosen, null)!!
        val p = seabank.parse("SeaBank", "Kamu baru melakukan transfer real-time senilai Rp35.000 pada 05 NOV 2026", 1L)
        assertEquals(TxType.EXPENSE, p!!.type)
        assertEquals(35_000L, p.amount)
        assertEquals(WalletType.BANK, p.walletType)
    }

    @Test
    fun `incoming transfer still wins over the new transfer words`() {
        val p = bank.parse("BCA", "Kamu menerima transfer real-time senilai Rp50.000", 1L)
        assertEquals(TxType.INCOME, p!!.type)
    }

    @Test
    fun `with no apps picked nothing is read`() {
        assertNull(ParserRegistry.resolve("com.bca", emptyList(), null))
        assertNull(ParserRegistry.resolve("id.co.bankfama.android", emptyList(), null))
    }

    @Test
    fun `a known e-wallet keeps its type when not linked to a wallet`() {
        val chosen = listOf(CaptureApp("ovo.id", "OVO", null))
        assertEquals(WalletType.EWALLET, ParserRegistry.resolve("ovo.id", chosen, null)!!.walletType)
    }

    @Test
    fun `apps recording into a wallet`() {
        val bca = WalletEntity(id = 1, name = "BCA", type = WalletType.BANK, captureSource = "BCA")
        val cash = WalletEntity(id = 2, name = "Cash", type = WalletType.CASH)
        val superbank = WalletEntity(id = 3, name = "Superbank", type = WalletType.BANK, captureSource = "Superbank")
        val chosen = listOf(
            CaptureApp("com.bca", "BCA", null),                 // records into the wallet created for it
            CaptureApp("id.dana", "DANA", 2),                    // linked directly
            CaptureApp("id.co.bankfama.android", "Super", null), // its own parser names the wallet "Superbank"
        )
        assertEquals(listOf("BCA"), chosen.recordingInto(bca).map { it.label })
        assertEquals(listOf("DANA"), chosen.recordingInto(cash).map { it.label })
        assertEquals(listOf("Super"), chosen.recordingInto(superbank).map { it.label })
    }

    @Test
    fun `picked apps are the only ones read`() {
        val chosen = listOf(CaptureApp("com.unknown.bank", "Bank Baru", walletId = 4), CaptureApp("id.co.bankfama.android", "Superbank", null))
        assertNull(ParserRegistry.resolve("com.bca", chosen, null))
        assertEquals("Bank Baru", ParserRegistry.resolve("com.unknown.bank", chosen, null)?.source)
        assertTrue(ParserRegistry.resolve("id.co.bankfama.android", chosen, null) is SuperbankParser)
    }

    @Test
    fun `a picked app follows its wallet type for top ups`() {
        val chosen = listOf(CaptureApp("com.unknown.wallet", "Dompet", walletId = 2))
        val asEwallet = ParserRegistry.resolve("com.unknown.wallet", chosen, WalletType.EWALLET)!!
        assertEquals(TxType.INCOME, asEwallet.parse("Dompet", "Top up Rp50.000 berhasil", 1L)!!.type)
        val asBank = ParserRegistry.resolve("com.unknown.wallet", chosen, null)!!
        assertEquals(TxType.EXPENSE, asBank.parse("Dompet", "Top up Rp50.000 berhasil", 1L)!!.type)
    }

    @Test
    fun `picked apps survive storage`() {
        val app = CaptureApp("id.bank", "My\tBank", 7)
        assertEquals(CaptureApp("id.bank", "My Bank", 7), CaptureApp.decode(CaptureApp.encode(app)))
        assertEquals(CaptureApp("id.bank", "Bank", null), CaptureApp.decode(CaptureApp.encode(CaptureApp("id.bank", "Bank", null))))
        assertNull(CaptureApp.decode("garbage"))
    }

    @Test
    fun `english payment with merchant`() {
        val p = ewallet.parse("Payment successful", "Payment of Rp18,000 to Corner Cafe was successful", 1L)
        assertEquals(TxType.EXPENSE, p!!.type)
        assertEquals(18_000L, p.amount)
        assertEquals("Corner Cafe", p.merchant)
    }

    @Test
    fun `english money in is income and your account is not a merchant`() {
        val p = bank.parse("BCA", "Rp 2,500,000.00 has been credited to your account", 1L)
        assertEquals(TxType.INCOME, p!!.type)
        assertEquals(2_500_000L, p.amount)
        assertNull(p.merchant)
    }

    @Test
    fun `english transfer and withdrawal are expenses`() {
        val transfer = bank.parse("BCA", "Transfer to Budi Santoso of Rp 75,000.00 completed", 1L)
        assertEquals(TxType.EXPENSE, transfer!!.type)
        assertEquals("Budi Santoso", transfer.merchant)

        assertEquals(TxType.EXPENSE, bank.parse("BCA", "ATM withdrawal IDR 500,000", 1L)!!.type)
    }

    @Test
    fun `english own transfer goes to review as a transfer`() {
        val p = bank.parse("BCA", "Transfer of Rp 100,000 between accounts", 1L)
        assertTrue(p!!.transferCandidate)
    }

    @Test
    fun `english non transactions are ignored`() {
        assertNull(bank.parse("BCA", "Your verification code is 123456 for the transaction of Rp 50,000", 1L))
        assertNull(bank.parse("BCA", "Payment of Rp 50,000 was unsuccessful", 1L))
        assertNull(bank.parse("BCA", "Payment of Rp 50,000 was cancelled", 1L))
        assertNull(ewallet.parse("GoPay", "Get a 50% discount when you pay, up to Rp 20,000", 1L))
    }

    @Test
    fun `superbank english transfer out is an expense to the title's recipient`() {
        val p = superbank.parse("Transfer to Budi Santoso", "Transfer of Rp.50,000.00 from Savings was successful", 1L)
        assertEquals(TxType.EXPENSE, p!!.type)
        assertEquals(50_000L, p.amount)
        assertEquals("Budi Santoso", p.merchant)
    }

    @Test
    fun `superbank english recipient containing a money in word is still an expense`() {
        val p = superbank.parse("Transfer to Incoming Ltd", "Transfer of Rp.50,000.00 from Savings was successful", 1L)
        assertEquals(TxType.EXPENSE, p!!.type)
    }

    @Test
    fun `superbank english money in is income`() {
        val p = superbank.parse("Money received", "You received Rp.750,000.00 in Savings", 1L)
        assertEquals(TxType.INCOME, p!!.type)
        assertEquals(750_000L, p.amount)
    }

    @Test
    fun `english category keywords are recognised`() {
        val guess = Categorizer.guess(TxType.EXPENSE, null, "Payment of Rp 90,000 to City Pharmacy", emptyMap())
        assertEquals(CategoryGuess.Builtin("health"), guess)
    }

    @Test
    fun `merchant learning key is normalised`() {
        assertEquals("kopi senja", MerchantKey.of("  KOPI-SENJA  "))
    }
}
