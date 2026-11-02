package com.example.moneyapp.data

import com.example.moneyapp.capture.MerchantKey
import com.example.moneyapp.core.BudgetMath
import com.example.moneyapp.core.BudgetProgress
import com.example.moneyapp.core.BudgetSpec
import com.example.moneyapp.core.DateRange
import com.example.moneyapp.core.ExpensePoint
import com.example.moneyapp.core.Periods
import com.example.moneyapp.core.Recurrence
import com.example.moneyapp.data.db.AppDatabase
import com.example.moneyapp.data.db.BudgetEntity
import com.example.moneyapp.data.db.CategoryEntity
import com.example.moneyapp.data.db.CategoryTotal
import com.example.moneyapp.data.db.MerchantRuleEntity
import com.example.moneyapp.data.db.MonthTotal
import com.example.moneyapp.data.db.RecurringEntity
import com.example.moneyapp.data.db.TransactionEntity
import com.example.moneyapp.data.db.TransactionRow
import com.example.moneyapp.data.db.TxSource
import com.example.moneyapp.data.db.TxStatus
import com.example.moneyapp.data.db.TxType
import com.example.moneyapp.data.db.WalletEntity
import com.example.moneyapp.data.db.WalletWithBalance
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.LocalDate

// Repositories = the service/data-access boundary. ViewModels never touch DAOs directly.

class WalletRepository(private val db: AppDatabase) {
    private val dao = db.walletDao()

    fun observeWithBalance(includeArchived: Boolean = false): Flow<List<WalletWithBalance>> =
        dao.observeWithBalance(includeArchived)

    fun observeActive(): Flow<List<WalletEntity>> = dao.observeActive()
    suspend fun get(id: Long) = dao.get(id)
    suspend fun save(wallet: WalletEntity): Long =
        if (wallet.id == 0L) dao.insert(wallet) else { dao.update(wallet); wallet.id }

    suspend fun delete(wallet: WalletEntity) = dao.delete(wallet)
}

class CategoryRepository(private val db: AppDatabase) {
    private val dao = db.categoryDao()
    fun observeAll(): Flow<List<CategoryEntity>> = dao.observeAll()
    suspend fun get(id: Long) = dao.get(id)
    suspend fun byKey(key: String) = dao.byKey(key)
    suspend fun save(category: CategoryEntity): Long =
        if (category.id == 0L) dao.insert(category) else { dao.update(category); category.id }

    /** Built-in categories are archived (hidden) instead of deleted, so history keeps its labels. */
    suspend fun remove(category: CategoryEntity) =
        if (category.builtinKey != null) dao.update(category.copy(archived = true)) else dao.delete(category)
}

class TransactionRepository(private val db: AppDatabase) {
    private val dao = db.transactionDao()
    private val rules = db.merchantRuleDao()

    fun observe(range: DateRange, walletId: Long? = null, query: String = ""): Flow<List<TransactionRow>> =
        dao.observeConfirmed(range.startMs, range.endMs, walletId, query.trim())

    fun observePending(): Flow<List<TransactionRow>> = dao.observePending()
    fun observePendingCount(): Flow<Int> = dao.observePendingCount()
    suspend fun get(id: Long) = dao.get(id)

    suspend fun save(tx: TransactionEntity): Long {
        require(tx.amount > 0) { "amount must be positive" }
        require(tx.type != TxType.TRANSFER || (tx.toWalletId != null && tx.toWalletId != tx.walletId)) {
            "transfer needs a different destination wallet"
        }
        return if (tx.id == 0L) dao.insert(tx) else { dao.update(tx); tx.id }
    }

    suspend fun delete(id: Long) = dao.delete(id)

    /** Confirm a captured transaction; if a category was set on a merchant, remember it for next time. */
    suspend fun confirm(tx: TransactionEntity) {
        dao.update(tx.copy(status = TxStatus.CONFIRMED))
        learn(tx)
    }

    suspend fun confirmAll(ids: List<Long>) {
        if (ids.isNotEmpty()) dao.confirm(ids)
    }

    suspend fun learn(tx: TransactionEntity) {
        val key = tx.merchant?.let(MerchantKey::of) ?: return
        val cat = tx.categoryId ?: return
        rules.put(MerchantRuleEntity(key, cat))
    }

    suspend fun merchantRules(): Map<String, Long> = rules.all().associate { it.merchantKey to it.categoryId }

    fun observeCategoryTotals(type: TxType, range: DateRange): Flow<List<CategoryTotal>> =
        dao.observeCategoryTotals(type, range.startMs, range.endMs)

    fun observeMonthTotals(range: DateRange): Flow<List<MonthTotal>> = dao.observeMonthTotals(range.startMs, range.endMs)

    fun observeExpenses(range: DateRange): Flow<List<TransactionEntity>> = dao.observeExpenses(range.startMs, range.endMs)
}

class BudgetRepository(private val db: AppDatabase, private val transactions: TransactionRepository) {
    private val dao = db.budgetDao()

    fun observeAll(): Flow<List<BudgetEntity>> = dao.observeAll()
    suspend fun get(id: Long) = dao.get(id)
    suspend fun save(budget: BudgetEntity) = dao.upsert(budget)
    suspend fun delete(id: Long) = dao.delete(id)

    /** Live progress for every budget in its current period. */
    fun observeProgress(today: LocalDate = LocalDate.now()): Flow<List<Pair<BudgetEntity, BudgetProgress>>> {
        val window = BudgetMath.queryRange(today)
        return combine(dao.observeAll(), transactions.observeExpenses(window)) { budgets, expenses ->
            val points = expenses.map { ExpensePoint(it.amount, it.walletId, it.categoryId, it.occurredAt) }
            val specs = budgets.map { BudgetSpec(it.id, it.categoryId, it.walletId, it.amount, it.period) }
            budgets.zip(BudgetMath.progress(specs, points, today))
        }
    }
}

class RecurringRepository(private val db: AppDatabase) {
    private val dao = db.recurringDao()

    fun observeAll(): Flow<List<RecurringEntity>> = dao.observeAll()
    suspend fun get(id: Long) = dao.get(id)
    suspend fun save(rule: RecurringEntity) = dao.upsert(rule)
    suspend fun delete(id: Long) = dao.delete(id)

    /**
     * Creates the transactions that are due (including missed ones) and moves each rule forward.
     * Called daily by RecurringWorker and when the app opens. Returns how many were created.
     */
    suspend fun runDue(today: LocalDate = LocalDate.now()): Int {
        var created = 0
        val zone = Periods.zone()
        for (rule in dao.due(Periods.endOfDay(today, zone))) {
            val start = Periods.toDate(rule.startDate, zone)
            val end = rule.endDate?.let { Periods.toDate(it, zone) }
            var current = rule
            for (date in Recurrence.dueDates(Periods.toDate(rule.nextRunAt, zone), start, rule.frequency, rule.everyN, today, end)) {
                val nextDate = Recurrence.next(date, start, rule.frequency, rule.everyN)
                val stillActive = end == null || !nextDate.isAfter(end)
                current = current.copy(nextRunAt = Periods.startOfDay(nextDate, zone), active = stillActive)
                val tx = TransactionEntity(
                    type = rule.type,
                    amount = rule.amount,
                    walletId = rule.walletId,
                    toWalletId = rule.toWalletId,
                    categoryId = rule.categoryId,
                    // Noon, so the date never shifts across time zones.
                    occurredAt = Periods.startOfDay(date, zone) + 12 * 60 * 60 * 1000L,
                    note = rule.note,
                    source = TxSource.RECURRING,
                    recurringId = rule.id,
                )
                db.atomicDao().runRecurring(tx, current)
                created++
            }
            // Past its end date with nothing left to create: switch it off.
            if (current.active && end != null && Periods.toDate(current.nextRunAt, zone).isAfter(end)) {
                dao.upsert(current.copy(active = false))
            }
        }
        return created
    }
}
