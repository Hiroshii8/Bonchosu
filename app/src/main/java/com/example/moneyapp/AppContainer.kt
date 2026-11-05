package com.hiroshi.moneyapp

import android.content.Context
import com.hiroshi.moneyapp.backup.BackupManager
import com.hiroshi.moneyapp.budget.BudgetNotifier
import com.hiroshi.moneyapp.capture.CaptureController
import com.hiroshi.moneyapp.capture.CaptureProcessor
import com.hiroshi.moneyapp.data.BillRepository
import com.hiroshi.moneyapp.data.BudgetRepository
import com.hiroshi.moneyapp.data.CategoryRepository
import com.hiroshi.moneyapp.data.RecurringRepository
import com.hiroshi.moneyapp.data.TransactionRepository
import com.hiroshi.moneyapp.data.WalletRepository
import com.hiroshi.moneyapp.data.db.AppDatabase
import com.hiroshi.moneyapp.data.settings.SettingsRepository
import com.hiroshi.moneyapp.statement.OcrReader
import com.hiroshi.moneyapp.statement.StatementImporter
import com.hiroshi.moneyapp.statement.StatementReader

/**
 * Manual dependency injection: one place that builds and owns the singletons.
 * (Same idea as a Spring context, without the annotations. Swap for Hilt later if you like.)
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val db: AppDatabase by lazy { AppDatabase.build(appContext) }
    val settings by lazy { SettingsRepository(appContext) }

    val wallets by lazy { WalletRepository(db) }
    val categories by lazy { CategoryRepository(db) }
    val transactions by lazy { TransactionRepository(db) }
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
