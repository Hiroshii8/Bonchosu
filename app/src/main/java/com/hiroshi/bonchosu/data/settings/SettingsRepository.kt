package com.hiroshi.bonchosu.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.hiroshi.bonchosu.capture.CaptureApp
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
    val theme: ThemeMode = ThemeMode.SYSTEM,
    /** Dark mode with a true black background (saves battery on OLED screens). */
    val amoled: Boolean = false,
    val accent: AccentColor = AccentColor.EMERALD,
    /** Take the colours from the wallpaper instead of [accent] (Android 12+). */
    val dynamicColor: Boolean = false,
    /** How big text, buttons and spacing are, all scaled together. */
    val uiSize: UiSize = UiSize.DEFAULT,
    val home: HomeLayout = HomeLayout(),
    val summaryFigures: List<SummaryFigure> = DEFAULT_FIGURES,
    /** Master switch for budget notifications. Off until you turn it on (it needs notification permission). */
    val budgetAlerts: Boolean = false,
    /** Apps you picked for auto-capture. Null until you pick any: the built-in bank list applies until then. */
    val captureApps: List<CaptureApp>? = null,
    /** When you last saved a backup file, or null if never. */
    val lastBackupAt: Long? = null,
)

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Accent for buttons, charts and highlights. */
enum class AccentColor { EMERALD, BLUE, VIOLET, ORANGE }

/** Display size: a multiplier on everything the app draws (text and layout together, so nothing gets cramped). */
enum class UiSize(val scale: Float) { SMALLER(0.85f), SMALL(0.92f), DEFAULT(1f), LARGE(1.1f) }

/** Optional cards on Home, below the balance card (which always shows). Declared in their default order. */
enum class HomeSection { REVIEW, BUDGETS, QUICK_ACTIONS, RECENT, SPLIT_BILL, SCAN_RECEIPTS, IMPORT_STATEMENT }

data class HomeLayout(
    val order: List<HomeSection> = HomeSection.entries,
    val hidden: Set<HomeSection> = setOf(HomeSection.SCAN_RECEIPTS, HomeSection.IMPORT_STATEMENT),
) {
    val visible get() = order.filter { it !in hidden }

    /**
     * Stored as "REVIEW,-IMPORT_STATEMENT,…": order kept, "-" = hidden. A section added in a later version goes right
     * after the section it follows by default, so it lands where it belongs instead of at the bottom.
     */
    fun encode() = order.joinToString(",") { if (it in hidden) "-${it.name}" else it.name }

    companion object {
        fun decode(raw: String?): HomeLayout {
            if (raw.isNullOrBlank()) return HomeLayout()
            val parsed = raw.split(',').mapNotNull { token ->
                HomeSection.entries.firstOrNull { it.name == token.removePrefix("-") }?.let { it to token.startsWith("-") }
            }
            val order = parsed.map { it.first }.distinct().toMutableList()
            val missing = HomeSection.entries.filter { it !in order }
            missing.forEach { section ->
                val before = HomeSection.entries.take(section.ordinal).lastOrNull { it in order }
                order.add(if (before == null) 0 else order.indexOf(before) + 1, section)
            }
            return HomeLayout(order, parsed.filter { it.second }.map { it.first }.toSet() + missing.filter { it in HomeLayout().hidden })
        }
    }
}

/** Figures the summary card can show for the chosen month. */
enum class SummaryFigure { INCOME, SPENT, NET, DAILY_AVERAGE, VS_LAST_MONTH }

val DEFAULT_FIGURES = listOf(SummaryFigure.INCOME, SummaryFigure.SPENT)

class SettingsRepository(private val context: Context) {

    private object Keys {
        val AUTO_CAPTURE = booleanPreferencesKey("feature_auto_capture")
        val AUTO_SAVE = booleanPreferencesKey("auto_save_confident")
        val HIDE_BALANCES = booleanPreferencesKey("hide_balances")
        val THEME = stringPreferencesKey("theme")
        val AMOLED = booleanPreferencesKey("theme_amoled")
        val ACCENT = stringPreferencesKey("theme_accent")
        val DYNAMIC = booleanPreferencesKey("theme_dynamic")
        val UI_SIZE = stringPreferencesKey("ui_size")
        val HOME = stringPreferencesKey("home_layout")
        val FIGURES = stringPreferencesKey("summary_figures")
        val BUDGET_ALERTS = booleanPreferencesKey("budget_alerts")
        val ALERTS_SENT = stringSetPreferencesKey("budget_alerts_sent")
        val CAPTURE_APPS = stringSetPreferencesKey("capture_apps")
        val LAST_BACKUP = longPreferencesKey("last_backup_at")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            autoCaptureEnabled = p[Keys.AUTO_CAPTURE] ?: false,
            autoSaveConfident = p[Keys.AUTO_SAVE] ?: false,
            hideBalances = p[Keys.HIDE_BALANCES] ?: false,
            theme = ThemeMode.entries.firstOrNull { it.name == p[Keys.THEME] } ?: ThemeMode.SYSTEM,
            amoled = p[Keys.AMOLED] ?: false,
            accent = AccentColor.entries.firstOrNull { it.name == p[Keys.ACCENT] } ?: AccentColor.EMERALD,
            dynamicColor = p[Keys.DYNAMIC] ?: false,
            uiSize = UiSize.entries.firstOrNull { it.name == p[Keys.UI_SIZE] } ?: UiSize.DEFAULT,
            home = HomeLayout.decode(p[Keys.HOME]),
            summaryFigures = p[Keys.FIGURES]?.split(',')?.mapNotNull { n -> SummaryFigure.entries.firstOrNull { it.name == n } }
                ?.takeIf { it.isNotEmpty() } ?: DEFAULT_FIGURES,
            budgetAlerts = p[Keys.BUDGET_ALERTS] ?: false,
            captureApps = p[Keys.CAPTURE_APPS]?.mapNotNull(CaptureApp::decode)?.sortedBy { it.label.lowercase() },
            lastBackupAt = p[Keys.LAST_BACKUP],
        )
    }

    suspend fun current(): AppSettings = settings.first()

    suspend fun setAutoCapture(enabled: Boolean) = context.dataStore.edit { it[Keys.AUTO_CAPTURE] = enabled }
    suspend fun setAutoSaveConfident(enabled: Boolean) = context.dataStore.edit { it[Keys.AUTO_SAVE] = enabled }
    suspend fun setHideBalances(enabled: Boolean) = context.dataStore.edit { it[Keys.HIDE_BALANCES] = enabled }
    suspend fun setTheme(mode: ThemeMode) = context.dataStore.edit { it[Keys.THEME] = mode.name }
    suspend fun setAmoled(enabled: Boolean) = context.dataStore.edit { it[Keys.AMOLED] = enabled }
    suspend fun setAccent(accent: AccentColor) = context.dataStore.edit { it[Keys.ACCENT] = accent.name }
    suspend fun setDynamicColor(enabled: Boolean) = context.dataStore.edit { it[Keys.DYNAMIC] = enabled }
    suspend fun setUiSize(size: UiSize) = context.dataStore.edit { it[Keys.UI_SIZE] = size.name }
    suspend fun setHomeLayout(layout: HomeLayout) = context.dataStore.edit { it[Keys.HOME] = layout.encode() }
    suspend fun setSummaryFigures(figures: List<SummaryFigure>) =
        context.dataStore.edit { it[Keys.FIGURES] = figures.joinToString(",") { f -> f.name } }
    suspend fun setBudgetAlerts(enabled: Boolean) = context.dataStore.edit { it[Keys.BUDGET_ALERTS] = enabled }
    suspend fun setCaptureApps(apps: List<CaptureApp>) =
        context.dataStore.edit { it[Keys.CAPTURE_APPS] = apps.map(CaptureApp::encode).toSet() }

    /** Keys of budget alerts already shown, so each level fires once per budget period. */
    suspend fun alertsSent(): Set<String> = context.dataStore.data.first()[Keys.ALERTS_SENT].orEmpty()
    suspend fun setAlertsSent(keys: Set<String>) = context.dataStore.edit { it[Keys.ALERTS_SENT] = keys }

    suspend fun setLastBackupAt(ms: Long) = context.dataStore.edit { it[Keys.LAST_BACKUP] = ms }

    /** Every stored setting, for a backup file. */
    suspend fun exportAll(): Map<String, Any> = context.dataStore.data.first().asMap().mapKeys { it.key.name }

    /** Replaces every setting with the ones from a backup. */
    suspend fun importAll(values: Map<String, Any>) = context.dataStore.edit { p ->
        p.clear()
        values.forEach { (name, value) ->
            when (value) {
                is Boolean -> p[booleanPreferencesKey(name)] = value
                is String -> p[stringPreferencesKey(name)] = value
                is Int -> p[intPreferencesKey(name)] = value
                is Long -> p[longPreferencesKey(name)] = value
                is Float -> p[floatPreferencesKey(name)] = value
                is Double -> p[doublePreferencesKey(name)] = value
                is Set<*> -> p[stringSetPreferencesKey(name)] = value.filterIsInstance<String>().toSet()
            }
        }
    }
}
