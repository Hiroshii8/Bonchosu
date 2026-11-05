package com.hiroshi.moneyapp

import android.app.Application
import com.hiroshi.moneyapp.backup.BackupManager
import com.hiroshi.moneyapp.recurring.RecurringWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch

class MoneyApplication : Application() {

    lateinit var container: AppContainer
        private set

    /** App-lifetime scope for fire-and-forget work. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @OptIn(FlowPreview::class)
    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // Before anything opens the database: swap in a backup chosen on the Backup & restore screen.
        BackupManager.applyPendingRestore(this, container.settings)
        RecurringWorker.schedule(this)
        appScope.launch {
            // Catch up recurring transactions right away instead of waiting for the daily job.
            container.recurring.runDue()
            // Keep the listener component in sync with the feature flag (e.g. after restoring data).
            container.captureController.applyFlag(container.settings.current().autoCaptureEnabled)
        }
        appScope.launch {
            // Any change to budgets or spending (typed, captured, imported, split) re-checks the alert levels.
            container.budgets.observeProgress().debounce(1_000).collect {
                runCatching { container.budgetNotifier.check() }
            }
        }
    }
}
