package com.example.moneyapp

import android.content.Context
import com.example.moneyapp.capture.CaptureController
import com.example.moneyapp.capture.CaptureProcessor
import com.example.moneyapp.data.BillRepository
import com.example.moneyapp.data.BudgetRepository
import com.example.moneyapp.data.CategoryRepository
import com.example.moneyapp.data.RecurringRepository
import com.example.moneyapp.data.TransactionRepository
import com.example.moneyapp.data.WalletRepository
import com.example.moneyapp.data.db.AppDatabase
import com.example.moneyapp.data.settings.SettingsRepository
import com.example.moneyapp.statement.OcrReader
import com.example.moneyapp.statement.StatementImporter
import com.example.moneyapp.statement.StatementReader

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

    val captureController by lazy { CaptureController(appContext) }
    val captureProcessor by lazy { CaptureProcessor(db, settings, transactions) }

    val ocr by lazy { OcrReader(appContext) }
    val statementReader by lazy { StatementReader(appContext, ocr) }
    val statementImporter by lazy { StatementImporter(db, transactions) }
}
