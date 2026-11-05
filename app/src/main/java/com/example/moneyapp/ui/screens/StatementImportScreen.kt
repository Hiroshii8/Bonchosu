package com.hiroshi.moneyapp.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
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
import com.hiroshi.moneyapp.AppContainer
import com.hiroshi.moneyapp.R
import com.hiroshi.moneyapp.data.db.TxType
import com.hiroshi.moneyapp.data.db.WalletEntity
import com.hiroshi.moneyapp.statement.PasswordRequiredException
import com.hiroshi.moneyapp.statement.StatementParser
import com.hiroshi.moneyapp.statement.StatementRow
import com.hiroshi.moneyapp.ui.Routes
import com.hiroshi.moneyapp.ui.components.DropdownField
import com.hiroshi.moneyapp.ui.components.ScreenScaffold
import com.hiroshi.moneyapp.ui.components.SectionCard
import com.hiroshi.moneyapp.ui.components.amountColor
import com.hiroshi.moneyapp.ui.components.appContainer
import com.hiroshi.moneyapp.ui.components.formatDate
import com.hiroshi.moneyapp.ui.components.signedAmount
import com.hiroshi.moneyapp.ui.theme.Brand
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PreviewRow(val row: StatementRow, val selected: Boolean, val duplicate: Boolean)

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
)

class StatementImportViewModel(private val c: AppContainer) : ViewModel() {
    val ui = MutableStateFlow(StatementImportUi())
    private var pendingUris: List<Uri> = emptyList()

    fun read(uris: List<Uri>, password: String? = null) {
        if (uris.isEmpty()) return
        pendingUris = uris
        ui.value = StatementImportUi(working = true)
        viewModelScope.launch {
            try {
                val parsed = uris.map { StatementParser.parse(c.statementReader.readLines(it, password)) }
                val rows = parsed.flatMap { it.rows }.sortedBy { it.date }
                val wallet = pickWallet(rows)
                val duplicates = wallet?.let { c.statementImporter.findDuplicates(it.id, rows) }.orEmpty()
                pendingUris = emptyList()
                ui.value = StatementImportUi(
                    nothingFound = rows.isEmpty(),
                    rows = rows.mapIndexed { i, row -> PreviewRow(row, selected = i !in duplicates, duplicate = i in duplicates) },
                    balancesMatch = parsed.singleOrNull()?.balancesMatch,
                    walletId = wallet?.id,
                )
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
            c.statementImporter.import(walletId, s.rows.filter { it.selected }.map { it.row })
            ui.update { it.copy(working = false, imported = true) }
        }
    }
}

@Composable
fun StatementImportScreen(nav: NavController) {
    val c = appContainer()
    val vm: StatementImportViewModel = viewModel { StatementImportViewModel(c) }
    val s by vm.ui.collectAsStateWithLifecycle()
    val wallets by rememberWallets(c)
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { vm.read(it) }
    val pick = { picker.launch(arrayOf("application/pdf", "image/*")) }

    LaunchedEffect(s.imported) {
        if (s.imported) nav.navigate(Routes.REVIEW) { popUpTo(Routes.STATEMENT_IMPORT) { inclusive = true } }
    }
    if (s.askPassword) PasswordDialog(s.wrongPassword, vm::submitPassword, vm::cancelPassword)

    ScreenScaffold(title = stringResource(R.string.statement_title), onBack = { nav.popBackStack() }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            when {
                s.working -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator()
                        Text(stringResource(R.string.statement_reading), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                s.rows.isEmpty() -> PickPrompt(s, pick)
                else -> Preview(s, wallets, vm, pick)
            }
        }
    }
}

@Composable
private fun PickPrompt(s: StatementImportUi, pick: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.statement_intro), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
        Text(stringResource(R.string.statement_privacy), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        if (s.failed) Notice(stringResource(R.string.statement_failed), warning = true)
        if (s.nothingFound) Notice(stringResource(R.string.statement_none), warning = true)
        Button(onClick = pick, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().height(52.dp)) {
            Text(stringResource(R.string.statement_pick))
        }
    }
}

@Composable
private fun Preview(s: StatementImportUi, wallets: List<WalletEntity>, vm: StatementImportViewModel, pick: () -> Unit) {
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
            when (s.balancesMatch) {
                true -> item { Notice(stringResource(R.string.statement_balance_ok), warning = false) }
                false -> item { Notice(stringResource(R.string.statement_balance_off), warning = true) }
                null -> item { Notice(stringResource(R.string.statement_balance_unknown), warning = true) }
            }
            if (uncertain > 0) item { Notice(pluralStringResource(R.plurals.statement_uncertain, uncertain, uncertain), warning = true) }
            if (duplicates > 0) item { Notice(pluralStringResource(R.plurals.statement_duplicates, duplicates, duplicates), warning = false) }
            itemsIndexed(s.rows) { i, p ->
                RowCard(p, onToggle = { vm.toggle(i) }, onFlip = { vm.flipType(i) })
            }
            item { OutlinedButton(onClick = pick, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.statement_pick_other)) } }
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
                Text(
                    formatDate(p.row.date) + if (p.duplicate) " · " + stringResource(R.string.statement_already_recorded) else "",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
