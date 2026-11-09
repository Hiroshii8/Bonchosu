@file:OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)

package com.hiroshi.bonchosu.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.outlined.SubdirectoryArrowRight
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.hiroshi.bonchosu.core.Money
import com.hiroshi.bonchosu.data.db.WalletEntity
import com.hiroshi.bonchosu.ui.components.DatePickerPopup
import com.hiroshi.bonchosu.ui.components.PrimaryButton
import com.hiroshi.bonchosu.ui.components.RoundIconButton
import com.hiroshi.bonchosu.ui.components.TimePickerPopup
import com.hiroshi.bonchosu.ui.components.currentLocale
import com.hiroshi.bonchosu.ui.theme.AmountStyle
import com.hiroshi.bonchosu.ui.theme.CardRadius
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
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
import com.hiroshi.bonchosu.AppContainer
import com.hiroshi.bonchosu.R
import com.hiroshi.bonchosu.core.Periods
import com.hiroshi.bonchosu.core.Recurrence
import com.hiroshi.bonchosu.data.db.CategoryEntity
import com.hiroshi.bonchosu.data.db.CategoryType
import com.hiroshi.bonchosu.data.db.Frequency
import com.hiroshi.bonchosu.data.db.RecurringEntity
import com.hiroshi.bonchosu.data.db.TransactionEntity
import com.hiroshi.bonchosu.data.db.TxSource
import com.hiroshi.bonchosu.data.db.TxStatus
import com.hiroshi.bonchosu.data.db.TxType
import com.hiroshi.bonchosu.ui.Routes
import com.hiroshi.bonchosu.ui.categoryLabel
import com.hiroshi.bonchosu.ui.components.AmountField
import com.hiroshi.bonchosu.ui.components.CategoryBadge
import com.hiroshi.bonchosu.ui.components.ConfirmDialog
import com.hiroshi.bonchosu.ui.components.DateField
import com.hiroshi.bonchosu.ui.components.DropdownField
import com.hiroshi.bonchosu.ui.components.ScreenScaffold
import com.hiroshi.bonchosu.ui.components.SegmentedChoice
import com.hiroshi.bonchosu.ui.components.TimeField
import com.hiroshi.bonchosu.ui.components.appContainer
import com.hiroshi.bonchosu.ui.frequencyLabel
import com.hiroshi.bonchosu.ui.theme.Brand
import com.hiroshi.bonchosu.ui.txTypeLabel
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
    var pickDate by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }
    // The number pad gives way to the phone's keyboard while you type a note or merchant.
    val typing = WindowInsets.isImeVisible

    Column(Modifier.fillMaxSize().background(Brand.Bg).statusBarsPadding().navigationBarsPadding().imePadding()) {
        // Top bar: close, centred title, delete when editing.
        Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
            RoundIconButton(Icons.Default.Close, stringResource(R.string.cancel), { nav.popBackStack() }, Modifier.align(Alignment.CenterStart))
            Text(
                stringResource(if (id > 0) R.string.edit_transaction else R.string.new_transaction),
                style = MaterialTheme.typography.titleLarge, modifier = Modifier.align(Alignment.Center),
            )
            if (id > 0) {
                RoundIconButton(Icons.Outlined.Delete, stringResource(R.string.delete), { confirmDelete = true }, Modifier.align(Alignment.CenterEnd))
            }
        }
        if (!f.loaded) return@Column

        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (f.pending) {
                Surface(color = Brand.AmberSoft, shape = RoundedCornerShape(16.dp)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(R.string.pending_edit_hint), fontSize = 13.sp, color = Brand.AmberInk, fontWeight = FontWeight.SemiBold)
                        f.rawText?.let { Text(it, fontSize = 12.sp, color = Brand.AmberInk) }
                    }
                }
            }

            SegmentedChoice(TxType.entries, f.type, { txTypeLabel(it) }, vm::setType)

            AmountDisplay(f)

            if (f.type != TxType.TRANSFER) {
                val wanted = if (f.type == TxType.INCOME) CategoryType.INCOME else CategoryType.EXPENSE
                CategoryStrip(
                    categories.filter { it.type == wanted && (!it.archived || it.id == f.categoryId) },
                    f.categoryId,
                    onPick = { cat -> vm.edit { it.copy(categoryId = cat.id) } },
                    onManage = { nav.navigate(Routes.CATEGORIES) },
                )
            }

            DetailsCard {
                WalletRow(
                    Icons.Outlined.AccountBalanceWallet,
                    stringResource(if (f.type == TxType.TRANSFER) R.string.from_wallet else R.string.wallet),
                    wallets, f.walletId,
                ) { w -> vm.edit { it.copy(walletId = w.id) } }
                if (f.type == TxType.TRANSFER) {
                    DetailDivider()
                    WalletRow(Icons.Outlined.SubdirectoryArrowRight, stringResource(R.string.to_wallet), wallets.filter { it.id != f.walletId }, f.toWalletId) { w ->
                        vm.edit { it.copy(toWalletId = w.id) }
                    }
                }
                DetailDivider()
                DetailRow(Icons.Outlined.CalendarToday, stringResource(R.string.date), onClick = { pickDate = true }) {
                    DetailValue(dateLabel(f.date))
                }
                DetailDivider()
                DetailRow(Icons.Outlined.Schedule, stringResource(R.string.time), onClick = { pickTime = true }) {
                    DetailValue(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(currentLocale()).format(f.time))
                }
                if (f.type != TxType.TRANSFER) {
                    DetailDivider()
                    // Captures fill this in from the notification or statement; fixing it here also teaches the category.
                    DetailRow(Icons.Outlined.Storefront, stringResource(R.string.merchant)) {
                        InlineInput(f.merchant, stringResource(R.string.optional)) { m -> vm.edit { it.copy(merchant = m) } }
                    }
                }
                DetailDivider()
                DetailRow(Icons.Outlined.Edit, stringResource(R.string.note)) {
                    InlineInput(f.note, stringResource(R.string.optional)) { n -> vm.edit { it.copy(note = n) } }
                }
                if (id == 0L) {
                    DetailDivider()
                    RepeatRow(f.repeat) { fr -> vm.edit { it.copy(repeat = fr) } }
                }
                if (f.type != TxType.TRANSFER) {
                    DetailDivider()
                    DetailRow(Icons.Outlined.VisibilityOff, stringResource(R.string.exclude_from_report)) {
                        Switch(checked = f.excludeFromReport, onCheckedChange = { v -> vm.edit { it.copy(excludeFromReport = v) } })
                    }
                }
            }

            f.error?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
            Spacer(Modifier.height(4.dp))
        }

        Column(Modifier.padding(horizontal = 20.dp).padding(top = 8.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (!typing) Keypad { key -> vm.edit { it.copy(digits = Keypad.apply(it.digits, key)) } }
            PrimaryButton(
                stringResource(
                    when {
                        f.pending -> R.string.save_and_confirm
                        f.type == TxType.INCOME -> R.string.save_income
                        f.type == TxType.TRANSFER -> R.string.save_transfer
                        else -> R.string.save_expense
                    }
                ),
                onClick = { vm.save { nav.popBackStack() } },
            )
        }
    }

    if (pickDate) DatePickerPopup(f.date, { d -> vm.edit { it.copy(date = d) } }) { pickDate = false }
    if (pickTime) TimePickerPopup(f.time, { t -> vm.edit { it.copy(time = t) } }) { pickTime = false }
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

/** "Amount" over a big "−Rp 45.000" (sign by type, so meaning doesn't rely on colour). */
@Composable
private fun AmountDisplay(f: TxForm) {
    val amount = f.digits.toLongOrNull() ?: 0L
    val text = when (f.type) {
        TxType.EXPENSE -> Money.format(-amount).takeIf { amount > 0 } ?: Money.format(0)
        TxType.INCOME -> "+" + Money.format(amount)
        TxType.TRANSFER -> Money.format(amount)
    }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(stringResource(R.string.amount), color = Brand.Muted, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Text(
            text, style = AmountStyle.copy(fontSize = if (text.length > 14) 40.sp else 50.sp, letterSpacing = (-1.5).sp),
            color = when {
                amount == 0L -> Brand.Muted
                f.type == TxType.INCOME -> Brand.Income
                else -> Brand.Text
            },
            maxLines = 1,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
}

/** Horizontal row of category bubbles; the chosen one gets an accent ring. */
@Composable
private fun CategoryStrip(categories: List<CategoryEntity>, selectedId: Long?, onPick: (CategoryEntity) -> Unit, onManage: () -> Unit) {
    val listState = rememberLazyListState()
    LaunchedEffect(Unit) {
        val i = categories.indexOfFirst { it.id == selectedId }
        if (i > 0) listState.scrollToItem(i)
    }
    LazyRow(
        state = listState,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxWidth().selectableGroup(),
    ) {
        items(categories, key = { it.id }) { cat ->
            val selected = cat.id == selectedId
            val label = categoryLabel(cat)
            Column(
                Modifier.width(72.dp).clip(RoundedCornerShape(16.dp))
                    .selectable(selected = selected, onClick = { onPick(cat) }, role = Role.RadioButton),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(
                    Modifier.size(64.dp)
                        .border(BorderStroke(3.dp, if (selected) Brand.Accent else Color.Transparent), RoundedCornerShape(20.dp))
                        .padding(4.dp),
                    contentAlignment = Alignment.Center,
                ) { CategoryBadge(label, cat.colorIndex, size = 54.dp) }
                Text(
                    label, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Medium,
                    color = if (selected) Brand.Text else Brand.Muted,
                )
            }
        }
        item {
            Column(
                Modifier.width(72.dp).clip(RoundedCornerShape(16.dp)).clickable(onClick = onManage),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(
                    Modifier.size(64.dp).padding(4.dp).clip(RoundedCornerShape(17.dp)).background(Brand.Surface2),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Default.Add, null, tint = Brand.Muted) }
                Text(stringResource(R.string.manage), fontSize = 13.sp, color = Brand.Muted, maxLines = 1)
            }
        }
    }
}

@Composable
private fun DetailsCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(CardRadius), color = Brand.Surface, border = BorderStroke(1.dp, Brand.Border),
        modifier = Modifier.fillMaxWidth(),
    ) { Column(Modifier.padding(horizontal = 18.dp), content = content) }
}

@Composable
private fun DetailDivider() = HorizontalDivider(color = Brand.Border)

/** Icon, label, and the value (or an input) on the right. */
@Composable
private fun DetailRow(icon: ImageVector, label: String, onClick: (() -> Unit)? = null, value: @Composable RowScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 60.dp).then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = Brand.Muted, modifier = Modifier.size(24.dp))
        Text(label, color = Brand.Muted, fontSize = 16.sp, modifier = Modifier.padding(start = 14.dp, end = 12.dp))
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically, content = value)
    }
}

@Composable
private fun DetailValue(text: String) {
    Text(text, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.End)
}

/** A borderless, right-aligned text input for the details card. */
@Composable
private fun InlineInput(value: String, placeholder: String, onChange: (String) -> Unit) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        if (value.isEmpty()) Text(placeholder, color = Brand.Muted, fontSize = 16.sp, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth())
        BasicTextField(
            value = value, onValueChange = onChange, singleLine = true,
            textStyle = TextStyle(color = Brand.Text, fontSize = 16.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End),
            cursorBrush = SolidColor(Brand.Accent),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
        )
    }
}

@Composable
private fun WalletRow(icon: ImageVector, label: String, wallets: List<WalletEntity>, selectedId: Long?, onPick: (WalletEntity) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        DetailRow(icon, label, onClick = { open = true }) {
            DetailValue(wallets.firstOrNull { it.id == selectedId }?.name ?: stringResource(R.string.choose))
            Icon(Icons.Default.KeyboardArrowDown, null, tint = Brand.Muted, modifier = Modifier.padding(start = 4.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, modifier = Modifier.align(Alignment.CenterEnd)) {
            wallets.forEach { w ->
                DropdownMenuItem(text = { Text(w.name, fontWeight = if (w.id == selectedId) FontWeight.ExtraBold else FontWeight.Normal) }, onClick = { onPick(w); open = false })
            }
        }
    }
}

@Composable
private fun RepeatRow(repeat: Frequency?, onPick: (Frequency?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        DetailRow(Icons.Outlined.Repeat, stringResource(R.string.repeat), onClick = { open = true }) {
            DetailValue(repeat?.let { frequencyLabel(it) } ?: stringResource(R.string.repeat_never))
            Icon(Icons.Default.KeyboardArrowDown, null, tint = Brand.Muted, modifier = Modifier.padding(start = 4.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, modifier = Modifier.align(Alignment.CenterEnd)) {
            (listOf(null) + Frequency.entries).forEach { fr ->
                DropdownMenuItem(
                    text = { Text(if (fr == null) stringResource(R.string.repeat_never) else frequencyLabel(fr)) },
                    onClick = { onPick(fr); open = false },
                )
            }
        }
    }
}

/** "Today, 8 Nov", "Yesterday, 7 Nov", or "6 Nov 2025". */
@Composable
private fun dateLabel(date: LocalDate): String {
    val today = LocalDate.now()
    val short = DateTimeFormatter.ofPattern(if (date.year == today.year) "d MMM" else "d MMM yyyy", currentLocale()).format(date)
    return when (date) {
        today -> stringResource(R.string.day_today) + ", " + short
        today.minusDays(1) -> stringResource(R.string.day_yesterday) + ", " + short
        else -> short
    }
}

/** Calculator-style number pad: 1–9, 000, 0, backspace (hold to clear). */
@Composable
private fun Keypad(onKey: (String) -> Unit) {
    val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "000", "0", Keypad.BACK)
    val haptics = LocalHapticFeedback.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        keys.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { key ->
                    val back = key == Keypad.BACK
                    Box(
                        Modifier.weight(1f).height(58.dp).clip(RoundedCornerShape(18.dp)).background(Brand.Surface)
                            .combinedClickable(
                                onClickLabel = null,
                                onLongClick = if (back) ({ haptics.performHapticFeedback(HapticFeedbackType.LongPress); onKey(Keypad.CLEAR) }) else null,
                                onClick = { onKey(key) },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (back) Icon(Icons.AutoMirrored.Outlined.Backspace, stringResource(R.string.delete_digit), tint = Brand.Text)
                        else Text(key, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Brand.Text)
                    }
                }
            }
        }
    }
}

/** The number pad's arithmetic, kept apart from the UI. */
object Keypad {
    const val BACK = "back"
    const val CLEAR = "clear"
    private const val MAX_DIGITS = 13

    fun apply(digits: String, key: String): String = when (key) {
        BACK -> digits.dropLast(1)
        CLEAR -> ""
        else -> (digits + key).trimStart('0').take(MAX_DIGITS)
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
                color = Brand.Surface,
                border = BorderStroke(2.dp, if (selected) Brand.Accent else androidx.compose.ui.graphics.Color.Transparent),
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
