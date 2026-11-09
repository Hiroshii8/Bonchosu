package com.hiroshi.bonchosu.ui.screens

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
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
import com.hiroshi.bonchosu.AppContainer
import com.hiroshi.bonchosu.R
import com.hiroshi.bonchosu.core.Periods
import com.hiroshi.bonchosu.data.db.CategoryType
import com.hiroshi.bonchosu.data.db.Frequency
import com.hiroshi.bonchosu.data.db.RecurringEntity
import com.hiroshi.bonchosu.data.db.TxType
import com.hiroshi.bonchosu.ui.Routes
import com.hiroshi.bonchosu.ui.categoryLabel
import com.hiroshi.bonchosu.ui.components.AmountField
import com.hiroshi.bonchosu.ui.components.ConfirmDialog
import com.hiroshi.bonchosu.ui.components.DateField
import com.hiroshi.bonchosu.ui.components.DropdownField
import com.hiroshi.bonchosu.ui.components.EmptyState
import com.hiroshi.bonchosu.ui.components.ScreenScaffold
import com.hiroshi.bonchosu.ui.components.SectionCard
import com.hiroshi.bonchosu.ui.components.SegmentedChoice
import com.hiroshi.bonchosu.ui.components.amountColor
import com.hiroshi.bonchosu.ui.components.appContainer
import com.hiroshi.bonchosu.ui.components.formatDate
import com.hiroshi.bonchosu.ui.components.signedAmount
import com.hiroshi.bonchosu.ui.frequencyLabel
import com.hiroshi.bonchosu.ui.theme.Brand
import com.hiroshi.bonchosu.ui.txTypeLabel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

// ---------- List ----------

class RecurringViewModel(private val c: AppContainer) : ViewModel() {
    val rules = c.recurring.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    fun setActive(rule: RecurringEntity, active: Boolean) = viewModelScope.launch { c.recurring.save(rule.copy(active = active)) }
}

@Composable
fun RecurringScreen(nav: NavController) {
    val c = appContainer()
    val vm: RecurringViewModel = viewModel { RecurringViewModel(c) }
    val rules by vm.rules.collectAsStateWithLifecycle()
    val wallets by rememberWallets(c)

    ScreenScaffold(
        title = stringResource(R.string.recurring),
        onBack = { nav.popBackStack() },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { nav.navigate(Routes.recurring()) },
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text(stringResource(R.string.add_recurring)) },
                containerColor = Brand.Hero, contentColor = Brand.OnHero,
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Text(stringResource(R.string.recurring_intro), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            if (rules.isEmpty()) item { EmptyState(stringResource(R.string.no_recurring)) }
            items(rules, key = { it.id }) { r ->
                SectionCard(Modifier.clickable { nav.navigate(Routes.recurring(r.id)) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(r.note.ifBlank { rememberCategoryName(c, r.categoryId).takeIf { r.type != TxType.TRANSFER } ?: txTypeLabel(r.type) },
                                fontWeight = FontWeight.Bold)
                            val walletName = wallets.firstOrNull { it.id == r.walletId }?.name.orEmpty()
                            Text(
                                frequencyLabel(r.frequency) + " · " + walletName,
                                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                if (r.active) stringResource(R.string.next_on, formatDate(Periods.toDate(r.nextRunAt))) else stringResource(R.string.paused),
                                fontSize = 12.sp, color = if (r.active) Brand.Accent else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(signedAmount(r.type, r.amount), fontWeight = FontWeight.Bold, color = amountColor(r.type))
                            Switch(checked = r.active, onCheckedChange = { vm.setActive(r, it) })
                        }
                    }
                }
            }
        }
    }
}

// ---------- Edit ----------

data class RecurringForm(
    val loaded: Boolean = false,
    val original: RecurringEntity? = null,
    val type: TxType = TxType.EXPENSE,
    val digits: String = "",
    val walletId: Long? = null,
    val toWalletId: Long? = null,
    val categoryId: Long? = null,
    val note: String = "",
    val frequency: Frequency = Frequency.MONTHLY,
    val everyN: String = "1",
    val start: LocalDate = LocalDate.now(),
    val end: LocalDate? = null,
    val error: Int? = null,
)

class RecurringEditViewModel(private val c: AppContainer) : ViewModel() {
    private val _form = MutableStateFlow(RecurringForm())
    val form = _form.asStateFlow()

    fun load(id: Long) {
        if (_form.value.loaded) return
        viewModelScope.launch {
            val r = if (id > 0) c.recurring.get(id) else null
            _form.value = if (r == null) RecurringForm(loaded = true, walletId = c.db.walletDao().firstActive()?.id)
            else RecurringForm(
                loaded = true, original = r, type = r.type, digits = r.amount.toString(), walletId = r.walletId,
                toWalletId = r.toWalletId, categoryId = r.categoryId, note = r.note, frequency = r.frequency,
                everyN = r.everyN.toString(), start = Periods.toDate(r.startDate), end = r.endDate?.let { Periods.toDate(it) },
            )
        }
    }

    fun edit(change: (RecurringForm) -> RecurringForm) = _form.update { change(it).copy(error = null) }

    fun save(onDone: () -> Unit) {
        val f = _form.value
        val amount = f.digits.toLongOrNull() ?: 0
        val error = when {
            amount <= 0 -> R.string.error_amount
            f.walletId == null -> R.string.error_wallet
            f.type == TxType.TRANSFER && (f.toWalletId == null || f.toWalletId == f.walletId) -> R.string.error_transfer_wallet
            f.type != TxType.TRANSFER && f.categoryId == null -> R.string.error_category
            f.end != null && f.end.isBefore(f.start) -> R.string.error_end_date
            else -> null
        }
        if (error != null) { _form.update { it.copy(error = error) }; return }
        viewModelScope.launch {
            val startMs = Periods.startOfDay(f.start)
            val o = f.original
            // Changing the start date re-plans the schedule from it; otherwise keep where the rule is.
            val nextRunAt = if (o == null || o.startDate != startMs) startMs else o.nextRunAt
            c.recurring.save(
                (o ?: RecurringEntity(type = f.type, amount = amount, walletId = f.walletId!!, frequency = f.frequency, startDate = startMs, nextRunAt = nextRunAt))
                    .copy(
                        type = f.type, amount = amount, walletId = f.walletId!!,
                        toWalletId = if (f.type == TxType.TRANSFER) f.toWalletId else null,
                        categoryId = if (f.type == TxType.TRANSFER) null else f.categoryId,
                        note = f.note.trim(), frequency = f.frequency, everyN = f.everyN.toIntOrNull()?.coerceIn(1, 365) ?: 1,
                        startDate = startMs, nextRunAt = nextRunAt, endDate = f.end?.let { Periods.endOfDay(it) },
                        active = o?.active ?: true,
                    )
            )
            c.recurring.runDue() // a start date of today (or earlier) creates its transaction right away
            onDone()
        }
    }

    fun delete(onDone: () -> Unit) = viewModelScope.launch {
        _form.value.original?.let { c.recurring.delete(it.id) }
        onDone()
    }
}

@Composable
fun RecurringEditScreen(nav: NavController, id: Long) {
    val c = appContainer()
    val vm: RecurringEditViewModel = viewModel { RecurringEditViewModel(c) }
    LaunchedEffect(id) { vm.load(id) }
    val f by vm.form.collectAsStateWithLifecycle()
    val categories by rememberCategories(c)
    val wallets by rememberWallets(c)
    var confirmDelete by remember { mutableStateOf(false) }

    ScreenScaffold(
        title = stringResource(if (id > 0) R.string.edit_recurring else R.string.add_recurring),
        onBack = { nav.popBackStack() },
        actions = { if (id > 0) IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, stringResource(R.string.delete)) } },
    ) { padding ->
        if (!f.loaded) return@ScreenScaffold
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SegmentedChoice(TxType.entries, f.type, { txTypeLabel(it) }, { t -> vm.edit { it.copy(type = t, categoryId = null) } })
            AmountField(f.digits, { d -> vm.edit { it.copy(digits = d) } })
            if (f.type != TxType.TRANSFER) {
                val wanted = if (f.type == TxType.INCOME) CategoryType.INCOME else CategoryType.EXPENSE
                val options = categories.filter { it.type == wanted && !it.archived }
                DropdownField(
                    label = stringResource(R.string.category), options = options,
                    selected = categories.firstOrNull { it.id == f.categoryId },
                    optionLabel = { categoryLabel(it) }, onSelect = { cat -> vm.edit { it.copy(categoryId = cat.id) } },
                )
            }
            DropdownField(
                label = stringResource(if (f.type == TxType.TRANSFER) R.string.from_wallet else R.string.wallet),
                options = wallets, selected = wallets.firstOrNull { it.id == f.walletId },
                optionLabel = { it.name }, onSelect = { w -> vm.edit { it.copy(walletId = w.id) } },
            )
            if (f.type == TxType.TRANSFER) {
                DropdownField(
                    label = stringResource(R.string.to_wallet), options = wallets.filter { it.id != f.walletId },
                    selected = wallets.firstOrNull { it.id == f.toWalletId },
                    optionLabel = { it.name }, onSelect = { w -> vm.edit { it.copy(toWalletId = w.id) } },
                )
            }
            OutlinedTextField(
                value = f.note, onValueChange = { n -> vm.edit { it.copy(note = n) } },
                label = { Text(stringResource(R.string.note)) }, placeholder = { Text(stringResource(R.string.recurring_note_hint)) },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = f.everyN, onValueChange = { v -> vm.edit { it.copy(everyN = v.filter(Char::isDigit).take(3)) } },
                    label = { Text(stringResource(R.string.every)) }, singleLine = true, modifier = Modifier.weight(0.35f),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                )
                DropdownField(
                    label = stringResource(R.string.frequency), options = Frequency.entries, selected = f.frequency,
                    optionLabel = { frequencyLabel(it) }, onSelect = { fr -> vm.edit { it.copy(frequency = fr) } },
                    modifier = Modifier.weight(0.65f),
                )
            }
            DateField(stringResource(R.string.start_date), f.start, { d -> vm.edit { it.copy(start = d) } })
            SwitchRow(stringResource(R.string.has_end_date), null, f.end != null) { on ->
                vm.edit { it.copy(end = if (on) it.start.plusMonths(12) else null) }
            }
            f.end?.let { end -> DateField(stringResource(R.string.end_date), end, { d -> vm.edit { it.copy(end = d) } }) }

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
            title = stringResource(R.string.delete_recurring_q),
            text = stringResource(R.string.delete_recurring_body),
            confirmLabel = stringResource(R.string.delete),
            onConfirm = { vm.delete { nav.popBackStack() } },
            onDismiss = { confirmDelete = false },
        )
    }
}
