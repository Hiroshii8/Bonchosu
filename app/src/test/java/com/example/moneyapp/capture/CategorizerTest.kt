package com.example.moneyapp.capture

import com.example.moneyapp.data.db.TxType
import org.junit.Assert.assertEquals
import org.junit.Test

class CategorizerTest {

    @Test
    fun `learned rule beats keywords`() {
        val g = Categorizer.guess(TxType.EXPENSE, "kopi senja", "Transaksi di KOPI SENJA", mapOf("kopi senja" to 42L))
        assertEquals(CategoryGuess.Learned(42L), g)
    }

    @Test
    fun `grabfood is food not transport`() {
        assertEquals(CategoryGuess.Builtin("food"), Categorizer.guess(TxType.EXPENSE, null, "Pembayaran GrabFood Rp 50.000", emptyMap()))
        assertEquals(CategoryGuess.Builtin("transport"), Categorizer.guess(TxType.EXPENSE, null, "Pembayaran Grab Rp 25.000", emptyMap()))
    }

    @Test
    fun `minimarket is groceries`() {
        assertEquals(CategoryGuess.Builtin("groceries"), Categorizer.guess(TxType.EXPENSE, null, "Debit di INDOMARET SUDIRMAN", emptyMap()))
    }

    @Test
    fun `salary income`() {
        assertEquals(CategoryGuess.Builtin("salary"), Categorizer.guess(TxType.INCOME, null, "Transfer masuk GAJI OKTOBER", emptyMap()))
    }

    @Test
    fun `unknown merchant is not confident`() {
        val g = Categorizer.guess(TxType.EXPENSE, "toko sinar jaya", "Debit di TOKO SINAR JAYA", emptyMap())
        assertEquals(CategoryGuess.Unknown, g)
        assertEquals(false, g.confident)
    }

    @Test
    fun `merchant names are tidied`() {
        assertEquals("Kopi Senja", MerchantKey.pretty("KOPI  SENJA"))
        assertEquals("McDonald's", MerchantKey.pretty("McDonald's"))
    }
}
