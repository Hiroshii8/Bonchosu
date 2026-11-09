package com.hiroshi.bonchosu.statement

import androidx.room.withTransaction
import com.hiroshi.bonchosu.capture.MerchantKey
import com.hiroshi.bonchosu.core.Periods
import com.hiroshi.bonchosu.data.TransactionRepository
import com.hiroshi.bonchosu.data.db.AppDatabase
import com.hiroshi.bonchosu.data.db.TransactionEntity
import com.hiroshi.bonchosu.data.db.TxSource
import com.hiroshi.bonchosu.data.db.TxStatus

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

    /** [source] says where the rows came from: IMPORT for statements, RECEIPT for scanned receipts. */
    suspend fun import(walletId: Long, rows: List<StatementRow>, source: TxSource = TxSource.IMPORT) {
        val hints = transactions.categoryHints()
        db.withTransaction {
            rows.forEach { row ->
                val merchant = row.description.takeIf { it.isNotBlank() }?.let(MerchantKey::pretty)
                val (categoryId, _) = hints.guess(row.type, merchant, row.rawText)
                db.transactionDao().insert(
                    TransactionEntity(
                        type = row.type,
                        amount = row.amount,
                        walletId = walletId,
                        categoryId = categoryId,
                        occurredAt = Periods.startOfDay(row.date) + NOON_MS, // statements have no time of day
                        merchant = merchant,
                        source = source,
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
