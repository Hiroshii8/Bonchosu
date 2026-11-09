package com.hiroshi.bonchosu.ui.screens

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import com.hiroshi.bonchosu.data.settings.ThemeMode
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Science
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.hiroshi.bonchosu.AppContainer
import com.hiroshi.bonchosu.BuildConfig
import com.hiroshi.bonchosu.R
import com.hiroshi.bonchosu.data.settings.AppSettings
import com.hiroshi.bonchosu.data.settings.AccentColor
import com.hiroshi.bonchosu.ui.components.Overline
import com.hiroshi.bonchosu.ui.theme.dynamicColorSupported
import com.hiroshi.bonchosu.ui.Routes
import com.hiroshi.bonchosu.ui.components.DropdownField
import com.hiroshi.bonchosu.ui.components.ScreenScaffold
import com.hiroshi.bonchosu.ui.components.SectionCard
import com.hiroshi.bonchosu.ui.components.appContainer
import com.hiroshi.bonchosu.ui.theme.Brand
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val c: AppContainer) : ViewModel() {
    val settings = c.settings.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())
    val pendingCount = c.transactions.observePendingCount().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** The feature flag. Also flips the Android listener component so "off" really means off. */
    fun setAutoCapture(enabled: Boolean) = viewModelScope.launch {
        c.settings.setAutoCapture(enabled)
        c.captureController.applyFlag(enabled)
    }

    fun setAutoSave(enabled: Boolean) = viewModelScope.launch { c.settings.setAutoSaveConfident(enabled) }
    fun setHideBalances(enabled: Boolean) = viewModelScope.launch { c.settings.setHideBalances(enabled) }
    fun setTheme(mode: ThemeMode) = viewModelScope.launch { c.settings.setTheme(mode) }
    fun setBudgetAlerts(enabled: Boolean) = viewModelScope.launch {
        c.settings.setBudgetAlerts(enabled)
        if (enabled) c.budgetNotifier.check()
    }
    fun canNotify() = c.budgetNotifier.canNotify()
    fun accessGranted() = c.captureController.isAccessGranted()
}

private enum class AppLanguage(val tag: String?) { SYSTEM(null), ENGLISH("en"), INDONESIAN("in") }

private fun currentLanguage(): AppLanguage {
    val lang = AppCompatDelegate.getApplicationLocales()[0]?.language ?: return AppLanguage.SYSTEM
    return if (lang == "in" || lang == "id") AppLanguage.INDONESIAN else AppLanguage.ENGLISH
}

@Composable
fun SettingsScreen(nav: NavController) {
    val c = appContainer()
    val vm: SettingsViewModel = viewModel { SettingsViewModel(c) }
    val s by vm.settings.collectAsStateWithLifecycle()
    val pending by vm.pendingCount.collectAsStateWithLifecycle()
    var granted by remember { mutableStateOf(vm.accessGranted()) }
    var canNotify by remember { mutableStateOf(vm.canNotify()) }
    LifecycleResumeEffect(Unit) {
        granted = vm.accessGranted() // re-check after returning from Android's settings
        canNotify = vm.canNotify()
        onPauseOrDispose { }
    }
    val context = LocalContext.current
    val askNotify = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        canNotify = vm.canNotify()
        vm.setBudgetAlerts(ok)
    }
    val notificationSettings = {
        context.startActivity(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        )
    }

    // A bottom-bar tab now, so no back button.
    ScreenScaffold(title = stringResource(R.string.settings)) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Header(stringResource(R.string.general)) }
            item {
                SectionCard {
                    DropdownField(
                        label = stringResource(R.string.language),
                        options = AppLanguage.entries,
                        selected = currentLanguage(),
                        optionLabel = {
                            stringResource(
                                when (it) {
                                    AppLanguage.SYSTEM -> R.string.language_system
                                    AppLanguage.ENGLISH -> R.string.language_english
                                    AppLanguage.INDONESIAN -> R.string.language_indonesian
                                }
                            )
                        },
                        onSelect = { lang ->
                            // AppCompat stores the choice and recreates the screen in the new language.
                            AppCompatDelegate.setApplicationLocales(
                                lang.tag?.let { LocaleListCompat.forLanguageTags(it) } ?: LocaleListCompat.getEmptyLocaleList()
                            )
                        },
                    )
                    val mode = stringResource(
                        when (s.theme) {
                            ThemeMode.SYSTEM -> R.string.theme_system_short
                            ThemeMode.LIGHT -> R.string.theme_light
                            ThemeMode.DARK -> R.string.theme_dark
                        }
                    )
                    val accent = stringResource(
                        when {
                            s.dynamicColor && dynamicColorSupported -> R.string.wallpaper
                            s.accent == AccentColor.BLUE -> R.string.accent_blue
                            s.accent == AccentColor.VIOLET -> R.string.accent_violet
                            s.accent == AccentColor.ORANGE -> R.string.accent_orange
                            else -> R.string.accent_emerald
                        }
                    )
                    NavRow(stringResource(R.string.appearance), stringResource(R.string.appearance_summary, mode, accent)) {
                        nav.navigate(Routes.APPEARANCE)
                    }
                    SwitchRow(stringResource(R.string.hide_balances), stringResource(R.string.hide_balances_hint), s.hideBalances) { vm.setHideBalances(it) }
                    SwitchRow(stringResource(R.string.budget_alerts), stringResource(R.string.budget_alerts_setting_hint), s.budgetAlerts) { on ->
                        when {
                            !on -> vm.setBudgetAlerts(false)
                            vm.canNotify() -> vm.setBudgetAlerts(true)
                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> askNotify.launch(Manifest.permission.POST_NOTIFICATIONS)
                            else -> { vm.setBudgetAlerts(true); notificationSettings() }
                        }
                    }
                    if (s.budgetAlerts && !canNotify) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                stringResource(R.string.notifications_blocked), fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = notificationSettings) { Text(stringResource(R.string.grant)) }
                        }
                    }
                }
            }

            item { Header(stringResource(R.string.manage)) }
            item {
                SectionCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
                    NavRow(stringResource(R.string.wallets)) { nav.navigate(Routes.WALLETS) }
                    HorizontalDivider(color = Brand.Border)
                    NavRow(stringResource(R.string.budgets)) { nav.navigate(Routes.BUDGETS) }
                    HorizontalDivider(color = Brand.Border)
                    NavRow(stringResource(R.string.bill_title)) { nav.navigate(Routes.BILLS) }
                    HorizontalDivider(color = Brand.Border)
                    NavRow(stringResource(R.string.categories)) { nav.navigate(Routes.CATEGORIES) }
                    HorizontalDivider(color = Brand.Border)
                    NavRow(stringResource(R.string.keywords_title)) { nav.navigate(Routes.KEYWORD_RULES) }
                    HorizontalDivider(color = Brand.Border)
                    NavRow(stringResource(R.string.recurring)) { nav.navigate(Routes.RECURRING) }
                    HorizontalDivider(color = Brand.Border)
                    NavRow(stringResource(R.string.statement_title)) { nav.navigate(Routes.STATEMENT_IMPORT) }
                    HorizontalDivider(color = Brand.Border)
                    NavRow(stringResource(R.string.receipts_title)) { nav.navigate(Routes.RECEIPT_SCAN) }
                    HorizontalDivider(color = Brand.Border)
                    NavRow(stringResource(R.string.customize_home)) { nav.navigate(Routes.HOME_CUSTOMIZE) }
                    HorizontalDivider(color = Brand.Border)
                    NavRow(stringResource(R.string.backup_title)) { nav.navigate(Routes.BACKUP) }
                }
            }

            item { Header(stringResource(R.string.labs), Icons.Default.Science) }
            item {
                SectionCard {
                    SwitchRow(stringResource(R.string.flag_auto_capture), stringResource(R.string.flag_auto_capture_hint), s.autoCaptureEnabled) { on ->
                        vm.setAutoCapture(on)
                        when {
                            !on -> Unit
                            !vm.accessGranted() -> nav.navigate(Routes.CAPTURE_SETUP)
                            // Nothing is read until apps are picked, so go straight there.
                            s.captureApps.isNullOrEmpty() -> nav.navigate(Routes.CAPTURE_APPS)
                        }
                    }
                    if (s.autoCaptureEnabled) {
                        HorizontalDivider(color = Brand.Border)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(R.string.notification_access), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Text(
                                    stringResource(if (granted) R.string.access_granted else R.string.access_missing),
                                    fontSize = 12.sp, color = if (granted) Brand.Accent else MaterialTheme.colorScheme.error,
                                )
                            }
                            if (!granted) TextButton(onClick = { nav.navigate(Routes.CAPTURE_SETUP) }) { Text(stringResource(R.string.grant)) }
                        }
                        SwitchRow(stringResource(R.string.auto_save), stringResource(R.string.auto_save_hint), s.autoSaveConfident) { vm.setAutoSave(it) }
                        NavRow(stringResource(R.string.review_inbox_n, pending)) { nav.navigate(Routes.REVIEW) }
                        NavRow(
                            s.captureApps?.takeIf { it.isNotEmpty() }?.let { stringResource(R.string.capture_apps_n, it.size) }
                                ?: stringResource(R.string.capture_apps_choose)
                        ) { nav.navigate(Routes.CAPTURE_APPS) }
                        val picked = s.captureApps.orEmpty()
                        Text(
                            if (picked.isEmpty()) stringResource(R.string.capture_apps_none_hint)
                            else stringResource(R.string.supported_apps, picked.joinToString(", ") { it.label }),
                            fontSize = 12.sp,
                            color = if (picked.isEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            item {
                Text(
                    stringResource(R.string.version_n, BuildConfig.VERSION_NAME),
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun Header(text: String, icon: ImageVector? = null) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(start = 4.dp, top = 10.dp)) {
        if (icon != null) Icon(icon, null, tint = Brand.Muted, modifier = Modifier.size(16.dp))
        Overline(text)
    }
}

@Composable
private fun NavRow(title: String, subtitle: String? = null, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            if (subtitle != null) Text(subtitle, fontSize = 13.sp, color = Brand.Muted)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Brand.Muted)
    }
}

// ---------- Auto-capture setup (shown when the flag is turned on) ----------

@Composable
fun CaptureSetupScreen(nav: NavController) {
    val c = appContainer()
    val context = LocalContext.current
    var granted by remember { mutableStateOf(c.captureController.isAccessGranted()) }
    LifecycleResumeEffect(Unit) {
        granted = c.captureController.isAccessGranted()
        onPauseOrDispose { }
    }

    ScreenScaffold(title = "", onBack = { nav.popBackStack() }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // Before/after illustration: a bank notification becomes a categorised transaction.
            Surface(color = Brand.AccentSoft, shape = RoundedCornerShape(24.dp)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(color = Brand.Surface, shape = RoundedCornerShape(14.dp), shadowElevation = 2.dp) {
                        Column(Modifier.fillMaxWidth().padding(12.dp)) {
                            Text(stringResource(R.string.setup_sample_source), fontSize = 11.sp, color = Brand.Muted, fontWeight = FontWeight.SemiBold)
                            Text(stringResource(R.string.setup_sample_text), fontSize = 13.sp)
                        }
                    }
                    Surface(color = Brand.Surface, shape = RoundedCornerShape(14.dp)) {
                        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.setup_sample_result), fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            Text("−Rp 35.000", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Text(stringResource(R.string.setup_title), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.setup_body), color = MaterialTheme.colorScheme.onSurfaceVariant)

            Point(Icons.Default.Bolt, stringResource(R.string.setup_point1_title), stringResource(R.string.setup_point1_body))
            Point(Icons.Default.Lock, stringResource(R.string.setup_point2_title), stringResource(R.string.setup_point2_body))
            Point(Icons.Default.Check, stringResource(R.string.setup_point3_title), stringResource(R.string.setup_point3_body))

            if (granted) {
                Surface(color = Brand.AccentSoft, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.setup_done), color = Brand.Accent, fontWeight = FontWeight.Bold, modifier = Modifier.padding(14.dp))
                }
                Button(onClick = { nav.popBackStack() }, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(14.dp)) {
                    Text(stringResource(R.string.done))
                }
            } else {
                Button(
                    onClick = { context.startActivity(c.captureController.accessSettingsIntent()) },
                    modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(14.dp),
                ) { Text(stringResource(R.string.allow_access)) }
                OutlinedButton(onClick = { nav.popBackStack() }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.not_now)) }
            }
            Box(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun Point(icon: ImageVector, title: String, body: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(shape = CircleShape, color = Brand.AccentSoft, modifier = Modifier.size(32.dp)) {
            Box(contentAlignment = Alignment.Center) { Icon(icon, null, tint = Brand.Accent, modifier = Modifier.size(16.dp)) }
        }
        Column {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(body, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
