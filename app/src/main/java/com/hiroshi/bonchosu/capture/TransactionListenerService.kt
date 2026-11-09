package com.hiroshi.bonchosu.capture

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.hiroshi.bonchosu.BuildConfig
import com.hiroshi.bonchosu.BonchosuApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Receives every notification posted on the phone (think: message consumer).
 * Only runs when the auto-capture flag is on: CaptureController enables/disables this component.
 */
class TransactionListenerService : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName) return
        val n = sbn.notification
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return // summaries repeat the children

        val extras = n.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
            ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString().orEmpty()
        if (title.isBlank() && text.isBlank()) return

        if (BuildConfig.DEBUG && DEBUG_CAPTURE) {
            Log.d(TAG, "pkg=${sbn.packageName} | title=$title | text=$text")
        }
        // Which apps count (your picks, or the built-in list) is decided in the processor.
        val processor = (application as BonchosuApplication).container.captureProcessor
        scope.launch {
            runCatching { processor.handle(sbn.packageName, title, text, sbn.postTime) }
                .onFailure { Log.w(TAG, "capture failed", it) }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "NotifCapture"

        /**
         * Set to true while collecting real samples from your banks (debug builds only).
         * It logs EVERY app's notifications, so never leave it on day to day.
         */
        const val DEBUG_CAPTURE = false
    }
}
