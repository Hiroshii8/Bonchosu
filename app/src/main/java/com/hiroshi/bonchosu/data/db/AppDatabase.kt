package com.hiroshi.bonchosu.data.db

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.hiroshi.bonchosu.R

@Database(
    entities = [
        WalletEntity::class,
        CategoryEntity::class,
        TransactionEntity::class,
        BudgetEntity::class,
        RecurringEntity::class,
        MerchantRuleEntity::class,
        BillEntity::class,
        BillPersonEntity::class,
        BillItemEntity::class,
        BillItemShareEntity::class,
        BillChargeEntity::class,
        KeywordRuleEntity::class,
    ],
    version = 4,
    exportSchema = true,
    // 3 → 4 adds keyword_rules (your own category words).
    autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3), AutoMigration(from = 3, to = 4)],
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun walletDao(): WalletDao
    abstract fun categoryDao(): CategoryDao
    abstract fun transactionDao(): TransactionDao
    abstract fun budgetDao(): BudgetDao
    abstract fun recurringDao(): RecurringDao
    abstract fun merchantRuleDao(): MerchantRuleDao
    abstract fun keywordRuleDao(): KeywordRuleDao
    abstract fun atomicDao(): AtomicDao
    abstract fun billDao(): BillDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "money.db")
                .addCallback(SeedCallback(context.applicationContext))
                // No destructive fallback: a schema change without a migration should crash, never wipe your money data.
                .build()
    }
}

/** Built-in categories. The key picks the translated label; colorIndex picks the palette entry. */
object DefaultCategories {
    data class Def(val key: String, val type: CategoryType, val englishName: String, val colorIndex: Int)

    val all = listOf(
        Def("food", CategoryType.EXPENSE, "Food & Drink", 0),
        Def("groceries", CategoryType.EXPENSE, "Groceries", 1),
        Def("transport", CategoryType.EXPENSE, "Transport", 2),
        Def("shopping", CategoryType.EXPENSE, "Shopping", 3),
        Def("bills", CategoryType.EXPENSE, "Bills & Utilities", 4),
        Def("entertainment", CategoryType.EXPENSE, "Entertainment", 5),
        Def("health", CategoryType.EXPENSE, "Health", 6),
        Def("education", CategoryType.EXPENSE, "Education", 7),
        Def("family", CategoryType.EXPENSE, "Family", 8),
        Def("gifts", CategoryType.EXPENSE, "Gifts & Donations", 9),
        Def("fees", CategoryType.EXPENSE, "Fees & Charges", 4),
        Def("other_expense", CategoryType.EXPENSE, "Other", 10),
        Def("salary", CategoryType.INCOME, "Salary", 11),
        Def("bonus", CategoryType.INCOME, "Bonus", 11),
        Def("business", CategoryType.INCOME, "Business", 2),
        Def("interest", CategoryType.INCOME, "Interest", 1),
        Def("gift_received", CategoryType.INCOME, "Gifts received", 9),
        Def("other_income", CategoryType.INCOME, "Other income", 10),
    )
}

private class SeedCallback(private val context: Context) : RoomDatabase.Callback() {
    override fun onCreate(db: SupportSQLiteDatabase) {
        DefaultCategories.all.forEach { def ->
            db.insert("categories", SQLiteDatabase.CONFLICT_ABORT, ContentValues().apply {
                put("name", def.englishName)
                put("type", def.type.name)
                put("builtinKey", def.key)
                put("colorIndex", def.colorIndex)
                put("archived", 0)
            })
        }
        db.insert("wallets", SQLiteDatabase.CONFLICT_ABORT, ContentValues().apply {
            put("name", context.getString(R.string.default_wallet_cash))
            put("type", WalletType.CASH.name)
            put("initialBalance", 0L)
            put("currency", "IDR")
            put("excludeFromTotal", 0)
            put("archived", 0)
            put("createdAt", System.currentTimeMillis())
        })
    }
}
