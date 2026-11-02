package com.example.moneyapp.capture

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.service.notification.NotificationListenerService
import androidx.core.app.NotificationManagerCompat

/** Wires the auto-capture feature flag to the Android side: the listener component and its permission. */
class CaptureController(private val context: Context) {

    private val component = ComponentName(context, TransactionListenerService::class.java)

    /**
     * Flag off → the listener component is disabled, so Android never starts it and the app
     * disappears from "Notification access". Flag on → enabled and (re)bound if access is granted.
     */
    fun applyFlag(enabled: Boolean) {
        context.packageManager.setComponentEnabledSetting(
            component,
            if (enabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP,
        )
        if (enabled && isAccessGranted()) {
            NotificationListenerService.requestRebind(component)
        }
    }

    fun isAccessGranted(): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

    /** Opens the system screen where the user grants notification access. */
    fun accessSettingsIntent(): Intent =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, component.flattenToString())
        } else {
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
