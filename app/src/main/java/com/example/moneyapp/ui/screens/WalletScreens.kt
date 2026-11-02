package com.example.moneyapp.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.moneyapp.AppContainer
import com.example.moneyapp.R
import com.example.moneyapp.capture.ParserRegistry
import com.example.moneyapp.core.Money
import com.example.moneyapp.data.db.WalletEntity
import com.example.moneyapp.data.db.WalletType
import com.example.moneyapp.data.db.WalletWithBalance
import com.example.moneyapp.data.settings.AppSettings
import com.example.moneyapp.ui.Routes
import com.example.moneyapp.ui.components.AmountField
import com.example.moneyapp.ui.components.CategoryBadge
import com.example.moneyapp.ui.components.ConfirmDialog
import com.example.moneyapp.ui.components.DropdownField
import com.example.moneyapp.ui.components.ScreenScaffold
import com.example.moneyapp.ui.components.SectionCard
import com.example.moneyapp.ui.components.appContainer
import com.example.moneyapp.ui.theme.Brand
import com.example.moneyapp.ui.theme.AmountStyle
import com.example.moneyapp.ui.walletTypeLabel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// ---------- List ----------

class WalletsViewModel(c: AppContainer) : ViewModel() {
    val wallets = c.wallets.observeWithBalance(includeArchived = true)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val settings = c.settings.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())
}

@Composable
fun WalletsScreen(nav: NavController) {
    val c = appContainer()
    val vm: WalletsViewModel = viewModel { WalletsViewModel(c) }
    val wallets by vm.wallets.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val active = wallets.filter { !it.wallet.archived }
    val archived = wallets.filter { it.wallet.archived }

    ScreenScaffold(
        title = stringResource(R.string.wallets),
        onBack = { nav.popBackStack() },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { nav.navigate(Routes.wallet()) },
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text(stringResource(R.string.add_wallet)) },
                containerColor = Brand.Green, contentColor = Color.White,
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column(Modifier.padding(horizontal = 4.dp)) {
                    Text(stringResource(R.string.total_across_wallets), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(Money.format(active.filter { !it.wallet.excludeFromTotal }.sumOf { it.balance }), style = AmountStyle)
                }
            }
            item {
                SectionCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
                    active.forEachIndexed { i, w ->
                        if (i > 0) HorizontalDivider(color = Brand.Line)
                        WalletRow(w, settings.autoCaptureEnabled) { nav.navigate(Routes.wallet(w.wallet.id)) }
                    }
                }
            }
            if (archived.isNotEmpty()) {
                item { Text(stringResource(R.string.archived), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 4.dp)) }
                item {
                    SectionCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
                        archived.forEach { w -> WalletRow(w, false) { nav.navigate(Routes.wallet(w.wallet.id)) } }
                    }
                }
            }
        }
    }
}

@Composable
private fun WalletRow(w: WalletWithBalance, showCapture: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CategoryBadge(w.wallet.name, colorIndexFor(w.wallet.type), size = 42.dp)
        Column(Modifier.weight(1f)) {
            Text(w.wallet.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (showCapture && w.wallet.captureSource != null) Icon(Icons.Default.Bolt, null, tint = Brand.Green, modifier = Modifier.height(13.dp))
                Text(
                    walletTypeLabel(w.wallet.type) + if (w.wallet.excludeFromTotal) " · " + stringResource(R.string.excluded) else "",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(Money.format(w.balance), fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}

private fun colorIndexFor(t: WalletType) = when (t) {
    WalletType.CASH -> 4
    WalletType.BANK -> 2
    WalletType.EWALLET -> 1
    WalletType.CREDIT_CARD -> 3
    WalletType.OTHER -> 10
}

// ---------- Edit ----------

data class WalletForm(
    val loaded: Boolean = false,
    val original: WalletEntity? = null,
    val name: String = "",
    val type: WalletType = WalletType.BANK,
    val digits: String = "",
    val negative: Boolean = false,
    val captureSource: String? = null,
    val excludeFromTotal: Boolean = false,
    val archived: Boolean = false,
    val error: Int? = null,
)

class WalletEditViewModel(private val c: AppContainer) : ViewModel() {
    private val _form = MutableStateFlow(WalletForm())
    val form = _form.asStateFlow()
    val settings = c.settings.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    fun load(id: Long) {
        if (_form.value.loaded) return
        viewModelScope.launch {
            val w = if (id > 0) c.wallets.get(id) else null
            _form.value = if (w == null) WalletForm(loaded = true) else WalletForm(
                loaded = true, original = w, name = w.name, type = w.type,
                digits = if (w.initialBalance == 0L) "" else kotlin.math.abs(w.initialBalance).toString(),
                negative = w.initialBalance < 0, captureSource = w.captureSource,
                excludeFromTotal = w.excludeFromTotal, archived = w.archived,
            )
        }
    }

    fun edit(change: (WalletForm) -> WalletForm) = _form.update { change(it).copy(error = null) }

    fun save(onDone: () -> Unit) {
        val f = _form.value
        if (f.name.isBlank()) { _form.update { it.copy(error = R.string.error_name) }; return }
        val balance = (f.digits.toLongOrNull() ?: 0L).let { if (f.negative) -it else it }
        viewModelScope.launch {
            val base = f.original ?: WalletEntity(name = f.name.trim(), type = f.type)
            c.wallets.save(
                base.copy(
                    name = f.name.trim(), type = f.type, initialBalance = balance, captureSource = f.captureSource,
                    excludeFromTotal = f.excludeFromTotal, archived = f.archived,
                )
            )
            onDone()
        }
    }

    fun delete(onDone: () -> Unit) = viewModelScope.launch {
        _form.value.original?.let { c.wallets.delete(it) }
        onDone()
    }
}

@Composable
fun WalletEditScreen(nav: NavController, id: Long) {
    val c = appContainer()
    val vm: WalletEditViewModel = viewModel { WalletEditViewModel(c) }
    LaunchedEffect(id) { vm.load(id) }
    val f by vm.form.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }

    ScreenScaffold(
        title = stringResource(if (id > 0) R.string.edit_wallet else R.string.add_wallet),
        onBack = { nav.popBackStack() },
        actions = { if (id > 0) IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, stringResource(R.string.delete)) } },
    ) { padding ->
        if (!f.loaded) return@ScreenScaffold
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            OutlinedTextField(
                value = f.name, onValueChange = { n -> vm.edit { it.copy(name = n) } },
                label = { Text(stringResource(R.string.name)) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            DropdownField(
                label = stringResource(R.string.wallet_type), options = WalletType.entries, selected = f.type,
                optionLabel = { walletTypeLabel(it) }, onSelect = { t -> vm.edit { it.copy(type = t) } },
            )
            AmountField(f.digits, { d -> vm.edit { it.copy(digits = d) } }, label = stringResource(R.string.starting_balance))
            SwitchRow(stringResource(R.string.negative_balance), stringResource(R.string.negative_balance_hint), f.negative) { v -> vm.edit { it.copy(negative = v) } }
            SwitchRow(stringResource(R.string.exclude_from_total), stringResource(R.string.exclude_from_total_hint), f.excludeFromTotal) { v -> vm.edit { it.copy(excludeFromTotal = v) } }

            // Feature-flagged: linking a wallet to a capture source only makes sense with auto-capture on.
            if (settings.autoCaptureEnabled) {
                DropdownField<String?>(
                    label = stringResource(R.string.capture_source),
                    options = listOf(null) + ParserRegistry.sources,
                    selected = f.captureSource,
                    optionLabel = { it ?: stringResource(R.string.capture_source_none) },
                    onSelect = { s -> vm.edit { it.copy(captureSource = s) } },
                    placeholder = stringResource(R.string.capture_source_none),
                )
                Text(stringResource(R.string.capture_source_hint), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            if (id > 0) {
                SwitchRow(stringResource(R.string.archive_wallet), stringResource(R.string.archive_wallet_hint), f.archived) { v -> vm.edit { it.copy(archived = v) } }
            }
            f.error?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error, fontSize = 13.sp) }
            Button(
                onClick = { vm.save { nav.popBackStack() } },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
            ) { Text(stringResource(R.string.save)) }
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = stringResource(R.string.delete_wallet_q),
            text = stringResource(R.string.delete_wallet_body),
            confirmLabel = stringResource(R.string.delete),
            onConfirm = { vm.delete { nav.popBackStack() } },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
fun SwitchRow(title: String, hint: String?, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            if (hint != null) Text(hint, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}
