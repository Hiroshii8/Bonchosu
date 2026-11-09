package com.hiroshi.bonchosu.data.db

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Uses its own database file, so running it doesn't touch the app's real money.db. */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java)

    @Test
    fun migrating1To2KeepsWalletsAndTransactions() {
        helper.createDatabase(NAME, 1).apply {
            insert("wallets", SQLiteDatabase.CONFLICT_ABORT, ContentValues().apply {
                put("id", 1L); put("name", "Cash"); put("type", "CASH"); put("initialBalance", 5_000L)
                put("currency", "IDR"); put("excludeFromTotal", 0); put("archived", 0); put("createdAt", 1L)
            })
            insert("transactions", SQLiteDatabase.CONFLICT_ABORT, ContentValues().apply {
                put("id", 7L); put("type", "EXPENSE"); put("amount", 1_250L); put("walletId", 1L); put("occurredAt", 1L)
                put("note", ""); put("source", "MANUAL"); put("status", "CONFIRMED"); put("excludeFromReport", 0); put("createdAt", 1L)
            })
            close()
        }

        val db = helper.runMigrationsAndValidate(NAME, 2, true)

        db.query("SELECT initialBalance FROM wallets WHERE id = 1").use { it.moveToFirst(); assertEquals(5_000L, it.getLong(0)) }
        db.query("SELECT amount FROM transactions WHERE id = 7").use { it.moveToFirst(); assertEquals(1_250L, it.getLong(0)) }
        db.query("SELECT COUNT(*) FROM bills").use { it.moveToFirst(); assertEquals(0, it.getInt(0)) }
        db.close()
    }

    @Test
    fun migrating2To3GivesExistingBudgetsDefaultAlertLevels() {
        helper.createDatabase(NAME, 2).apply {
            insert("budgets", SQLiteDatabase.CONFLICT_ABORT, ContentValues().apply {
                put("id", 3L); put("amount", 2_000_000L); put("period", "MONTHLY")
            })
            close()
        }

        val db = helper.runMigrationsAndValidate(NAME, 3, true)

        db.query("SELECT amount, alertLevels FROM budgets WHERE id = 3").use {
            it.moveToFirst()
            assertEquals(2_000_000L, it.getLong(0))
            assertEquals("50,75,100", it.getString(1))
        }
        db.close()
    }

    @Test
    fun migrating3To4AddsKeywordRulesAndKeepsTransactions() {
        helper.createDatabase(NAME, 3).apply {
            insert("wallets", SQLiteDatabase.CONFLICT_ABORT, ContentValues().apply {
                put("id", 1L); put("name", "Cash"); put("type", "CASH"); put("initialBalance", 0L)
                put("currency", "IDR"); put("excludeFromTotal", 0); put("archived", 0); put("createdAt", 1L)
            })
            insert("transactions", SQLiteDatabase.CONFLICT_ABORT, ContentValues().apply {
                put("id", 9L); put("type", "EXPENSE"); put("amount", 42_000L); put("walletId", 1L); put("occurredAt", 1L)
                put("note", ""); put("source", "IMPORT"); put("status", "PENDING_REVIEW"); put("excludeFromReport", 0); put("createdAt", 1L)
            })
            close()
        }

        val db = helper.runMigrationsAndValidate(NAME, 4, true)

        db.query("SELECT amount FROM transactions WHERE id = 9").use { it.moveToFirst(); assertEquals(42_000L, it.getLong(0)) }
        db.query("SELECT COUNT(*) FROM keyword_rules").use { it.moveToFirst(); assertEquals(0, it.getInt(0)) }
        db.close()
    }

    private companion object {
        const val NAME = "migration-test"
    }
}
