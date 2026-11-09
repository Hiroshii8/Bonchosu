package com.hiroshi.bonchosu.ui.screens

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
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
import com.hiroshi.bonchosu.data.db.CategoryEntity
import com.hiroshi.bonchosu.data.db.CategoryType
import com.hiroshi.bonchosu.data.db.TransactionRow
import com.hiroshi.bonchosu.data.db.TxType
import com.hiroshi.bonchosu.ui.Routes
import com.hiroshi.bonchosu.ui.categoryLabel
import com.hiroshi.bonchosu.ui.components.EmptyState
import com.hiroshi.bonchosu.ui.components.ScreenScaffold
import com.hiroshi.bonchosu.ui.components.SectionCard
import com.hiroshi.bonchosu.ui.components.amountColor
import com.hiroshi.bonchosu.ui.components.appContainer
import com.hiroshi.bonchosu.ui.components.formatDate
import com.hiroshi.bonchosu.ui.components.signedAmount
import com.hiroshi.bonchosu.core.Periods
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ReviewViewModel(private val c: AppContainer) : ViewModel() {
    val pending = c.transactions.observePending()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setCategory(txId: Long, categoryId: Long) = viewModelScope.launch {
        c.transactions.get(txId)?.let { c.transactions.save(it.copy(categoryId = categoryId)) }
    }

    fun confirm(txId: Long) = viewModelScope.launch {
        c.transactions.get(txId)?.let { c.transactions.confirm(it) }
    }

    fun ignore(txId: Long) = viewModelScope.launch { c.transactions.delete(txId) }

    /** Confirms every capture that already has a category. */
    fun confirmReady() = viewModelScope.launch {
        pending.value.filter { it.categoryId != null && it.type != TxType.TRANSFER }.forEach { row ->
            c.transactions.get(row.id)?.let { c.transactions.confirm(it) }
        }
    }
}

@Composable
fun ReviewScreen(nav: NavController) {
    val c = appContainer()
    val vm: ReviewViewModel = viewModel { ReviewViewModel(c) }
    val rows by vm.pending.collectAsStateWithLifecycle()
    val categories by rememberCategories(c)
    val ready = rows.count { it.categoryId != null && it.type != TxType.TRANSFER }

    ScreenScaffold(title = stringResource(R.string.review_title), onBack = { nav.popBackStack() }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(
                Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Text(stringResource(R.string.review_intro), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                }
                if (rows.isEmpty()) item { EmptyState(stringResource(R.string.all_caught_up)) }
                items(rows, key = { it.id }) { row ->
                    ReviewCard(
                        row = row,
                        categories = categories.filter {
                            !it.archived && it.type == (if (row.type == TxType.INCOME) CategoryType.INCOME else CategoryType.EXPENSE)
                        },
                        onCategory = { vm.setCategory(row.id, it.id) },
                        onConfirm = { vm.confirm(row.id) },
                        onIgnore = { vm.ignore(row.id) },
                        onEdit = { nav.navigate(Routes.tx(row.id)) },
                    )
                }
            }
            if (ready > 0) {
                Button(
                    onClick = { vm.confirmReady() },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp).height(52.dp),
                ) { Text(pluralStringResource(R.plurals.confirm_ready, ready, ready)) }
            }
        }
    }
}

@Composable
private fun ReviewCard(
    row: TransactionRow,
    categories: List<CategoryEntity>,
    onCategory: (CategoryEntity) -> Unit,
    onConfirm: () -> Unit,
    onIgnore: () -> Unit,
    onEdit: () -> Unit,
) {
    SectionCard(modifier = Modifier.clickable(onClick = onEdit)) {
        Row {
            Text(row.walletName, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            Text(formatDate(Periods.toDate(row.occurredAt)), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                row.merchant ?: stringResource(R.string.unknown_merchant),
                fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.weight(1f),
            )
            Text(signedAmount(row.type, row.amount), fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = amountColor(row.type))
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CategoryPicker(row, categories, onCategory, Modifier.weight(1f))
            OutlinedIconButton(onClick = onIgnore) { Icon(Icons.Default.Close, stringResource(R.string.ignore)) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onEdit, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.edit_or_transfer)) }
            Button(onClick = onConfirm, enabled = row.categoryId != null, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.confirm))
            }
        }
    }
}

@Composable
private fun CategoryPicker(row: TransactionRow, categories: List<CategoryEntity>, onPick: (CategoryEntity) -> Unit, modifier: Modifier) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        AssistChip(
            onClick = { open = true },
            label = {
                Text(
                    if (row.categoryId == null) stringResource(R.string.pick_category)
                    else categoryLabel(row.categoryName, row.categoryKey),
                )
            },
            trailingIcon = { Icon(Icons.Default.ExpandMore, null) },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            categories.forEach { cat ->
                DropdownMenuItem(text = { Text(categoryLabel(cat)) }, onClick = { onPick(cat); open = false })
            }
        }
    }
}
