package com.hiroshi.bonchosu.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface WalletDao {

    /**
     * Current balance = initial balance + confirmed income − confirmed expense
     * − transfers out + transfers in. Pending (unreviewed) captures don't count yet.
     */
    @Query(
        """
        SELECT w.*,
          w.initialBalance
          + COALESCE((SELECT SUM(CASE WHEN t.type = 'INCOME' THEN t.amount ELSE -t.amount END)
                      FROM transactions t WHERE t.walletId = w.id AND t.status = 'CONFIRMED'), 0)
          + COALESCE((SELECT SUM(t.amount) FROM transactions t
                      WHERE t.toWalletId = w.id AND t.type = 'TRANSFER' AND t.status = 'CONFIRMED'), 0)
          AS balance
        FROM wallets w
        WHERE (:includeArchived OR w.archived = 0)
        ORDER BY w.archived, w.id
        """
    )
    fun observeWithBalance(includeArchived: Boolean): Flow<List<WalletWithBalance>>

    @Query("SELECT * FROM wallets WHERE archived = 0 ORDER BY id")
    fun observeActive(): Flow<List<WalletEntity>>

    @Query("SELECT * FROM wallets WHERE id = :id")
    suspend fun get(id: Long): WalletEntity?

    @Query("SELECT * FROM wallets WHERE captureSource = :source AND archived = 0 ORDER BY id LIMIT 1")
    suspend fun findByCaptureSource(source: String): WalletEntity?

    @Query("SELECT * FROM wallets WHERE archived = 0 ORDER BY id LIMIT 1")
    suspend fun firstActive(): WalletEntity?

    @Insert
    suspend fun insert(wallet: WalletEntity): Long

    @Update
    suspend fun update(wallet: WalletEntity)

    /** Cascades to the wallet's transactions, budgets and recurring rules. */
    @Delete
    suspend fun delete(wallet: WalletEntity)
}

@Dao
interface CategoryDao {
    /** Includes archived ones (history still needs their names); pickers filter them out. */
    @Query("SELECT * FROM categories ORDER BY type, id")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun get(id: Long): CategoryEntity?

    @Query("SELECT * FROM categories WHERE builtinKey = :key LIMIT 1")
    suspend fun byKey(key: String): CategoryEntity?

    @Insert
    suspend fun insert(category: CategoryEntity): Long

    @Insert
    suspend fun insertAll(categories: List<CategoryEntity>)

    @Update
    suspend fun update(category: CategoryEntity)

    @Delete
    suspend fun delete(category: CategoryEntity)
}

@Dao
interface TransactionDao {

    @Query(
        """
        SELECT t.id, t.type, t.amount, t.occurredAt, t.note, t.merchant, t.source, t.status,
               t.excludeFromReport, t.walletId, w.name AS walletName, t.toWalletId, tw.name AS toWalletName,
               t.categoryId, c.name AS categoryName, c.builtinKey AS categoryKey, c.colorIndex AS categoryColor
        FROM transactions t
        JOIN wallets w ON w.id = t.walletId
        LEFT JOIN wallets tw ON tw.id = t.toWalletId
        LEFT JOIN categories c ON c.id = t.categoryId
        WHERE t.status = 'CONFIRMED'
          AND t.occurredAt BETWEEN :fromMs AND :toMs
          AND (:walletId IS NULL OR t.walletId = :walletId OR t.toWalletId = :walletId)
          AND (:query = '' OR t.note LIKE '%' || :query || '%' OR t.merchant LIKE '%' || :query || '%'
               OR c.name LIKE '%' || :query || '%'
               OR (REPLACE(REPLACE(:query, '.', ''), ',', '') GLOB '[0-9]*'
                   AND CAST(t.amount AS TEXT) LIKE '%' || REPLACE(REPLACE(:query, '.', ''), ',', '') || '%'))
        ORDER BY t.occurredAt DESC, t.id DESC
        """
    )
    fun observeConfirmed(fromMs: Long, toMs: Long, walletId: Long?, query: String): Flow<List<TransactionRow>>

    @Query(
        """
        SELECT t.id, t.type, t.amount, t.occurredAt, t.note, t.merchant, t.source, t.status,
               t.excludeFromReport, t.walletId, w.name AS walletName, t.toWalletId, tw.name AS toWalletName,
               t.categoryId, c.name AS categoryName, c.builtinKey AS categoryKey, c.colorIndex AS categoryColor
        FROM transactions t
        JOIN wallets w ON w.id = t.walletId
        LEFT JOIN wallets tw ON tw.id = t.toWalletId
        LEFT JOIN categories c ON c.id = t.categoryId
        WHERE t.status = 'PENDING_REVIEW'
        ORDER BY t.occurredAt DESC, t.id DESC
        """
    )
    fun observePending(): Flow<List<TransactionRow>>

    @Query("SELECT COUNT(*) FROM transactions WHERE status = 'PENDING_REVIEW'")
    fun observePendingCount(): Flow<Int>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun get(id: Long): TransactionEntity?

    @Insert
    suspend fun insert(tx: TransactionEntity): Long

    @Update
    suspend fun update(tx: TransactionEntity)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE transactions SET status = 'CONFIRMED' WHERE id IN (:ids)")
    suspend fun confirm(ids: List<Long>)

    /** Duplicate guard for captures: same wallet, type and amount within a time window. */
    @Query(
        """
        SELECT COUNT(*) FROM transactions
        WHERE walletId = :walletId AND type = :type AND amount = :amount
          AND occurredAt BETWEEN :fromMs AND :toMs
        """
    )
    suspend fun countSimilar(walletId: Long, type: TxType, amount: Long, fromMs: Long, toMs: Long): Int

    // ---------- Reports ----------

    @Query(
        """
        SELECT t.categoryId AS categoryId, c.name AS name, c.builtinKey AS builtinKey,
               c.colorIndex AS colorIndex, SUM(t.amount) AS total
        FROM transactions t
        JOIN wallets w ON w.id = t.walletId
        LEFT JOIN categories c ON c.id = t.categoryId
        WHERE t.type = :type AND t.status = 'CONFIRMED' AND t.excludeFromReport = 0
          AND w.excludeFromTotal = 0 AND t.occurredAt BETWEEN :fromMs AND :toMs
        GROUP BY t.categoryId
        ORDER BY total DESC
        """
    )
    fun observeCategoryTotals(type: TxType, fromMs: Long, toMs: Long): Flow<List<CategoryTotal>>

    @Query(
        """
        SELECT strftime('%Y-%m', t.occurredAt / 1000, 'unixepoch', 'localtime') AS month,
               SUM(CASE WHEN t.type = 'INCOME' THEN t.amount ELSE 0 END) AS income,
               SUM(CASE WHEN t.type = 'EXPENSE' THEN t.amount ELSE 0 END) AS expense
        FROM transactions t
        JOIN wallets w ON w.id = t.walletId
        WHERE t.status = 'CONFIRMED' AND t.excludeFromReport = 0 AND w.excludeFromTotal = 0
          AND t.type != 'TRANSFER' AND t.occurredAt BETWEEN :fromMs AND :toMs
        GROUP BY month
        ORDER BY month
        """
    )
    fun observeMonthTotals(fromMs: Long, toMs: Long): Flow<List<MonthTotal>>

    /** Expense rows used for budget progress (small: one period's worth). */
    @Query(
        """
        SELECT t.* FROM transactions t
        WHERE t.type = 'EXPENSE' AND t.status = 'CONFIRMED' AND t.excludeFromReport = 0
          AND t.occurredAt BETWEEN :fromMs AND :toMs
        """
    )
    fun observeExpenses(fromMs: Long, toMs: Long): Flow<List<TransactionEntity>>
}

@Dao
interface BudgetDao {
    @Query("SELECT * FROM budgets ORDER BY id")
    fun observeAll(): Flow<List<BudgetEntity>>

    @Query("SELECT * FROM budgets WHERE id = :id")
    suspend fun get(id: Long): BudgetEntity?

    @Upsert
    suspend fun upsert(budget: BudgetEntity): Long

    @Query("DELETE FROM budgets WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface RecurringDao {
    @Query("SELECT * FROM recurring ORDER BY active DESC, nextRunAt")
    fun observeAll(): Flow<List<RecurringEntity>>

    @Query("SELECT * FROM recurring WHERE id = :id")
    suspend fun get(id: Long): RecurringEntity?

    @Query("SELECT * FROM recurring WHERE active = 1 AND nextRunAt <= :nowMs")
    suspend fun due(nowMs: Long): List<RecurringEntity>

    @Upsert
    suspend fun upsert(rule: RecurringEntity): Long

    @Query("DELETE FROM recurring WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface MerchantRuleDao {
    @Query("SELECT * FROM merchant_rules")
    suspend fun all(): List<MerchantRuleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(rule: MerchantRuleEntity)
}

@Dao
interface KeywordRuleDao {
    /** Rules whose category is still in use, longest words first (the more specific word wins). */
    @Query(
        """
        SELECT k.id, k.keyword, k.categoryId, c.type AS categoryType, c.name AS categoryName,
               c.builtinKey AS categoryKey, c.colorIndex AS categoryColor
        FROM keyword_rules k JOIN categories c ON c.id = k.categoryId
        WHERE c.archived = 0
        ORDER BY LENGTH(k.keyword) DESC, k.keyword
        """
    )
    fun observeActive(): Flow<List<KeywordRuleRow>>

    @Query(
        """
        SELECT k.id, k.keyword, k.categoryId, c.type AS categoryType, c.name AS categoryName,
               c.builtinKey AS categoryKey, c.colorIndex AS categoryColor
        FROM keyword_rules k JOIN categories c ON c.id = k.categoryId
        WHERE c.archived = 0
        ORDER BY LENGTH(k.keyword) DESC, k.keyword
        """
    )
    suspend fun active(): List<KeywordRuleRow>

    /** Replaces a rule with the same word, so a word always points at one category. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(rule: KeywordRuleEntity): Long

    @Query("DELETE FROM keyword_rules WHERE id = :id")
    suspend fun delete(id: Long)

    /** Review items still without a category: a new rule may be able to fill them in. */
    @Query("SELECT * FROM transactions WHERE status = 'PENDING_REVIEW' AND categoryId IS NULL AND type != 'TRANSFER'")
    suspend fun uncategorizedPending(): List<TransactionEntity>

    @Query("UPDATE transactions SET categoryId = :categoryId WHERE id = :id AND categoryId IS NULL")
    suspend fun setCategory(id: Long, categoryId: Long)
}

/** One bill in the list, with what's still open: owed to me if [BillEntity.paidByMe], else what I owe. */
data class BillRow(
    @Embedded val bill: BillEntity,
    val outstanding: Long,
)

@Dao
interface BillDao {
    @Query(
        """
        SELECT b.*, (SELECT COALESCE(SUM(p.owed), 0) FROM bill_people p WHERE p.billId = b.id AND p.settled = 0) AS outstanding
        FROM bills b ORDER BY b.occurredAt DESC, b.id DESC
        """
    )
    fun observeAll(): Flow<List<BillRow>>

    @Query("SELECT * FROM bills WHERE id = :id")
    fun observe(id: Long): Flow<BillEntity?>

    @Query("SELECT * FROM bill_people WHERE billId = :billId ORDER BY id")
    fun observePeople(billId: Long): Flow<List<BillPersonEntity>>

    @Query("SELECT * FROM bill_items WHERE billId = :billId ORDER BY position")
    fun observeItems(billId: Long): Flow<List<BillItemEntity>>

    @Query("SELECT s.* FROM bill_item_shares s JOIN bill_items i ON i.id = s.itemId WHERE i.billId = :billId")
    fun observeShares(billId: Long): Flow<List<BillItemShareEntity>>

    @Query("SELECT * FROM bill_charges WHERE billId = :billId ORDER BY position")
    fun observeCharges(billId: Long): Flow<List<BillChargeEntity>>

    @Query("SELECT * FROM bills WHERE id = :id")
    suspend fun get(id: Long): BillEntity?

    @Query("SELECT * FROM bill_people WHERE id = :id")
    suspend fun person(id: Long): BillPersonEntity?

    @Query("SELECT settledTxId FROM bill_people WHERE billId = :billId AND settledTxId IS NOT NULL")
    suspend fun settlementTxIds(billId: Long): List<Long>

    @Insert suspend fun insert(bill: BillEntity): Long
    @Insert suspend fun insert(person: BillPersonEntity): Long
    @Insert suspend fun insert(item: BillItemEntity): Long
    @Insert suspend fun insert(share: BillItemShareEntity)
    @Insert suspend fun insert(charge: BillChargeEntity)
    @Update suspend fun update(bill: BillEntity)
    @Update suspend fun update(person: BillPersonEntity)

    @Query("DELETE FROM bills WHERE id = :id")
    suspend fun delete(id: Long)
}

/** Multi-table writes that must succeed or fail together. */
@Dao
abstract class AtomicDao {
    @Insert
    abstract suspend fun insertTx(tx: TransactionEntity): Long

    @Upsert
    abstract suspend fun upsertRule(rule: RecurringEntity): Long

    /** Materialize one recurring occurrence and move the rule forward, atomically. */
    @Transaction
    open suspend fun runRecurring(tx: TransactionEntity, advanced: RecurringEntity) {
        insertTx(tx)
        upsertRule(advanced)
    }
}
