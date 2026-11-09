package com.hiroshi.bonchosu.ui.screens

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.hiroshi.bonchosu.AppContainer
import com.hiroshi.bonchosu.R
import com.hiroshi.bonchosu.capture.CaptureApp
import com.hiroshi.bonchosu.capture.InstalledApp
import com.hiroshi.bonchosu.capture.InstalledApps
import com.hiroshi.bonchosu.data.db.WalletEntity
import com.hiroshi.bonchosu.ui.components.DropdownField
import com.hiroshi.bonchosu.ui.components.ScreenScaffold
import com.hiroshi.bonchosu.ui.components.SectionCard
import com.hiroshi.bonchosu.ui.components.appContainer
import com.hiroshi.bonchosu.ui.theme.Brand
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class CaptureAppsUi(val loading: Boolean = true, val apps: List<InstalledApp> = emptyList(), val chosen: List<CaptureApp> = emptyList())

class CaptureAppsViewModel(private val c: AppContainer, context: Context) : ViewModel() {
    val ui = MutableStateFlow(CaptureAppsUi())

    init {
        viewModelScope.launch {
            val installed = withContext(Dispatchers.IO) { InstalledApps.withLauncher(context) }
            // Nothing is ticked for you: supported apps are only suggested, at the top of the list.
            val chosen = c.settings.current().captureApps.orEmpty()
            ui.value = CaptureAppsUi(
                loading = false,
                // Picked apps first, then suggestions, then everything else A–Z.
                apps = installed.sortedWith(
                    compareBy<InstalledApp>({ app -> chosen.none { it.packageName == app.packageName } }, { !it.suggested }, { it.label.lowercase() })
                ),
                chosen = chosen,
            )
        }
    }

    fun toggle(app: InstalledApp) = update { chosen ->
        if (chosen.any { it.packageName == app.packageName }) chosen.filter { it.packageName != app.packageName }
        else chosen + CaptureApp(app.packageName, app.label, null)
    }

    fun setWallet(packageName: String, walletId: Long?) = update { chosen ->
        chosen.map { if (it.packageName == packageName) it.copy(walletId = walletId) else it }
    }

    /** Every change is saved straight away; captures pick it up from the next notification. */
    private fun update(change: (List<CaptureApp>) -> List<CaptureApp>) {
        val next = change(ui.value.chosen)
        ui.value = ui.value.copy(chosen = next)
        viewModelScope.launch { c.settings.setCaptureApps(next) }
    }
}

@Composable
fun CaptureAppsScreen(nav: NavController) {
    val c = appContainer()
    val context = LocalContext.current
    val vm: CaptureAppsViewModel = viewModel { CaptureAppsViewModel(c, context.applicationContext) }
    val s by vm.ui.collectAsStateWithLifecycle()
    val wallets by rememberWallets(c)
    var query by rememberSaveable { mutableStateOf("") }

    ScreenScaffold(title = stringResource(R.string.capture_apps_title), onBack = { nav.popBackStack() }) { padding ->
        if (s.loading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@ScreenScaffold
        }
        val shown = s.apps.filter { query.isBlank() || it.label.contains(query.trim(), ignoreCase = true) }
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { Text(stringResource(R.string.capture_apps_intro), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            item {
                OutlinedTextField(
                    value = query, onValueChange = { query = it }, singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    placeholder = { Text(stringResource(R.string.capture_apps_search)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            items(shown, key = { it.packageName }) { app ->
                val choice = s.chosen.firstOrNull { it.packageName == app.packageName }
                AppRow(app, choice, wallets, onToggle = { vm.toggle(app) }, onWallet = { vm.setWallet(app.packageName, it) })
            }
        }
    }
}

@Composable
private fun AppRow(app: InstalledApp, choice: CaptureApp?, wallets: List<WalletEntity>, onToggle: () -> Unit, onWallet: (Long?) -> Unit) {
    SectionCard(contentPadding = PaddingValues(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp)) {
        Row(Modifier.fillMaxWidth().clickable(onClick = onToggle), verticalAlignment = Alignment.CenterVertically) {
            AppIcon(app.packageName)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(app.label, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                if (app.suggested) Text(stringResource(R.string.capture_apps_suggested), fontSize = 12.sp, color = Brand.Accent)
            }
            Checkbox(checked = choice != null, onCheckedChange = { onToggle() })
        }
        if (choice != null) {
            val newWallet = stringResource(R.string.capture_apps_new_wallet, app.label)
            DropdownField<WalletEntity?>(
                label = stringResource(R.string.capture_apps_wallet),
                options = listOf(null) + wallets,
                selected = wallets.firstOrNull { it.id == choice.walletId },
                optionLabel = { it?.name ?: newWallet },
                onSelect = { onWallet(it?.id) },
                placeholder = newWallet,
                modifier = Modifier.padding(end = 8.dp),
            )
        }
    }
}

@Composable
private fun AppIcon(packageName: String) {
    val context = LocalContext.current
    val icon by produceState<ImageBitmap?>(null, packageName) {
        value = withContext(Dispatchers.IO) {
            runCatching { context.packageManager.getApplicationIcon(packageName).toBitmap(96, 96).asImageBitmap() }.getOrNull()
        }
    }
    Box(Modifier.size(40.dp)) { icon?.let { Image(it, contentDescription = null, modifier = Modifier.fillMaxSize()) } }
}
