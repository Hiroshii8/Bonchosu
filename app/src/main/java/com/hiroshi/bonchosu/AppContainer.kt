package com.hiroshi.bonchosu

import android.content.Context
import com.hiroshi.bonchosu.backup.BackupManager
import com.hiroshi.bonchosu.budget.BudgetNotifier
import com.hiroshi.bonchosu.capture.CaptureController
import com.hiroshi.bonchosu.capture.CaptureProcessor
import com.hiroshi.bonchosu.data.BillRepository
import com.hiroshi.bonchosu.data.BudgetRepository
import com.hiroshi.bonchosu.data.CategoryRepository
import com.hiroshi.bonchosu.data.KeywordRuleRepository
import com.hiroshi.bonchosu.data.RecurringRepository
import com.hiroshi.bonchosu.data.TransactionRepository
import com.hiroshi.bonchosu.data.WalletRepository
import com.hiroshi.bonchosu.data.db.AppDatabase
import com.hiroshi.bonchosu.data.settings.SettingsRepository
import com.hiroshi.bonchosu.statement.OcrReader
import com.hiroshi.bonchosu.statement.StatementImporter
import com.hiroshi.bonchosu.statement.StatementReader

/**
 * Manual dependency injection: one place that builds and owns the singletons.
 * (Same idea as a Spring context, without the annotations. Swap for Hilt later if you like.)
 */
class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext

    val db: AppDatabase by lazy { AppDatabase.build(appContext) }
    val settings by lazy { SettingsRepository(appContext) }

    val wallets by lazy { WalletRepository(db) }
    val categories by lazy { CategoryRepository(db) }
    val transactions by lazy { TransactionRepository(db) }
    val keywordRules by lazy { KeywordRuleRepository(db, transactions) }
    val budgets by lazy { BudgetRepository(db, transactions) }
    val recurring by lazy { RecurringRepository(db) }
    val bills by lazy { BillRepository(db) }
    val budgetNotifier by lazy { BudgetNotifier(appContext, settings, budgets, categories) }

    val captureController by lazy { CaptureController(appContext) }
    val captureProcessor by lazy { CaptureProcessor(db, settings, transactions) }

    val backup by lazy { BackupManager(appContext, db, settings) }
    val ocr by lazy { OcrReader(appContext) }
    val statementReader by lazy { StatementReader(appContext, ocr) }
    val statementImporter by lazy { StatementImporter(db, transactions) }
}
