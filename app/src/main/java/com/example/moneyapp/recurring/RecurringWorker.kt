package com.hiroshi.moneyapp.recurring

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.hiroshi.moneyapp.MoneyApplication
import java.util.concurrent.TimeUnit

/** Daily background job (think: cron) that creates due recurring transactions, then checks budget alerts. */
class RecurringWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as MoneyApplication
        return runCatching {
            app.container.recurring.runDue()
            app.container.budgetNotifier.check()
        }.fold(onSuccess = { Result.success() }, onFailure = { Result.retry() })
    }

    companion object {
        private const val NAME = "recurring-daily"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<RecurringWorker>(1, TimeUnit.DAYS).build()
            // UPDATE, not KEEP: a job saved by an older build may name a worker class that no longer exists
            // (the code moved from com.example to com.hiroshi), and KEEP would leave that broken job in place.
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
        }
    }
}
