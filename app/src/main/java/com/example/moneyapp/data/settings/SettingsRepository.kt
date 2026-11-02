package com.example.moneyapp.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * App settings, including the feature flags.
 * Language isn't stored here: AppCompat persists the per-app locale itself.
 */
data class AppSettings(
    /** Feature flag for the whole auto-capture feature. Off = plain money manager. */
    val autoCaptureEnabled: Boolean = false,
    /** Save captures with a known category straight away instead of sending them to review. */
    val autoSaveConfident: Boolean = false,
    /** Hide balances on Home (for using the app in public). */
    val hideBalances: Boolean = false,
)

class SettingsRepository(private val context: Context) {

    private object Keys {
        val AUTO_CAPTURE = booleanPreferencesKey("feature_auto_capture")
        val AUTO_SAVE = booleanPreferencesKey("auto_save_confident")
        val HIDE_BALANCES = booleanPreferencesKey("hide_balances")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            autoCaptureEnabled = p[Keys.AUTO_CAPTURE] ?: false,
            autoSaveConfident = p[Keys.AUTO_SAVE] ?: false,
            hideBalances = p[Keys.HIDE_BALANCES] ?: false,
        )
    }

    suspend fun current(): AppSettings = settings.first()

    suspend fun setAutoCapture(enabled: Boolean) = context.dataStore.edit { it[Keys.AUTO_CAPTURE] = enabled }
    suspend fun setAutoSaveConfident(enabled: Boolean) = context.dataStore.edit { it[Keys.AUTO_SAVE] = enabled }
    suspend fun setHideBalances(enabled: Boolean) = context.dataStore.edit { it[Keys.HIDE_BALANCES] = enabled }
}
