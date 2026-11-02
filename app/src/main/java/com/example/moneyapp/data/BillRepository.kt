package com.example.moneyapp.data

import androidx.room.withTransaction
import com.example.moneyapp.core.BillSplit
import com.example.moneyapp.core.Periods
import com.example.moneyapp.data.db.AppDatabase
import com.example.moneyapp.data.db.BillChargeEntity
import com.example.moneyapp.data.db.BillEntity
import com.example.moneyapp.data.db.BillItemEntity
import com.example.moneyapp.data.db.BillItemShareEntity
import com.example.moneyapp.data.db.BillPersonEntity
import com.example.moneyapp.data.db.TransactionEntity
import com.example.moneyapp.data.db.TxType
import java.time.LocalDate

/** A finished draft. People are referred to by [NewPerson.key] until they have database ids. */
data class NewBill(
    val merchant: String,
    val date: LocalDate,
    val walletId: Long,
    val categoryId: Long?,
    val people: List<NewPerson>,
    val items: List<NewItem>,
    val charges: List<NewCharge>,
)

data class NewPerson(val key: Long, val name: String, val isPayer: Boolean)

/** [mine] = the people whose portion of this order is flagged as mine. */
data class NewItem(val name: String, val quantity: Int, val lineTotal: Long, val units: Map<Long, Int>, val mine: Set<Long>)
data class NewCharge(val name: String, val amount: Long, val equally: Boolean)

/** What the summary shows and the save writes. Pure, so the two can't disagree. */
data class BillTotals(val shares: Map<Long, BillSplit.Share>, val mineByPerson: Map<Long, Long>) {
    val total get() = shares.values.sumOf { it.total }
    val myShare get() = mineByPerson.values.sum()

    companion object {
        fun of(people: List<Long>, items: List<NewItem>, charges: List<NewCharge>): BillTotals {
            val shares = BillSplit.split(
                people,
                items.mapIndexed { i, item -> BillSplit.Item(i.toLong(), item.lineTotal, item.units) },
                charges.map { BillSplit.Charge(it.amount, it.equally) },
            )
            val mine = people.associateWith { person ->
                BillSplit.orderCosts(shares.getValue(person)).entries.sumOf { (index, cost) ->
                    if (person in items[index.toInt()].mine) cost else 0L
                }
            }
            return BillTotals(shares, mine)
        }
    }
}

class BillRepository(private val db: AppDatabase) {
    private val dao = db.billDao()
    private val txDao = db.transactionDao()

    fun observeAll() = dao.observeAll()
    fun observe(id: Long) = dao.observe(id)
    fun observePeople(billId: Long) = dao.observePeople(billId)
    fun observeItems(billId: Long) = dao.observeItems(billId)
    fun observeShares(billId: Long) = dao.observeShares(billId)
    fun observeCharges(billId: Long) = dao.observeCharges(billId)

    /** True when this wallet already has an expense of exactly this size that day (e.g. a captured notification). */
    suspend fun hasSimilarExpense(walletId: Long, amount: Long, date: LocalDate): Boolean =
        txDao.countSimilar(walletId, TxType.EXPENSE, amount, Periods.startOfDay(date), Periods.endOfDay(date)) > 0

    /**
     * Budgets should count only the orders flagged as mine, while the wallet should show what really left it.
     * If I paid: my orders are a normal expense, and the rest is an expense hidden from reports until repaid.
     * If someone else paid: nothing moves until I pay them back ([setSettled]).
     */
    suspend fun save(bill: NewBill): Long = db.withTransaction {
        val totals = BillTotals.of(bill.people.map { it.key }, bill.items, bill.charges)
        val paidByMe = bill.people.none { it.isPayer }
        val occurredAt = Periods.startOfDay(bill.date) + NOON_MS

        val billId = dao.insert(
            BillEntity(
                merchant = bill.merchant, occurredAt = occurredAt, total = totals.total, paidByMe = paidByMe,
                myShare = totals.myShare, walletId = bill.walletId, categoryId = bill.categoryId,
            )
        )
        val personIds = bill.people.associate { p ->
            val share = totals.shares.getValue(p.key).total
            val owed = when {
                paidByMe -> share - totals.mineByPerson.getValue(p.key)
                p.isPayer -> totals.myShare
                else -> 0L
            }
            p.key to dao.insert(BillPersonEntity(billId = billId, name = p.name, isPayer = p.isPayer, share = share, owed = owed, settled = owed <= 0))
        }
        bill.items.forEachIndexed { i, item ->
            val itemId = dao.insert(BillItemEntity(billId = billId, name = item.name, quantity = item.quantity, lineTotal = item.lineTotal, position = i))
            item.units.filterValues { it > 0 }.forEach { (key, units) ->
                dao.insert(BillItemShareEntity(itemId, personIds.getValue(key), units, mine = key in item.mine))
            }
        }
        bill.charges.forEachIndexed { i, charge ->
            dao.insert(BillChargeEntity(billId = billId, name = charge.name, amount = charge.amount, equally = charge.equally, position = i))
        }

        if (paidByMe) {
            val others = totals.total - totals.myShare
            val myTx = if (totals.myShare > 0) txDao.insert(expense(bill, totals.myShare, occurredAt, bill.categoryId, hidden = false)) else null
            val othersTx = if (others > 0) txDao.insert(expense(bill, others, occurredAt, null, hidden = true)) else null
            dao.update(dao.get(billId)!!.copy(myShareTxId = myTx, othersShareTxId = othersTx))
        }
        billId
    }

    private fun expense(bill: NewBill, amount: Long, at: Long, categoryId: Long?, hidden: Boolean) = TransactionEntity(
        type = TxType.EXPENSE, amount = amount, walletId = bill.walletId, categoryId = categoryId,
        occurredAt = at, merchant = bill.merchant, excludeFromReport = hidden,
    )

    /** Marks a person as settled, or undoes it. Writes or removes the money movement that goes with it. */
    suspend fun setSettled(personId: Long, settled: Boolean) = db.withTransaction {
        val person = dao.person(personId) ?: return@withTransaction
        val bill = dao.get(person.billId) ?: return@withTransaction
        if (person.settled == settled || person.owed <= 0) return@withTransaction
        if (settled) {
            val walletId = bill.walletId ?: return@withTransaction
            val now = System.currentTimeMillis()
            val tx = if (bill.paidByMe) {
                // Their money coming back isn't income: it only undoes the hidden expense.
                TransactionEntity(type = TxType.INCOME, amount = person.owed, walletId = walletId, occurredAt = now, merchant = person.name, excludeFromReport = true)
            } else {
                TransactionEntity(type = TxType.EXPENSE, amount = person.owed, walletId = walletId, categoryId = bill.categoryId, occurredAt = now, merchant = bill.merchant)
            }
            dao.update(person.copy(settled = true, settledTxId = txDao.insert(tx)))
        } else {
            person.settledTxId?.let { txDao.delete(it) }
            dao.update(person.copy(settled = false, settledTxId = null))
        }
    }

    /** Removes the bill and every transaction it wrote, so balances go back to how they were. */
    suspend fun delete(billId: Long) = db.withTransaction {
        val bill = dao.get(billId) ?: return@withTransaction
        (dao.settlementTxIds(billId) + listOfNotNull(bill.myShareTxId, bill.othersShareTxId)).forEach { txDao.delete(it) }
        dao.delete(billId)
    }

    private companion object {
        const val NOON_MS = 12 * 60 * 60 * 1000L
    }
}
