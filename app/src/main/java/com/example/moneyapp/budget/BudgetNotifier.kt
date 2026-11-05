package com.hiroshi.moneyapp.budget

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.hiroshi.moneyapp.MainActivity
import com.hiroshi.moneyapp.R
import com.hiroshi.moneyapp.core.BudgetAlerts
import com.hiroshi.moneyapp.core.Money
import com.hiroshi.moneyapp.data.BudgetRepository
import com.hiroshi.moneyapp.data.CategoryRepository
import com.hiroshi.moneyapp.data.settings.SettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate

/**
 * Posts "budget 75% used" style notifications. Runs after every change to spending (while the app process is
 * alive, which includes notification capture) and once a day from the background job.
 */
class BudgetNotifier(
    private val context: Context,
    private val settings: SettingsRepository,
    private val budgets: BudgetRepository,
    private val categories: CategoryRepository,
) {
    private val mutex = Mutex()

    fun canNotify(): Boolean =
        (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(context).areNotificationsEnabled()

    suspend fun check() = mutex.withLock {
        if (!settings.current().budgetAlerts) return@withLock
        val progress = budgets.observeProgress(LocalDate.now()).first()
        val result = BudgetAlerts.check(
            progress.map { (b, p) -> BudgetAlerts.Input(b.id, BudgetAlerts.parseLevels(b.alertLevels), p) },
            settings.alertsSent(),
        )
        // Mark as sent even without permission, so allowing it later doesn't replay old alerts.
        settings.setAlertsSent(result.sent)
        if (result.alerts.isEmpty() || !canNotify()) return@withLock

        createChannel()
        val names = categories.observeAll().first().associate { it.id to it.name }
        val budgetsById = progress.associate { it.first.id to it.first }
        result.alerts.forEach { alert ->
            val category = budgetsById[alert.budgetId]?.categoryId
            val name = category?.let { names[it] } ?: context.getString(R.string.all_expenses)
            val p = alert.progress
            val text = if (p.overBudget) context.getString(R.string.alert_over, Money.format(-p.remaining))
            else context.getString(R.string.alert_left, Money.format(p.remaining), p.daysLeft)
            post(alert.budgetId.toInt(), context.getString(R.string.alert_title, name, alert.level), text)
        }
    }

    @Suppress("MissingPermission") // checked in canNotify()
    private fun post(id: Int, title: String, text: String) {
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        // One notification per budget: a newer level replaces the older one.
        NotificationManagerCompat.from(context).notify(TAG, id, notification)
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(CHANNEL, context.getString(R.string.alert_channel), NotificationManager.IMPORTANCE_DEFAULT)
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private companion object {
        const val CHANNEL = "budget_alerts"
        const val TAG = "budget"
    }
}
