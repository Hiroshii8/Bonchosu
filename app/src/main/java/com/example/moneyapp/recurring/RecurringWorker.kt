package com.example.moneyapp.recurring

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.moneyapp.MoneyApplication
import java.util.concurrent.TimeUnit

/** Daily background job (think: cron) that creates due recurring transactions. */
class RecurringWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as MoneyApplication
        return runCatching { app.container.recurring.runDue() }
            .fold(onSuccess = { Result.success() }, onFailure = { Result.retry() })
    }

    companion object {
        private const val NAME = "recurring-daily"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<RecurringWorker>(1, TimeUnit.DAYS).build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
