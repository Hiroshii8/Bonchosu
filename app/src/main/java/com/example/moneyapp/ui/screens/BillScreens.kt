package com.hiroshi.moneyapp.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.hiroshi.moneyapp.AppContainer
import com.hiroshi.moneyapp.R
import com.hiroshi.moneyapp.bill.ReceiptParser
import com.hiroshi.moneyapp.core.BillSplit
import com.hiroshi.moneyapp.core.Money
import com.hiroshi.moneyapp.core.Periods
import com.hiroshi.moneyapp.data.BillTotals
import com.hiroshi.moneyapp.data.NewBill
import com.hiroshi.moneyapp.data.NewCharge
import com.hiroshi.moneyapp.data.NewItem
import com.hiroshi.moneyapp.data.NewPerson
import com.hiroshi.moneyapp.data.db.BillChargeEntity
import com.hiroshi.moneyapp.data.db.BillEntity
import com.hiroshi.moneyapp.data.db.BillItemEntity
import com.hiroshi.moneyapp.data.db.BillItemShareEntity
import com.hiroshi.moneyapp.data.db.BillPersonEntity
import com.hiroshi.moneyapp.data.db.CategoryType
import com.hiroshi.moneyapp.ui.Routes
import com.hiroshi.moneyapp.ui.categoryLabel
import com.hiroshi.moneyapp.ui.components.ConfirmDialog
import com.hiroshi.moneyapp.ui.components.DateField
import com.hiroshi.moneyapp.ui.components.DropdownField
import com.hiroshi.moneyapp.ui.components.EmptyState
import com.hiroshi.moneyapp.ui.components.ScreenScaffold
import com.hiroshi.moneyapp.ui.components.SectionCard
import com.hiroshi.moneyapp.ui.components.SectionTitle
import com.hiroshi.moneyapp.ui.components.ThousandsTransformation
import com.hiroshi.moneyapp.ui.components.appContainer
import com.hiroshi.moneyapp.ui.components.formatDate
import com.hiroshi.moneyapp.ui.theme.Brand
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate

// ---------- Saved bills ----------

@Composable
fun BillsScreen(nav: NavController) {
    val c = appContainer()
    val flow = remember(c) { c.bills.observeAll() }
    val rows by flow.collectAsStateWithLifecycle(emptyList())

    ScreenScaffold(
        title = stringResource(R.string.bill_title),
        onBack = { nav.popBackStack() },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { nav.navigate(Routes.BILL_NEW) },
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text(stringResource(R.string.bill_new)) },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (rows.isEmpty()) item { EmptyState(stringResource(R.string.bill_empty)) }
            items(rows, key = { it.bill.id }) { row ->
                SectionCard(modifier = Modifier.clickable { nav.navigate(Routes.bill(row.bill.id)) }) {
                    Row {
                        Text(row.bill.merchant, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Text(Money.format(row.bill.total), fontWeight = FontWeight.Bold)
                    }
                    Text(
                        formatDate(Periods.toDate(row.bill.occurredAt)) + " · " + stringResource(R.string.bill_yours, Money.format(row.bill.myShare)),
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (row.outstanding > 0) {
                        val open = stringResource(if (row.bill.paidByMe) R.string.bill_owed_to_you else R.string.bill_you_owe, Money.format(row.outstanding))
                        Text(open, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Brand.AmberInk)
                    }
                }
            }
        }
    }
}

// ---------- New bill ----------

enum class BillStep { RECEIPT, PEOPLE, ASSIGN, SUMMARY }

data class DraftItem(
    val key: Long,
    val name: String = "",
    val quantity: Int = 1,
    val digits: String = "",
    /** Person key → portions eaten. */
    val units: Map<Long, Int> = emptyMap(),
    /** People whose portion of this order is flagged as mine. */
    val mine: Set<Long> = emptySet(),
) {
    val amount get() = digits.toLongOrNull() ?: 0L
    val assigned get() = units.values.any { it > 0 }
}

data class DraftCharge(val key: Long, val name: String = "", val digits: String = "", val discount: Boolean = false, val equally: Boolean = false) {
    val amount get() = (digits.toLongOrNull() ?: 0L).let { if (discount) -it else it }
}

data class DraftPerson(val key: Long, val name: String)

data class BillDraft(
    val step: BillStep = BillStep.RECEIPT,
    val scanning: Boolean = false,
    val scanFailed: Boolean = false,
    val scanned: Boolean = false,
    val taxIncluded: Boolean = false,
    val merchant: String = "",
    val date: LocalDate = LocalDate.now(),
    val items: List<DraftItem> = emptyList(),
    val charges: List<DraftCharge> = emptyList(),
    val printedTotal: Long? = null,
    val people: List<DraftPerson> = emptyList(),
    /** Null = I paid the whole bill. */
    val payerKey: Long? = null,
    val walletId: Long? = null,
    val categoryId: Long? = null,
    val similarExpense: Boolean = false,
    val savedId: Long? = null,
) {
    val validItems get() = items.filter { it.amount > 0 && it.name.isNotBlank() }
    val validCharges get() = charges.filter { it.amount != 0L }
    val computedTotal get() = validItems.sumOf { it.amount } + validCharges.sumOf { it.amount }

    fun newItems() = validItems.map { item ->
        NewItem(item.name.trim(), item.quantity, item.amount, item.units.filterValues { it > 0 }, item.mine.filter { (item.units[it] ?: 0) > 0 }.toSet())
    }
    fun newCharges() = validCharges.map { NewCharge(it.name.trim().ifEmpty { "-" }, it.amount, it.equally) }
    fun totals() = BillTotals.of(people.map { it.key }, newItems(), newCharges())
}

class BillEditViewModel(private val c: AppContainer) : ViewModel() {
    val draft = MutableStateFlow(BillDraft())
    private var nextKey = 1L

    init {
        viewModelScope.launch {
            val wallet = c.wallets.observeActive().first().firstOrNull()
            val food = c.categories.observeAll().first().firstOrNull { it.builtinKey == "food" && !it.archived }
            draft.update { it.copy(walletId = wallet?.id, categoryId = food?.id) }
        }
    }

    private fun key() = nextKey++

    fun edit(change: (BillDraft) -> BillDraft) = draft.update(change)
    fun editItem(key: Long, change: (DraftItem) -> DraftItem) =
        draft.update { d -> d.copy(items = d.items.map { if (it.key == key) change(it) else it }) }
    fun editCharge(key: Long, change: (DraftCharge) -> DraftCharge) =
        draft.update { d -> d.copy(charges = d.charges.map { if (it.key == key) change(it) else it }) }
    fun addItem() = draft.update { it.copy(items = it.items + DraftItem(key())) }
    fun removeItem(key: Long) = draft.update { d -> d.copy(items = d.items.filter { it.key != key }) }
    fun addCharge() = draft.update { it.copy(charges = it.charges + DraftCharge(key())) }
    fun removeCharge(key: Long) = draft.update { d -> d.copy(charges = d.charges.filter { it.key != key }) }

    fun scan(uri: Uri, deleteAfter: File? = null) {
        draft.update { it.copy(scanning = true, scanFailed = false) }
        viewModelScope.launch {
            try {
                val receipt = withContext(Dispatchers.IO) { ReceiptParser.parse(c.ocr.readImage(uri)) }
                draft.update { d ->
                    d.copy(
                        scanning = false,
                        scanned = true,
                        scanFailed = receipt.items.isEmpty(),
                        taxIncluded = receipt.taxIncluded,
                        merchant = receipt.merchant ?: d.merchant,
                        date = receipt.date ?: d.date,
                        printedTotal = receipt.printedTotal,
                        items = receipt.items.map { DraftItem(key(), it.name, it.quantity, it.lineTotal.toString()) },
                        charges = receipt.charges.map {
                            DraftCharge(key(), it.name, kotlin.math.abs(it.amount).toString(), discount = it.amount < 0, equally = it.equally)
                        },
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Deliberately not logged: the failure could quote the receipt.
                draft.update { it.copy(scanning = false, scanFailed = true) }
            } finally {
                deleteAfter?.delete()
            }
        }
    }

    /** Sets how many people are on the bill. Lowering it drops the last people and their assignments. */
    fun setPeopleCount(count: Int, nameFor: (Int) -> String) = draft.update { d ->
        val n = count.coerceIn(1, MAX_PEOPLE)
        val people = d.people.take(n) + (d.people.size until n).map { DraftPerson(key(), nameFor(it + 1)) }
        val keys = people.map { it.key }.toSet()
        d.copy(
            people = people,
            payerKey = d.payerKey?.takeIf { it in keys },
            items = d.items.map { i -> i.copy(units = i.units.filterKeys { it in keys }, mine = i.mine.filter { it in keys }.toSet()) },
        )
    }

    fun renamePerson(key: Long, name: String) = draft.update { d ->
        d.copy(people = d.people.map { if (it.key == key) it.copy(name = name) else it })
    }

    /** Tapping steps through the portions: a single order toggles, 3 portions go 1, 2, 3, then off. */
    fun tapAssign(itemKey: Long, personKey: Long) = editItem(itemKey) { item ->
        val next = ((item.units[personKey] ?: 0) + 1).let { if (it > item.quantity) 0 else it }
        item.copy(units = item.units + (personKey to next), mine = if (next == 0) item.mine - personKey else item.mine)
    }

    fun assignToEveryone(itemKey: Long) = editItem(itemKey) { item ->
        item.copy(units = draft.value.people.associate { it.key to 1 })
    }

    fun toggleMine(itemKey: Long, personKey: Long) = editItem(itemKey) { item ->
        item.copy(mine = if (personKey in item.mine) item.mine - personKey else item.mine + personKey)
    }

    /** Flags everything this person had as mine, or clears it if it already was. */
    fun togglePersonMine(personKey: Long) = draft.update { d ->
        val theirs = d.items.filter { (it.units[personKey] ?: 0) > 0 }
        val allMine = theirs.all { personKey in it.mine }
        d.copy(items = d.items.map { i ->
            if ((i.units[personKey] ?: 0) <= 0) i
            else i.copy(mine = if (allMine) i.mine - personKey else i.mine + personKey)
        })
    }

    fun goTo(step: BillStep) {
        draft.update { it.copy(step = step) }
        if (step == BillStep.SUMMARY) checkSimilar()
    }

    fun setWallet(walletId: Long) {
        draft.update { it.copy(walletId = walletId) }
        checkSimilar()
    }

    private fun checkSimilar() = viewModelScope.launch {
        val d = draft.value
        val wallet = d.walletId ?: return@launch
        val similar = c.bills.hasSimilarExpense(wallet, d.computedTotal, d.date)
        draft.update { it.copy(similarExpense = similar) }
    }

    fun save(defaultMerchant: String) = viewModelScope.launch {
        val d = draft.value
        val wallet = d.walletId ?: return@launch
        val id = c.bills.save(
            NewBill(
                merchant = d.merchant.trim().ifEmpty { defaultMerchant },
                date = d.date,
                walletId = wallet,
                categoryId = d.categoryId,
                people = d.people.map { NewPerson(it.key, it.name.trim().ifEmpty { "-" }, isPayer = it.key == d.payerKey) },
                items = d.newItems(),
                charges = d.newCharges(),
            )
        )
        draft.update { it.copy(savedId = id) }
    }

    private companion object {
        const val MAX_PEOPLE = 30
    }
}

@Composable
fun BillEditScreen(nav: NavController) {
    val c = appContainer()
    val vm: BillEditViewModel = viewModel { BillEditViewModel(c) }
    val d by vm.draft.collectAsStateWithLifecycle()

    LaunchedEffect(d.savedId) {
        d.savedId?.let { id -> nav.navigate(Routes.bill(id)) { popUpTo(Routes.BILL_NEW) { inclusive = true } } }
    }
    val back = {
        when (d.step) {
            BillStep.RECEIPT -> nav.popBackStack()
            BillStep.PEOPLE -> { vm.goTo(BillStep.RECEIPT); true }
            BillStep.ASSIGN -> { vm.goTo(BillStep.PEOPLE); true }
            BillStep.SUMMARY -> { vm.goTo(BillStep.ASSIGN); true }
        }
        Unit
    }
    BackHandler(onBack = back)

    val title = stringResource(
        when (d.step) {
            BillStep.RECEIPT -> R.string.bill_step_receipt
            BillStep.PEOPLE -> R.string.bill_step_people
            BillStep.ASSIGN -> R.string.bill_step_assign
            BillStep.SUMMARY -> R.string.bill_step_summary
        }
    )
    ScreenScaffold(title = title, onBack = back) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (d.step) {
                BillStep.RECEIPT -> ReceiptStep(d, vm)
                BillStep.PEOPLE -> PeopleStep(d, vm)
                BillStep.ASSIGN -> AssignStep(d, vm)
                BillStep.SUMMARY -> SummaryStep(d, vm)
            }
        }
    }
}

@Composable
private fun StepColumn(buttonLabel: String, enabled: Boolean, onNext: () -> Unit, content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content,
        )
        Button(
            onClick = onNext, enabled = enabled, shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp).height(52.dp),
        ) { Text(buttonLabel) }
    }
}

// ---------- Step 1: receipt ----------

private fun receiptFile(context: Context): File =
    File(context.cacheDir, "receipts").apply { mkdirs() }.let { File(it, "receipt.jpg") }

@Composable
private fun ReceiptStep(d: BillDraft, vm: BillEditViewModel) {
    val context = LocalContext.current
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { taken ->
        val file = receiptFile(context)
        if (taken) vm.scan(Uri.fromFile(file), deleteAfter = file) else file.delete()
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { vm.scan(it) } }

    if (d.scanning) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator()
                Text(stringResource(R.string.bill_reading), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        return
    }

    StepColumn(stringResource(R.string.bill_next), d.validItems.isNotEmpty(), { vm.goTo(BillStep.PEOPLE) }) {
        item {
            Text(stringResource(R.string.bill_receipt_intro), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", receiptFile(context))
                        camera.launch(uri)
                    },
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.bill_take_photo)) }
                OutlinedButton(onClick = { gallery.launch(arrayOf("image/*")) }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.bill_from_gallery))
                }
            }
        }
        if (d.scanFailed) item { BillNotice(stringResource(R.string.bill_scan_failed), warning = true) }
        if (d.scanned && !d.scanFailed) {
            item {
                val printed = d.printedTotal
                when {
                    printed == null -> BillNotice(stringResource(R.string.bill_total_unknown), warning = true)
                    printed == d.computedTotal -> BillNotice(stringResource(R.string.bill_total_ok, Money.format(printed)), warning = false)
                    else -> BillNotice(stringResource(R.string.bill_total_off, Money.format(d.computedTotal), Money.format(printed)), warning = true)
                }
            }
        }
        if (d.taxIncluded) item { BillNotice(stringResource(R.string.bill_tax_included), warning = false) }
        item {
            OutlinedTextField(
                value = d.merchant, onValueChange = { m -> vm.edit { it.copy(merchant = m) } },
                label = { Text(stringResource(R.string.bill_merchant)) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
        }
        item { DateField(stringResource(R.string.date), d.date, { date -> vm.edit { it.copy(date = date) } }, Modifier.fillMaxWidth()) }

        item { SectionTitle(stringResource(R.string.bill_orders)) { TextButton(onClick = vm::addItem) { Text(stringResource(R.string.bill_add)) } } }
        items(d.items, key = { "i${it.key}" }) { item ->
            SectionCard(contentPadding = PaddingValues(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = item.name, onValueChange = { n -> vm.editItem(item.key) { it.copy(name = n) } },
                        label = { Text(stringResource(R.string.bill_order_name)) }, singleLine = true, modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { vm.removeItem(item.key) }) { Icon(Icons.Default.Close, stringResource(R.string.ignore)) }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    QuantityStepper(item.quantity) { q -> vm.editItem(item.key) { it.copy(quantity = q, units = it.units.mapValues { (_, u) -> u.coerceAtMost(q) }) } }
                    MoneyInput(item.digits, stringResource(R.string.bill_line_total), Modifier.weight(1f)) { v -> vm.editItem(item.key) { it.copy(digits = v) } }
                }
            }
        }

        item { SectionTitle(stringResource(R.string.bill_fees)) { TextButton(onClick = vm::addCharge) { Text(stringResource(R.string.bill_add)) } } }
        items(d.charges, key = { "c${it.key}" }) { charge ->
            SectionCard(contentPadding = PaddingValues(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = charge.name, onValueChange = { n -> vm.editCharge(charge.key) { it.copy(name = n) } },
                        label = { Text(stringResource(R.string.bill_fee_name)) }, singleLine = true, modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { vm.removeCharge(charge.key) }) { Icon(Icons.Default.Close, stringResource(R.string.ignore)) }
                }
                MoneyInput(charge.digits, stringResource(R.string.amount), Modifier.fillMaxWidth()) { v -> vm.editCharge(charge.key) { it.copy(digits = v) } }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = charge.discount, onClick = { vm.editCharge(charge.key) { it.copy(discount = !it.discount) } },
                        label = { Text(stringResource(R.string.bill_is_discount)) },
                    )
                    FilterChip(
                        selected = charge.equally, onClick = { vm.editCharge(charge.key) { it.copy(equally = !it.equally) } },
                        label = { Text(stringResource(R.string.bill_split_equally)) },
                    )
                }
            }
        }
        item {
            Row(Modifier.padding(vertical = 4.dp)) {
                Text(stringResource(R.string.bill_total), fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(Money.format(d.computedTotal), fontWeight = FontWeight.ExtraBold)
            }
        }
    }
}

@Composable
private fun MoneyInput(digits: String, label: String, modifier: Modifier, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = digits,
        onValueChange = { v -> onChange(v.filter(Char::isDigit).trimStart('0').take(13)) },
        label = { Text(label) },
        prefix = { Text("Rp ") },
        singleLine = true,
        visualTransformation = ThousandsTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
    )
}

@Composable
private fun QuantityStepper(value: Int, min: Int = 1, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onChange(value - 1) }, enabled = value > min) { Icon(Icons.Default.Remove, null) }
        Text(value.toString(), fontWeight = FontWeight.Bold, fontSize = 16.sp)
        IconButton(onClick = { onChange(value + 1) }) { Icon(Icons.Default.Add, null) }
    }
}

// ---------- Step 2: how many people ----------

@Composable
private fun PeopleStep(d: BillDraft, vm: BillEditViewModel) {
    val context = LocalContext.current
    val nameFor: (Int) -> String = { context.getString(R.string.bill_person_n, it) }
    LaunchedEffect(Unit) { if (d.people.isEmpty()) vm.setPeopleCount(2, nameFor) }

    StepColumn(stringResource(R.string.bill_next), d.people.isNotEmpty(), { vm.goTo(BillStep.ASSIGN) }) {
        item {
            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.bill_people_count), fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    QuantityStepper(d.people.size.coerceAtLeast(1)) { vm.setPeopleCount(it, nameFor) }
                }
            }
        }
        items(d.people, key = { it.key }) { person ->
            OutlinedTextField(
                value = person.name, onValueChange = { vm.renamePerson(person.key, it) },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

// ---------- Step 3: who had what ----------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AssignStep(d: BillDraft, vm: BillEditViewModel) {
    val items = d.validItems
    StepColumn(stringResource(R.string.bill_calculate), items.all { it.assigned }, { vm.goTo(BillStep.SUMMARY) }) {
        item { Text(stringResource(R.string.bill_assign_intro), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(items, key = { it.key }) { item ->
            SectionCard(contentPadding = PaddingValues(12.dp)) {
                Row {
                    Text(
                        (if (item.quantity > 1) "${item.quantity}× " else "") + item.name,
                        fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.weight(1f),
                    )
                    Text(Money.format(item.amount), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    d.people.forEach { person ->
                        val units = item.units[person.key] ?: 0
                        FilterChip(
                            selected = units > 0,
                            onClick = { vm.tapAssign(item.key, person.key) },
                            label = { Text(if (item.quantity > 1 && units > 0) "$units× ${person.name}" else person.name) },
                        )
                    }
                    if (d.people.size > 1) {
                        TextButton(onClick = { vm.assignToEveryone(item.key) }) { Text(stringResource(R.string.bill_everyone)) }
                    }
                }
            }
        }
    }
}

// ---------- Step 4: result, flag my orders, confirm ----------

@Composable
private fun SummaryStep(d: BillDraft, vm: BillEditViewModel) {
    val c = appContainer()
    val wallets by rememberWallets(c)
    val categories by rememberCategories(c)
    val items = d.validItems
    val totals = d.totals()
    val defaultMerchant = stringResource(R.string.bill_title)
    val iPaid = stringResource(R.string.bill_i_paid)

    StepColumn(stringResource(R.string.bill_save, Money.format(totals.myShare)), d.walletId != null && totals.total > 0, { vm.save(defaultMerchant) }) {
        item { Text(stringResource(R.string.bill_flag_intro), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(d.people, key = { it.key }) { person ->
            val share = totals.shares.getValue(person.key)
            val costs = BillSplit.orderCosts(share)
            val mine = items.filterIndexed { i, item -> i.toLong() in costs && person.key in item.mine }
            SectionCard(contentPadding = PaddingValues(start = 4.dp, end = 12.dp, top = 10.dp, bottom = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = costs.isNotEmpty() && mine.size == costs.size,
                        enabled = costs.isNotEmpty(),
                        onCheckedChange = { vm.togglePersonMine(person.key) },
                    )
                    Text(person.name, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text(Money.format(share.total), fontWeight = FontWeight.ExtraBold)
                }
                items.forEachIndexed { i, item ->
                    val cost = costs[i.toLong()] ?: return@forEachIndexed
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = person.key in item.mine, onCheckedChange = { vm.toggleMine(item.key, person.key) })
                        Text(item.name, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        Text(Money.format(cost), fontSize = 13.sp)
                    }
                }
                if (share.chargesTotal != 0L) {
                    Text(
                        stringResource(R.string.bill_fees_included, Money.format(share.chargesTotal)),
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 12.dp),
                    )
                }
            }
        }
        item {
            Surface(color = Brand.GreenSoft, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row {
                        Text(stringResource(R.string.bill_your_orders), fontWeight = FontWeight.Bold, color = Brand.Green, modifier = Modifier.weight(1f))
                        Text(Money.format(totals.myShare), fontWeight = FontWeight.ExtraBold, color = Brand.Green)
                    }
                    Text(stringResource(R.string.bill_of_total, Money.format(totals.total)), fontSize = 12.sp, color = Brand.Green)
                }
            }
        }
        item {
            DropdownField(
                label = stringResource(R.string.bill_who_paid),
                options = listOf<DraftPerson?>(null) + d.people,
                selected = d.people.firstOrNull { it.key == d.payerKey },
                optionLabel = { it?.name ?: iPaid },
                onSelect = { p -> vm.edit { it.copy(payerKey = p?.key) } },
                placeholder = iPaid,
            )
        }
        item {
            DropdownField(
                label = stringResource(R.string.bill_wallet),
                options = wallets,
                selected = wallets.firstOrNull { it.id == d.walletId },
                optionLabel = { it.name },
                onSelect = { vm.setWallet(it.id) },
            )
        }
        item {
            val options = categories.filter { !it.archived && it.type == CategoryType.EXPENSE }
            DropdownField(
                label = stringResource(R.string.bill_category),
                options = options,
                selected = options.firstOrNull { it.id == d.categoryId },
                optionLabel = { categoryLabel(it) },
                onSelect = { cat -> vm.edit { it.copy(categoryId = cat.id) } },
            )
        }
        item {
            val explain = when {
                d.payerKey == null -> stringResource(R.string.bill_effect_i_paid, Money.format(totals.myShare), Money.format(totals.total - totals.myShare))
                else -> stringResource(R.string.bill_effect_other_paid, Money.format(totals.myShare))
            }
            Text(explain, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (d.similarExpense && d.payerKey == null) item { BillNotice(stringResource(R.string.bill_similar_warning, Money.format(d.computedTotal)), warning = true) }
    }
}

@Composable
private fun BillNotice(text: String, warning: Boolean) {
    Surface(
        color = if (warning) MaterialTheme.colorScheme.errorContainer else Brand.GreenSoft,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text, fontSize = 13.sp, modifier = Modifier.padding(12.dp),
            color = if (warning) MaterialTheme.colorScheme.onErrorContainer else Brand.Green,
        )
    }
}

// ---------- Bill detail: who still owes, share ----------

data class BillDetail(
    val bill: BillEntity,
    val people: List<BillPersonEntity>,
    val items: List<BillItemEntity>,
    val shares: List<BillItemShareEntity>,
    val charges: List<BillChargeEntity>,
) {
    /** Recomputed the same way as when it was saved, so the numbers match. */
    val split: Map<Long, BillSplit.Share> by lazy {
        BillSplit.split(
            people.map { it.id },
            items.map { item -> BillSplit.Item(item.id, item.lineTotal, shares.filter { it.itemId == item.id }.associate { it.personId to it.units }) },
            charges.map { BillSplit.Charge(it.amount, it.equally) },
        )
    }
}

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class BillDetailViewModel(private val c: AppContainer, private val billId: Long) : ViewModel() {
    val detail = c.bills.observe(billId).flatMapLatest { bill ->
        if (bill == null) flowOf(null)
        else combine(
            c.bills.observePeople(billId), c.bills.observeItems(billId), c.bills.observeShares(billId), c.bills.observeCharges(billId),
        ) { people, items, shares, charges -> BillDetail(bill, people, items, shares, charges) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setSettled(personId: Long, settled: Boolean) = viewModelScope.launch { c.bills.setSettled(personId, settled) }
    fun delete(onDone: () -> Unit) = viewModelScope.launch { c.bills.delete(billId); onDone() }
}

@Composable
fun BillDetailScreen(nav: NavController, billId: Long) {
    val c = appContainer()
    val vm: BillDetailViewModel = viewModel { BillDetailViewModel(c, billId) }
    val d by vm.detail.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val detail = d

    if (confirmDelete) {
        ConfirmDialog(
            title = stringResource(R.string.bill_delete),
            text = stringResource(R.string.bill_delete_body),
            confirmLabel = stringResource(R.string.bill_delete),
            onConfirm = { confirmDelete = false; vm.delete { nav.popBackStack() } },
            onDismiss = { confirmDelete = false },
        )
    }

    val shareText = detail?.let { shareText(it) }
    ScreenScaffold(
        title = detail?.bill?.merchant.orEmpty(),
        onBack = { nav.popBackStack() },
        actions = {
            if (shareText != null) {
                IconButton(onClick = {
                    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, shareText)
                    context.startActivity(Intent.createChooser(send, null))
                }) { Icon(Icons.Default.Share, stringResource(R.string.bill_share)) }
            }
        },
    ) { padding ->
        if (detail == null) return@ScreenScaffold
        val bill = detail.bill
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Surface(color = Brand.GreenSoft, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row {
                            Text(stringResource(R.string.bill_your_orders), fontWeight = FontWeight.Bold, color = Brand.Green, modifier = Modifier.weight(1f))
                            Text(Money.format(bill.myShare), fontWeight = FontWeight.ExtraBold, color = Brand.Green)
                        }
                        Text(
                            formatDate(Periods.toDate(bill.occurredAt)) + " · " + stringResource(R.string.bill_of_total, Money.format(bill.total)),
                            fontSize = 12.sp, color = Brand.Green,
                        )
                    }
                }
            }
            itemsIndexed(detail.people, key = { _, p -> p.id }) { _, person ->
                val share = detail.split[person.id]
                SectionCard {
                    Row {
                        Text(person.name, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Text(Money.format(person.share), fontWeight = FontWeight.ExtraBold)
                    }
                    if (person.isPayer) Text(stringResource(R.string.bill_paid_the_bill), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    share?.let { BillSplit.orderCosts(it) }?.forEach { (itemId, cost) ->
                        val item = detail.items.firstOrNull { it.id == itemId } ?: return@forEach
                        val mine = detail.shares.any { it.itemId == itemId && it.personId == person.id && it.mine }
                        Row {
                            Text(item.name + if (mine) " · " + stringResource(R.string.bill_mine_tag) else "", fontSize = 13.sp, modifier = Modifier.weight(1f))
                            Text(Money.format(cost), fontSize = 13.sp)
                        }
                    }
                    if (person.owed > 0) {
                        HorizontalDivider(color = Brand.Line)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val label = when {
                                person.settled -> R.string.bill_settled
                                bill.paidByMe -> R.string.bill_they_owe
                                else -> R.string.bill_you_owe
                            }
                            Text(
                                stringResource(label, Money.format(person.owed)),
                                fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                                color = if (person.settled) Brand.Green else Brand.AmberInk,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = { vm.setSettled(person.id, !person.settled) }) {
                                Text(stringResource(if (person.settled) R.string.bill_undo else R.string.bill_mark_paid))
                            }
                        }
                    }
                }
            }
            if (detail.charges.isNotEmpty()) {
                item {
                    SectionCard {
                        Text(stringResource(R.string.bill_fees), fontWeight = FontWeight.Bold)
                        detail.charges.forEach { charge ->
                            Row {
                                Text(charge.name, fontSize = 13.sp, modifier = Modifier.weight(1f))
                                Text(Money.format(charge.amount), fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
            item {
                OutlinedButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.bill_delete)) }
            }
        }
    }
}

@Composable
private fun shareText(d: BillDetail): String {
    val feesWord = stringResource(R.string.bill_share_fees)
    val totalWord = stringResource(R.string.bill_total)
    return buildString {
        appendLine(d.bill.merchant)
        appendLine("$totalWord: ${Money.format(d.bill.total)}")
        d.people.forEach { person ->
            val share = d.split[person.id] ?: return@forEach
            appendLine()
            appendLine("${person.name}: ${Money.format(person.share)}")
            share.itemAmounts.forEach { (itemId, amount) ->
                d.items.firstOrNull { it.id == itemId }?.let { appendLine("- ${it.name}: ${Money.format(amount)}") }
            }
            if (share.chargesTotal != 0L) appendLine("- $feesWord: ${Money.format(share.chargesTotal)}")
        }
    }.trimEnd()
}
