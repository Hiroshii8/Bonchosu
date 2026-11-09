package com.hiroshi.bonchosu.capture

import com.hiroshi.bonchosu.data.db.TxType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KeywordRulesTest {

    private val kos = KeywordRule("kos", categoryId = 50, type = TxType.EXPENSE)
    private val kopi = KeywordRule("kopi", categoryId = 51, type = TxType.EXPENSE)
    private val kopiKenangan = KeywordRule("kopi kenangan", categoryId = 52, type = TxType.EXPENSE)
    private val freelance = KeywordRule("freelance", categoryId = 60, type = TxType.INCOME)
    private val rules = listOf(kos, kopi, kopiKenangan, freelance)

    @Test
    fun `typed words are stored the same way`() {
        assertEquals("kopi kenangan", Keywords.normalize("  Kopi   KENANGAN "))
    }

    @Test
    fun `whole words only`() {
        assertTrue(Keywords.matches("kopi", "pembayaran di kopi kenangan"))
        assertTrue(Keywords.matches("kos", "bayar kos-kosan"))
        assertFalse(Keywords.matches("kopi", "beli kopiah"))
        assertFalse(Keywords.matches("kos", "kosmetik"))
    }

    @Test
    fun `your word beats the built in list`() {
        // "kopi" is a built-in food word; your rule sends it to your own category instead.
        val g = Categorizer.guess(TxType.EXPENSE, null, "Pembayaran QRIS di Kopi Senja", emptyMap(), listOf(kopi))
        assertEquals(CategoryGuess.Custom(51), g)
    }

    @Test
    fun `the longer matching word wins`() {
        val g = Categorizer.guess(TxType.EXPENSE, null, "Pembayaran di Kopi Kenangan", emptyMap(), rules)
        assertEquals(CategoryGuess.Custom(52), g)
    }

    @Test
    fun `a correction for this exact merchant still comes first`() {
        val g = Categorizer.guess(TxType.EXPENSE, "kopi kenangan", "Kopi Kenangan", mapOf("kopi kenangan" to 7L), rules)
        assertEquals(CategoryGuess.Learned(7), g)
    }

    @Test
    fun `rules only match their own side`() {
        assertEquals(CategoryGuess.Custom(60), Categorizer.guess(TxType.INCOME, null, "Transfer masuk freelance", emptyMap(), rules))
        assertEquals(CategoryGuess.Unknown, Categorizer.guess(TxType.EXPENSE, null, "freelance", emptyMap(), rules))
    }

    @Test
    fun `hints resolve built in keys and look at the merchant too`() {
        val hints = CategoryHints(emptyMap(), listOf(kos), builtinIds = mapOf("food" to 3L))
        assertEquals(50L to true, hints.guess(TxType.EXPENSE, "Kos Melati", "Transfer Rp1.500.000"))
        assertEquals(3L to true, hints.guess(TxType.EXPENSE, "Bakso Pak Kumis", ""))
        val hidden = CategoryHints(emptyMap(), emptyList(), builtinIds = emptyMap())
        assertEquals(null to false, hidden.guess(TxType.EXPENSE, "Bakso Pak Kumis", ""))
        assertNull(hidden.guess(TxType.EXPENSE, null, "Transfer").first)
    }
}
