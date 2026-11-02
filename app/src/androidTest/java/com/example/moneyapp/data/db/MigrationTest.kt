package com.example.moneyapp.data.db

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

    private companion object {
        const val NAME = "migration-test"
    }
}
