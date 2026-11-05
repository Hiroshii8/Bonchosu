package com.hiroshi.moneyapp.statement

import androidx.room.withTransaction
import com.hiroshi.moneyapp.capture.CategoryGuess
import com.hiroshi.moneyapp.capture.Categorizer
import com.hiroshi.moneyapp.capture.MerchantKey
import com.hiroshi.moneyapp.core.Periods
import com.hiroshi.moneyapp.data.TransactionRepository
import com.hiroshi.moneyapp.data.db.AppDatabase
import com.hiroshi.moneyapp.data.db.TransactionEntity
import com.hiroshi.moneyapp.data.db.TxSource
import com.hiroshi.moneyapp.data.db.TxStatus

/** Saves statement rows as PENDING_REVIEW, so they don't touch balances or budgets until confirmed. */
class StatementImporter(
    private val db: AppDatabase,
    private val transactions: TransactionRepository,
) {
    /**
     * Indexes of rows already recorded in this wallet: same type and amount on the same day.
     * Counted one for one, so two equal payments in a day survive when only one is already recorded.
     */
    suspend fun findDuplicates(walletId: Long, rows: List<StatementRow>): Set<Int> {
        val dao = db.transactionDao()
        val remaining = mutableMapOf<Triple<Any, Any, Long>, Int>()
        return rows.indices.filter { i ->
            val row = rows[i]
            val key = Triple<Any, Any, Long>(row.date, row.type, row.amount)
            val left = remaining.getOrPut(key) {
                dao.countSimilar(walletId, row.type, row.amount, Periods.startOfDay(row.date), Periods.endOfDay(row.date))
            }
            if (left > 0) remaining[key] = left - 1
            left > 0
        }.toSet()
    }

    suspend fun import(walletId: Long, rows: List<StatementRow>) {
        val learned = transactions.merchantRules()
        db.withTransaction {
            rows.forEach { row ->
                val merchant = row.description.takeIf { it.isNotBlank() }?.let(MerchantKey::pretty)
                val guess = Categorizer.guess(row.type, merchant?.let(MerchantKey::of), row.rawText, learned)
                val categoryId = when (guess) {
                    is CategoryGuess.Learned -> guess.categoryId
                    is CategoryGuess.Builtin -> db.categoryDao().byKey(guess.key)?.takeIf { !it.archived }?.id
                    CategoryGuess.Unknown -> null
                }
                db.transactionDao().insert(
                    TransactionEntity(
                        type = row.type,
                        amount = row.amount,
                        walletId = walletId,
                        categoryId = categoryId,
                        occurredAt = Periods.startOfDay(row.date) + NOON_MS, // statements have no time of day
                        merchant = merchant,
                        source = TxSource.IMPORT,
                        status = TxStatus.PENDING_REVIEW,
                        rawText = row.rawText,
                    )
                )
            }
        }
    }

    private companion object {
        const val NOON_MS = 12 * 60 * 60 * 1000L
    }
}
