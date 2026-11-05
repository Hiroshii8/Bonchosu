package com.hiroshi.moneyapp.capture

import android.content.Context
import android.content.Intent
import com.hiroshi.moneyapp.data.db.AppDatabase
import com.hiroshi.moneyapp.data.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** An app on the phone that has a home-screen icon. */
data class InstalledApp(val packageName: String, val label: String) {
    /** One of the banking apps the app knows: shown as a suggestion in the picker. */
    val suggested: Boolean get() = packageName in ParserRegistry.knownPackages
}

object InstalledApps {
    /** Uses a launcher query (declared in the manifest's <queries>) instead of reading every installed package, which Google Play restricts. */
    fun withLauncher(context: Context): List<InstalledApp> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, 0)
            .map { InstalledApp(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
            .filter { it.packageName != context.packageName }
            .distinctBy { it.packageName }
    }

    /**
     * One-time move for installs from before the app picker, which read a built-in list of banks.
     * If capture is on and no apps were ever picked, picks the known banking apps that are installed and links each
     * to the wallet already created for it, so capture carries on as before. Does nothing once apps have been picked.
     */
    suspend fun pickKnownAppsOnce(context: Context, settings: SettingsRepository, db: AppDatabase) {
        val s = settings.current()
        if (s.captureApps != null || !s.autoCaptureEnabled) return
        val installed = withContext(Dispatchers.IO) { withLauncher(context) }
        val picked = installed.mapNotNull { app ->
            val known = ParserRegistry.known(app.packageName) ?: return@mapNotNull null
            CaptureApp(app.packageName, app.label, db.walletDao().findByCaptureSource(known.name)?.id)
        }
        settings.setCaptureApps(picked)
    }
}
