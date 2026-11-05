@file:OptIn(ExperimentalLayoutApi::class)

package com.hiroshi.moneyapp.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.hiroshi.moneyapp.AppContainer
import com.hiroshi.moneyapp.R
import com.hiroshi.moneyapp.core.Periods
import com.hiroshi.moneyapp.core.Recurrence
import com.hiroshi.moneyapp.data.db.CategoryEntity
import com.hiroshi.moneyapp.data.db.CategoryType
import com.hiroshi.moneyapp.data.db.Frequency
import com.hiroshi.moneyapp.data.db.RecurringEntity
import com.hiroshi.moneyapp.data.db.TransactionEntity
import com.hiroshi.moneyapp.data.db.TxSource
import com.hiroshi.moneyapp.data.db.TxStatus
import com.hiroshi.moneyapp.data.db.TxType
import com.hiroshi.moneyapp.ui.Routes
import com.hiroshi.moneyapp.ui.categoryLabel
import com.hiroshi.moneyapp.ui.components.AmountField
import com.hiroshi.moneyapp.ui.components.CategoryBadge
import com.hiroshi.moneyapp.ui.components.ConfirmDialog
import com.hiroshi.moneyapp.ui.components.DateField
import com.hiroshi.moneyapp.ui.components.DropdownField
import com.hiroshi.moneyapp.ui.components.ScreenScaffold
import com.hiroshi.moneyapp.ui.components.SegmentedChoice
import com.hiroshi.moneyapp.ui.components.TimeField
import com.hiroshi.moneyapp.ui.components.appContainer
import com.hiroshi.moneyapp.ui.frequencyLabel
import com.hiroshi.moneyapp.ui.theme.Brand
import com.hiroshi.moneyapp.ui.txTypeLabel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

data class TxForm(
    val loaded: Boolean = false,
    val id: Long = 0,
    val type: TxType = TxType.EXPENSE,
    val digits: String = "",
    val walletId: Long? = null,
    val toWalletId: Long? = null,
    val categoryId: Long? = null,
    val date: LocalDate = LocalDate.now(),
    val time: LocalTime = LocalTime.now(),
    val note: String = "",
    val merchant: String = "",
    val excludeFromReport: Boolean = false,
    /** Only for new transactions: also create a recurring rule. */
    val repeat: Frequency? = null,
    val pending: Boolean = false,
    val rawText: String? = null,
    val original: TransactionEntity? = null,
    val error: Int? = null,
)

class TransactionEditViewModel(private val c: AppContainer) : ViewModel() {
    private val _form = MutableStateFlow(TxForm())
    val form = _form.asStateFlow()

    fun load(id: Long, initialType: String) {
        if (_form.value.loaded) return
        viewModelScope.launch {
            val tx = if (id > 0) c.transactions.get(id) else null
            _form.value = if (tx != null) {
                val at = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(tx.occurredAt), Periods.zone())
                TxForm(
                    loaded = true, id = tx.id, type = tx.type, digits = tx.amount.toString(),
                    walletId = tx.walletId, toWalletId = tx.toWalletId, categoryId = tx.categoryId,
                    date = at.toLocalDate(), time = at.toLocalTime(), note = tx.note, merchant = tx.merchant.orEmpty(),
                    excludeFromReport = tx.excludeFromReport, pending = tx.status == TxStatus.PENDING_REVIEW,
                    rawText = tx.rawText, original = tx,
                )
            } else {
                val type = TxType.entries.firstOrNull { it.name == initialType } ?: TxType.EXPENSE
                TxForm(loaded = true, type = type, walletId = c.db.walletDao().firstActive()?.id)
            }
        }
    }

    fun edit(change: (TxForm) -> TxForm) = _form.update { change(it).copy(error = null) }

    fun setType(type: TxType) = edit {
        // Categories are per type, so clear a category that no longer fits.
        it.copy(type = type, categoryId = if (type == it.type) it.categoryId else null)
    }

    fun save(onDone: () -> Unit) {
        val f = _form.value
        val amount = f.digits.toLongOrNull() ?: 0L
        val error = when {
            amount <= 0 -> R.string.error_amount
            f.walletId == null -> R.string.error_wallet
            f.type == TxType.TRANSFER && (f.toWalletId == null || f.toWalletId == f.walletId) -> R.string.error_transfer_wallet
            f.type != TxType.TRANSFER && f.categoryId == null -> R.string.error_category
            else -> null
        }
        if (error != null) { _form.update { it.copy(error = error) }; return }

        viewModelScope.launch {
            val zone = Periods.zone()
            val occurredAt = LocalDateTime.of(f.date, f.time).atZone(zone).toInstant().toEpochMilli()
            val base = f.original ?: TransactionEntity(type = f.type, amount = amount, walletId = f.walletId!!, occurredAt = occurredAt)
            var tx = base.copy(
                type = f.type,
                amount = amount,
                walletId = f.walletId!!,
                toWalletId = if (f.type == TxType.TRANSFER) f.toWalletId else null,
                categoryId = if (f.type == TxType.TRANSFER) null else f.categoryId,
                occurredAt = occurredAt,
                note = f.note.trim(),
                merchant = if (f.type == TxType.TRANSFER) null else f.merchant.trim().ifEmpty { null },
                excludeFromReport = f.type != TxType.TRANSFER && f.excludeFromReport,
                status = TxStatus.CONFIRMED, // saving a reviewed capture confirms it
            )

            val repeat = f.repeat
            if (f.id == 0L && repeat != null) {
                val next = Recurrence.next(f.date, f.date, repeat, 1)
                val ruleId = c.recurring.save(
                    RecurringEntity(
                        type = tx.type, amount = amount, walletId = tx.walletId, toWalletId = tx.toWalletId,
                        categoryId = tx.categoryId, note = tx.note, frequency = repeat,
                        startDate = Periods.startOfDay(f.date, zone), nextRunAt = Periods.startOfDay(next, zone),
                    )
                )
                tx = tx.copy(source = TxSource.RECURRING, recurringId = ruleId)
            }

            c.transactions.save(tx)
            if (f.pending || tx.source == TxSource.NOTIFICATION) c.transactions.learn(tx)
            onDone()
        }
    }

    fun delete(onDone: () -> Unit) = viewModelScope.launch {
        if (_form.value.id > 0) c.transactions.delete(_form.value.id)
        onDone()
    }
}

@Composable
fun TransactionEditScreen(nav: NavController, id: Long, initialType: String) {
    val c = appContainer()
    val vm: TransactionEditViewModel = viewModel { TransactionEditViewModel(c) }
    LaunchedEffect(id) { vm.load(id, initialType) }
    val f by vm.form.collectAsStateWithLifecycle()
    val categories by rememberCategories(c)
    val wallets by rememberWallets(c)
    var confirmDelete by remember { mutableStateOf(false) }

    ScreenScaffold(
        title = stringResource(if (id > 0) R.string.edit_transaction else R.string.new_transaction),
        onBack = { nav.popBackStack() },
        actions = {
            if (id > 0) IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, stringResource(R.string.delete)) }
        },
    ) { padding ->
        if (!f.loaded) return@ScreenScaffold
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (f.pending) {
                Surface(color = Brand.AmberSoft, shape = RoundedCornerShape(12.dp)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(R.string.pending_edit_hint), fontSize = 13.sp, color = Brand.AmberInk, fontWeight = FontWeight.SemiBold)
                        f.rawText?.let { Text(it, fontSize = 12.sp, color = Brand.AmberInk) }
                    }
                }
            }

            SegmentedChoice(TxType.entries, f.type, { txTypeLabel(it) }, vm::setType)
            AmountField(f.digits, { d -> vm.edit { it.copy(digits = d) } })

            if (f.type != TxType.TRANSFER) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.category), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    TextButton(onClick = { nav.navigate(Routes.CATEGORIES) }) { Text(stringResource(R.string.manage)) }
                }
                val wanted = if (f.type == TxType.INCOME) CategoryType.INCOME else CategoryType.EXPENSE
                CategoryGrid(
                    categories.filter { it.type == wanted && (!it.archived || it.id == f.categoryId) },
                    f.categoryId,
                ) { cat -> vm.edit { it.copy(categoryId = cat.id) } }
            }

            DropdownField(
                label = stringResource(if (f.type == TxType.TRANSFER) R.string.from_wallet else R.string.wallet),
                options = wallets,
                selected = wallets.firstOrNull { it.id == f.walletId },
                optionLabel = { it.name },
                onSelect = { w -> vm.edit { it.copy(walletId = w.id) } },
            )
            if (f.type == TxType.TRANSFER) {
                DropdownField(
                    label = stringResource(R.string.to_wallet),
                    options = wallets.filter { it.id != f.walletId },
                    selected = wallets.firstOrNull { it.id == f.toWalletId },
                    optionLabel = { it.name },
                    onSelect = { w -> vm.edit { it.copy(toWalletId = w.id) } },
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DateField(stringResource(R.string.date), f.date, { d -> vm.edit { it.copy(date = d) } }, Modifier.weight(1f))
                TimeField(stringResource(R.string.time), f.time, { t -> vm.edit { it.copy(time = t) } }, Modifier.weight(0.6f))
            }

            if (f.type != TxType.TRANSFER) {
                // Captures fill this in from the notification or statement; fixing it here also teaches the category.
                OutlinedTextField(
                    value = f.merchant,
                    onValueChange = { m -> vm.edit { it.copy(merchant = m) } },
                    label = { Text(stringResource(R.string.merchant)) },
                    placeholder = { Text(stringResource(R.string.merchant_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            OutlinedTextField(
                value = f.note,
                onValueChange = { n -> vm.edit { it.copy(note = n) } },
                label = { Text(stringResource(R.string.note)) },
                placeholder = { Text(stringResource(R.string.note_hint)) },
                modifier = Modifier.fillMaxWidth(),
            )

            if (f.type != TxType.TRANSFER) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.exclude_from_report), fontWeight = FontWeight.SemiBold)
                        Text(stringResource(R.string.exclude_from_report_hint), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = f.excludeFromReport, onCheckedChange = { v -> vm.edit { it.copy(excludeFromReport = v) } })
                }
            }

            if (id == 0L) {
                DropdownField<Frequency?>(
                    label = stringResource(R.string.repeat),
                    options = listOf(null) + Frequency.entries,
                    selected = f.repeat,
                    optionLabel = { fr -> if (fr == null) stringResource(R.string.repeat_never) else frequencyLabel(fr) },
                    onSelect = { fr -> vm.edit { it.copy(repeat = fr) } },
                    placeholder = stringResource(R.string.repeat_never),
                )
            }

            f.error?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error, fontSize = 13.sp) }

            Button(
                onClick = { vm.save { nav.popBackStack() } },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
            ) { Text(stringResource(if (f.pending) R.string.save_and_confirm else R.string.save)) }
            Row(Modifier.height(16.dp)) {}
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = stringResource(R.string.delete_transaction_q),
            text = stringResource(R.string.delete_cannot_undo),
            confirmLabel = stringResource(R.string.delete),
            onConfirm = { vm.delete { nav.popBackStack() } },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
fun CategoryGrid(categories: List<CategoryEntity>, selectedId: Long?, onPick: (CategoryEntity) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        categories.forEach { cat ->
            val selected = cat.id == selectedId
            val label = categoryLabel(cat)
            Surface(
                onClick = { onPick(cat) },
                shape = RoundedCornerShape(14.dp),
                color = Brand.Card,
                border = BorderStroke(2.dp, if (selected) Brand.Green else androidx.compose.ui.graphics.Color.Transparent),
                modifier = Modifier.width(84.dp),
            ) {
                Column(Modifier.padding(vertical = 8.dp, horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    CategoryBadge(label, cat.colorIndex, size = 36.dp)
                    Text(label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                }
            }
        }
    }
}
