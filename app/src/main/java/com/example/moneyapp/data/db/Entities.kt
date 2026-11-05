package com.hiroshi.moneyapp.data.db

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "bills",
    foreignKeys = [
        ForeignKey(entity = WalletEntity::class, parentColumns = ["id"], childColumns = ["walletId"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [Index("walletId"), Index("occurredAt")],
)
data class BillEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val merchant: String,
    val occurredAt: Long,
    /** Items plus charges. */
    val total: Long,
    val paidByMe: Boolean,
    /** Cost of the orders flagged as mine, fees included. This is all that counts as my spending. */
    val myShare: Long,
    val walletId: Long?,
    val categoryId: Long?,
    /** Transactions written when the bill was saved; deleted along with the bill. Null when nothing was written. */
    val myShareTxId: Long? = null,
    val othersShareTxId: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "bill_people",
    foreignKeys = [ForeignKey(entity = BillEntity::class, parentColumns = ["id"], childColumns = ["billId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("billId")],
)
data class BillPersonEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val billId: Long,
    val name: String,
    /** Paid the restaurant. No payer on a bill means I paid. */
    val isPayer: Boolean,
    /** This person's part of the whole bill. */
    val share: Long,
    /** Money to settle with me: what they owe me if I paid, or what I owe them if they paid. */
    val owed: Long,
    val settled: Boolean,
    /** The transaction that recorded the settlement, removed again if it's undone. */
    val settledTxId: Long? = null,
)

@Entity(
    tableName = "bill_items",
    foreignKeys = [ForeignKey(entity = BillEntity::class, parentColumns = ["id"], childColumns = ["billId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("billId")],
)
data class BillItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val billId: Long,
    val name: String,
    val quantity: Int,
    val lineTotal: Long,
    val position: Int,
)

@Entity(
    tableName = "bill_item_shares",
    primaryKeys = ["itemId", "personId"],
    foreignKeys = [
        ForeignKey(entity = BillItemEntity::class, parentColumns = ["id"], childColumns = ["itemId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = BillPersonEntity::class, parentColumns = ["id"], childColumns = ["personId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("personId")],
)
data class BillItemShareEntity(
    val itemId: Long,
    val personId: Long,
    val units: Int,
    /** Flagged as my order: its cost counts towards my wallet and budgets. */
    val mine: Boolean,
)

@Entity(
    tableName = "bill_charges",
    foreignKeys = [ForeignKey(entity = BillEntity::class, parentColumns = ["id"], childColumns = ["billId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("billId")],
)
data class BillChargeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val billId: Long,
    val name: String,
    /** Negative for discounts. */
    val amount: Long,
    val equally: Boolean,
    val position: Int,
)

@Entity(tableName = "wallets")
data class WalletEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: WalletType,
    /** Balance when the wallet was added; the current balance is computed from transactions. */
    val initialBalance: Long = 0,
    val currency: String = "IDR",
    val excludeFromTotal: Boolean = false,
    val archived: Boolean = false,
    /** Auto-capture source this wallet receives, e.g. "BCA". Null = manual only. */
    val captureSource: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** User-visible name. For built-in categories the UI shows the translated name instead. */
    val name: String,
    val type: CategoryType,
    /** Stable id of a built-in category ("food", "salary"…), null for user-made ones. */
    val builtinKey: String? = null,
    /** Index into the category palette (ui/theme/Palette.kt). */
    val colorIndex: Int = 0,
    val archived: Boolean = false,
)

@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(entity = WalletEntity::class, parentColumns = ["id"], childColumns = ["walletId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = WalletEntity::class, parentColumns = ["id"], childColumns = ["toWalletId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = CategoryEntity::class, parentColumns = ["id"], childColumns = ["categoryId"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [Index("walletId"), Index("toWalletId"), Index("categoryId"), Index("occurredAt"), Index("status")],
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: TxType,
    /** Always positive; [type] gives the direction. */
    val amount: Long,
    val walletId: Long,
    /** Destination wallet, only for TRANSFER. */
    val toWalletId: Long? = null,
    /** Null for transfers, or when a category was deleted / not yet chosen. */
    val categoryId: Long? = null,
    val occurredAt: Long,
    val note: String = "",
    val merchant: String? = null,
    val source: TxSource = TxSource.MANUAL,
    val status: TxStatus = TxStatus.CONFIRMED,
    /** Money Lover's "exclude from report": still moves the balance, hidden from stats and budgets. */
    val excludeFromReport: Boolean = false,
    /** Original notification text, kept so you can debug or re-parse later. */
    val rawText: String? = null,
    val recurringId: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "budgets",
    foreignKeys = [
        ForeignKey(entity = CategoryEntity::class, parentColumns = ["id"], childColumns = ["categoryId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = WalletEntity::class, parentColumns = ["id"], childColumns = ["walletId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("categoryId"), Index("walletId")],
)
data class BudgetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Null = all expenses. */
    val categoryId: Long? = null,
    /** Null = all wallets. */
    val walletId: Long? = null,
    val amount: Long,
    val period: BudgetPeriod = BudgetPeriod.MONTHLY,
    /** Percentages that trigger a notification, e.g. "50,75,100". Empty = no alerts for this budget. */
    @ColumnInfo(defaultValue = "50,75,100") val alertLevels: String = "50,75,100",
)

@Entity(
    tableName = "recurring",
    foreignKeys = [
        ForeignKey(entity = WalletEntity::class, parentColumns = ["id"], childColumns = ["walletId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = WalletEntity::class, parentColumns = ["id"], childColumns = ["toWalletId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = CategoryEntity::class, parentColumns = ["id"], childColumns = ["categoryId"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [Index("walletId"), Index("toWalletId"), Index("categoryId"), Index("nextRunAt")],
)
data class RecurringEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: TxType,
    val amount: Long,
    val walletId: Long,
    val toWalletId: Long? = null,
    val categoryId: Long? = null,
    val note: String = "",
    val frequency: Frequency,
    /** Every N periods, e.g. every 2 weeks. */
    val everyN: Int = 1,
    /** Date (epoch ms, start of day) of the first occurrence; its day-of-month is kept for monthly rules. */
    val startDate: Long,
    val nextRunAt: Long,
    val endDate: Long? = null,
    val active: Boolean = true,
)

/** Learned merchant → category mapping, written when you confirm or fix a captured transaction. */
@Entity(tableName = "merchant_rules")
data class MerchantRuleEntity(
    @PrimaryKey val merchantKey: String,
    val categoryId: Long,
    val updatedAt: Long = System.currentTimeMillis(),
)

// ---------- Query results ----------

data class WalletWithBalance(
    @Embedded val wallet: WalletEntity,
    val balance: Long,
)

data class TransactionRow(
    val id: Long,
    val type: TxType,
    val amount: Long,
    val occurredAt: Long,
    val note: String,
    val merchant: String?,
    val source: TxSource,
    val status: TxStatus,
    val excludeFromReport: Boolean,
    val walletId: Long,
    val walletName: String,
    val toWalletId: Long?,
    val toWalletName: String?,
    val categoryId: Long?,
    val categoryName: String?,
    @ColumnInfo(name = "categoryKey") val categoryKey: String?,
    val categoryColor: Int?,
)

data class CategoryTotal(
    val categoryId: Long?,
    val name: String?,
    val builtinKey: String?,
    val colorIndex: Int?,
    val total: Long,
)

data class MonthTotal(
    /** "2026-10" */
    val month: String,
    val income: Long,
    val expense: Long,
)
