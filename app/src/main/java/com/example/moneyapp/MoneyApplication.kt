package com.example.moneyapp

import android.app.Application
import com.example.moneyapp.recurring.RecurringWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MoneyApplication : Application() {

    lateinit var container: AppContainer
        private set

    /** App-lifetime scope for fire-and-forget work. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        RecurringWorker.schedule(this)
        appScope.launch {
            // Catch up recurring transactions right away instead of waiting for the daily job.
            container.recurring.runDue()
            // Keep the listener component in sync with the feature flag (e.g. after restoring data).
            container.captureController.applyFlag(container.settings.current().autoCaptureEnabled)
        }
    }
}
