package com.hiroshi.bonchosu.ui.screens

import android.net.Uri
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.hiroshi.bonchosu.AppContainer
import com.hiroshi.bonchosu.R
import com.hiroshi.bonchosu.bill.ReceiptScan
import com.hiroshi.bonchosu.core.Periods
import com.hiroshi.bonchosu.data.db.TxSource
import com.hiroshi.bonchosu.data.db.TxType
import com.hiroshi.bonchosu.data.db.WalletEntity
import com.hiroshi.bonchosu.statement.PasswordRequiredException
import com.hiroshi.bonchosu.statement.StatementParser
import com.hiroshi.bonchosu.statement.StatementRow
import com.hiroshi.bonchosu.ui.Routes
import com.hiroshi.bonchosu.ui.components.DropdownField
import com.hiroshi.bonchosu.ui.components.ScreenScaffold
import com.hiroshi.bonchosu.ui.components.SectionCard
import com.hiroshi.bonchosu.ui.components.amountColor
import com.hiroshi.bonchosu.ui.components.appContainer
import com.hiroshi.bonchosu.ui.components.formatDate
import com.hiroshi.bonchosu.ui.components.signedAmount
import com.hiroshi.bonchosu.ui.theme.Brand
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

data class PreviewRow(
    val row: StatementRow,
    val selected: Boolean,
    val duplicate: Boolean,
    /** Receipts only: no date on the paper, so the photo's date was used. */
    val dateGuessed: Boolean = false,
    /** Receipts only: the same receipt appears earlier in this batch. */
    val repeat: Boolean = false,
)

data class StatementImportUi(
    val working: Boolean = false,
    val askPassword: Boolean = false,
    val wrongPassword: Boolean = false,
    val failed: Boolean = false,
    val nothingFound: Boolean = false,
    val rows: List<PreviewRow> = emptyList(),
    /** Only known when one statement was read and it prints both balances. */
    val balancesMatch: Boolean? = null,
    val walletId: Long? = null,
    val imported: Boolean = false,
    /** Receipts only: photos that didn't look like a receipt. */
    val skipped: Int = 0,
)

/** One screen, two jobs: bank statements (PDF or screenshots), or [receipts] (several photos, one expense each). */
class StatementImportViewModel(private val c: AppContainer, private val receipts: Boolean) : ViewModel() {
    val ui = MutableStateFlow(StatementImportUi())
    private var pendingUris: List<Uri> = emptyList()

    fun read(uris: List<Uri>, password: String? = null) {
        if (uris.isEmpty()) return
        pendingUris = uris
        ui.value = StatementImportUi(working = true)
        viewModelScope.launch {
            try {
                ui.value = if (receipts) readReceipts(uris) else readStatements(uris, password)
                pendingUris = emptyList()
            } catch (_: PasswordRequiredException) {
                ui.value = StatementImportUi(askPassword = true, wrongPassword = password != null)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Deliberately not logged: the failure could quote statement contents.
                ui.value = StatementImportUi(failed = true)
            }
        }
    }

    private suspend fun readStatements(uris: List<Uri>, password: String?): StatementImportUi {
        val parsed = uris.map { StatementParser.parse(c.statementReader.readLines(it, password)) }
        val rows = parsed.flatMap { it.rows }.sortedBy { it.date }
        val wallet = pickWallet(rows)
        val duplicates = wallet?.let { c.statementImporter.findDuplicates(it.id, rows) }.orEmpty()
        return StatementImportUi(
            nothingFound = rows.isEmpty(),
            rows = rows.mapIndexed { i, row -> PreviewRow(row, selected = i !in duplicates, duplicate = i in duplicates) },
            balancesMatch = parsed.singleOrNull()?.balancesMatch,
            walletId = wallet?.id,
        )
    }

    /** Each photo is read on its own; photos that aren't receipts are skipped and counted. */
    private suspend fun readReceipts(uris: List<Uri>): StatementImportUi {
        val found = withContext(Dispatchers.IO) {
            uris.mapNotNull { uri ->
                runCatching { ReceiptScan.read(c.ocr.readImage(uri), photoDate(uri)) }.getOrNull()
            }
        }.sortedBy { it.row.date }
        val rows = found.map { it.row }
        val wallet = pickWallet(rows)
        val duplicates = wallet?.let { c.statementImporter.findDuplicates(it.id, rows) }.orEmpty()
        val repeats = ReceiptScan.repeats(rows)
        return StatementImportUi(
            nothingFound = rows.isEmpty(),
            rows = found.mapIndexed { i, r ->
                val dup = i in duplicates
                val again = i in repeats
                PreviewRow(r.row, selected = !dup && !again, duplicate = dup, dateGuessed = !r.dateFound, repeat = again)
            },
            walletId = wallet?.id,
            skipped = uris.size - found.size,
        )
    }

    /** When the photo was taken, for receipts that print no date. Falls back to today. */
    private fun photoDate(uri: Uri): LocalDate {
        val takenMs = runCatching {
            c.appContext.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.DATE_TAKEN), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else null
            }
        }.getOrNull()
        return takenMs?.takeIf { it > 0 }?.let { Periods.toDate(it) } ?: LocalDate.now()
    }

    fun submitPassword(password: String) = read(pendingUris, password)
    fun cancelPassword() { ui.value = StatementImportUi() }

    /** A wallet named in the descriptions wins (e.g. the "Superbank" wallet), else the first one. */
    private suspend fun pickWallet(rows: List<StatementRow>): WalletEntity? {
        val wallets = c.wallets.observeActive().first()
        val text = rows.joinToString(" ") { it.rawText }.lowercase()
        return wallets.firstOrNull { it.name.lowercase() in text } ?: wallets.firstOrNull()
    }

    fun setWallet(walletId: Long) = viewModelScope.launch {
        val rows = ui.value.rows
        val duplicates = c.statementImporter.findDuplicates(walletId, rows.map { it.row })
        ui.update { s ->
            s.copy(
                walletId = walletId,
                rows = rows.mapIndexed { i, p -> p.copy(selected = i !in duplicates, duplicate = i in duplicates) },
            )
        }
    }

    fun toggle(index: Int) = updateRow(index) { it.copy(selected = !it.selected) }

    fun flipType(index: Int) = updateRow(index) { p ->
        val flipped = if (p.row.type == TxType.INCOME) TxType.EXPENSE else TxType.INCOME
        p.copy(row = p.row.copy(type = flipped, typeCertain = true))
    }

    private fun updateRow(index: Int, change: (PreviewRow) -> PreviewRow) = ui.update { s ->
        s.copy(rows = s.rows.mapIndexed { i, p -> if (i == index) change(p) else p })
    }

    fun import() {
        val s = ui.value
        val walletId = s.walletId ?: return
        ui.update { it.copy(working = true) }
        viewModelScope.launch {
            c.statementImporter.import(walletId, s.rows.filter { it.selected }.map { it.row }, if (receipts) TxSource.RECEIPT else TxSource.IMPORT)
            ui.update { it.copy(working = false, imported = true) }
        }
    }
}

@Composable
fun StatementImportScreen(nav: NavController, receipts: Boolean = false) {
    val c = appContainer()
    val vm: StatementImportViewModel = viewModel { StatementImportViewModel(c, receipts) }
    val s by vm.ui.collectAsStateWithLifecycle()
    val wallets by rememberWallets(c)
    val files = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { vm.read(it) }
    // The photo picker needs no storage permission and lets you tick many photos at once.
    val photos = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(MAX_RECEIPTS)) { vm.read(it) }
    val pick = {
        if (receipts) photos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        else files.launch(arrayOf("application/pdf", "image/*"))
    }
    val route = if (receipts) Routes.RECEIPT_SCAN else Routes.STATEMENT_IMPORT

    LaunchedEffect(s.imported) {
        if (s.imported) nav.navigate(Routes.REVIEW) { popUpTo(route) { inclusive = true } }
    }
    if (s.askPassword) PasswordDialog(s.wrongPassword, vm::submitPassword, vm::cancelPassword)

    val title = stringResource(if (receipts) R.string.receipts_title else R.string.statement_title)
    ScreenScaffold(title = title, onBack = { nav.popBackStack() }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            when {
                s.working -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator()
                        Text(stringResource(R.string.statement_reading), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                s.rows.isEmpty() -> PickPrompt(s, receipts, pick)
                else -> Preview(s, receipts, wallets, vm, pick)
            }
        }
    }
}

private const val MAX_RECEIPTS = 30

@Composable
private fun PickPrompt(s: StatementImportUi, receipts: Boolean, pick: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(if (receipts) R.string.receipts_intro else R.string.statement_intro), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
        Text(stringResource(R.string.statement_privacy), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        if (s.failed) Notice(stringResource(R.string.statement_failed), warning = true)
        if (s.nothingFound) Notice(stringResource(if (receipts) R.string.receipts_none else R.string.statement_none), warning = true)
        Button(onClick = pick, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().height(52.dp)) {
            Text(stringResource(if (receipts) R.string.receipts_pick else R.string.statement_pick))
        }
    }
}

@Composable
private fun Preview(s: StatementImportUi, receipts: Boolean, wallets: List<WalletEntity>, vm: StatementImportViewModel, pick: () -> Unit) {
    val selected = s.rows.count { it.selected }
    val uncertain = s.rows.count { it.selected && !it.row.typeCertain }
    val duplicates = s.rows.count { it.duplicate }

    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                DropdownField(
                    label = stringResource(R.string.statement_wallet),
                    options = wallets,
                    selected = wallets.firstOrNull { it.id == s.walletId },
                    optionLabel = { it.name },
                    onSelect = { vm.setWallet(it.id) },
                )
            }
            if (receipts) {
                item { Notice(stringResource(R.string.receipts_check), warning = false) }
                if (s.skipped > 0) item { Notice(pluralStringResource(R.plurals.receipts_skipped, s.skipped, s.skipped), warning = true) }
            } else when (s.balancesMatch) {
                true -> item { Notice(stringResource(R.string.statement_balance_ok), warning = false) }
                false -> item { Notice(stringResource(R.string.statement_balance_off), warning = true) }
                null -> item { Notice(stringResource(R.string.statement_balance_unknown), warning = true) }
            }
            if (uncertain > 0) item { Notice(pluralStringResource(R.plurals.statement_uncertain, uncertain, uncertain), warning = true) }
            if (duplicates > 0) item { Notice(pluralStringResource(R.plurals.statement_duplicates, duplicates, duplicates), warning = false) }
            itemsIndexed(s.rows) { i, p ->
                RowCard(p, onToggle = { vm.toggle(i) }, onFlip = { vm.flipType(i) })
            }
            item {
                OutlinedButton(onClick = pick, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(if (receipts) R.string.receipts_pick_other else R.string.statement_pick_other))
                }
            }
        }
        Button(
            onClick = { vm.import() },
            enabled = selected > 0 && s.walletId != null,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp).height(52.dp),
        ) { Text(pluralStringResource(R.plurals.statement_send, selected, selected)) }
    }
}

@Composable
private fun RowCard(p: PreviewRow, onToggle: () -> Unit, onFlip: () -> Unit) {
    SectionCard(contentPadding = PaddingValues(start = 4.dp, end = 12.dp, top = 8.dp, bottom = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = p.selected, onCheckedChange = { onToggle() })
            Column(Modifier.weight(1f)) {
                Text(
                    p.row.description.ifBlank { stringResource(R.string.unknown_merchant) },
                    fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                )
                val notes = listOfNotNull(
                    formatDate(p.row.date),
                    stringResource(R.string.receipts_photo_date).takeIf { p.dateGuessed },
                    stringResource(R.string.statement_already_recorded).takeIf { p.duplicate },
                    stringResource(R.string.receipts_repeat).takeIf { p.repeat },
                )
                Text(notes.joinToString(" · "), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            // Tap the amount to flip income/expense when the statement gave no clear signal.
            Column(horizontalAlignment = Alignment.End, modifier = Modifier.clickable(onClick = onFlip).padding(start = 8.dp)) {
                Text(signedAmount(p.row.type, p.row.amount), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = amountColor(p.row.type))
                if (!p.row.typeCertain) {
                    Text(stringResource(R.string.statement_tap_to_flip), fontSize = 11.sp, color = Brand.AmberInk, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun Notice(text: String, warning: Boolean) {
    Surface(
        color = if (warning) MaterialTheme.colorScheme.errorContainer else Brand.AccentSoft,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text, fontSize = 13.sp, modifier = Modifier.padding(12.dp),
            color = if (warning) MaterialTheme.colorScheme.onErrorContainer else Brand.Accent,
        )
    }
}

@Composable
private fun PasswordDialog(wrong: Boolean, onSubmit: (String) -> Unit, onCancel: () -> Unit) {
    var password by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.statement_password_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(if (wrong) R.string.statement_password_wrong else R.string.statement_password_body), fontSize = 13.sp)
                OutlinedTextField(
                    value = password, onValueChange = { password = it }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSubmit(password) }, enabled = password.isNotEmpty()) { Text(stringResource(R.string.statement_open)) } },
        dismissButton = { TextButton(onClick = onCancel) { Text(stringResource(R.string.not_now)) } },
    )
}
