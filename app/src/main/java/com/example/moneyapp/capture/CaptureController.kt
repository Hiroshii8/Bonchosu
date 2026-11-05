package com.hiroshi.moneyapp.capture

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.service.notification.NotificationListenerService

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

    /**
     * Checks this exact listener, not just the package: access granted to an older build's listener class
     * (before the code moved to com.hiroshi) still lists the package but no longer delivers anything.
     */
    fun isAccessGranted(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            context.getSystemService(NotificationManager::class.java).isNotificationListenerAccessGranted(component)
        } else {
            Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners").orEmpty()
                .split(':').mapNotNull(ComponentName::unflattenFromString).contains(component)
        }

    /** Opens the system screen where the user grants notification access. */
    fun accessSettingsIntent(): Intent =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, component.flattenToString())
        } else {
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
