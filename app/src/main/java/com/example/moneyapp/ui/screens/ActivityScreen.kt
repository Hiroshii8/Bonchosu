@file:OptIn(ExperimentalCoroutinesApi::class)

package com.example.moneyapp.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.example.moneyapp.AppContainer
import com.example.moneyapp.R
import com.example.moneyapp.core.Money
import com.example.moneyapp.core.PeriodKind
import com.example.moneyapp.core.Periods
import com.example.moneyapp.data.db.TransactionRow
import com.example.moneyapp.data.db.TxType
import com.example.moneyapp.data.db.WalletEntity
import com.example.moneyapp.ui.Routes
import com.example.moneyapp.ui.components.DropdownField
import com.example.moneyapp.ui.components.EmptyState
import com.example.moneyapp.ui.components.PeriodSwitcher
import com.example.moneyapp.ui.components.ScreenScaffold
import com.example.moneyapp.ui.components.SectionCard
import com.example.moneyapp.ui.components.TransactionItem
import com.example.moneyapp.ui.components.appContainer
import com.example.moneyapp.ui.components.formatDayHeader
import com.example.moneyapp.ui.components.formatMonth
import com.example.moneyapp.ui.theme.Brand
import com.example.moneyapp.ui.txTypeLabel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.LocalDate

data class ActivityFilter(
    val monthOffset: Int = 0,
    val walletId: Long? = null,
    val query: String = "",
    /** null = all types */
    val type: TxType? = null,
)

data class DayGroup(val date: LocalDate, val rows: List<TransactionRow>, val net: Long)

data class ActivityState(
    val filter: ActivityFilter = ActivityFilter(),
    val days: List<DayGroup> = emptyList(),
    val inflow: Long = 0,
    val outflow: Long = 0,
)

class ActivityViewModel(c: AppContainer) : ViewModel() {
    private val filter = MutableStateFlow(ActivityFilter())

    val state = filter.flatMapLatest { f ->
        val range = Periods.of(PeriodKind.MONTH, LocalDate.now(), f.monthOffset)
        c.transactions.observe(range, f.walletId, f.query).map { rows ->
            val shown = if (f.type == null) rows else rows.filter { it.type == f.type }
            ActivityState(
                filter = f,
                days = shown.groupBy { Periods.toDate(it.occurredAt) }.map { (date, list) ->
                    DayGroup(date, list, list.sumOf { netOf(it) })
                },
                inflow = shown.filter { it.type == TxType.INCOME }.sumOf { it.amount },
                outflow = shown.filter { it.type == TxType.EXPENSE }.sumOf { it.amount },
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ActivityState())

    fun setFilter(change: (ActivityFilter) -> ActivityFilter) = filter.update(change)

    private fun netOf(r: TransactionRow) = when (r.type) {
        TxType.INCOME -> r.amount
        TxType.EXPENSE -> -r.amount
        TxType.TRANSFER -> 0L
    }
}

@Composable
fun ActivityScreen(nav: NavController) {
    val c = appContainer()
    val vm: ActivityViewModel = viewModel { ActivityViewModel(c) }
    val s by vm.state.collectAsStateWithLifecycle()
    val wallets by rememberWallets(c)
    val f = s.filter

    ScreenScaffold(title = stringResource(R.string.tab_activity)) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                PeriodSwitcher(
                    label = formatMonth(LocalDate.now().plusMonths(f.monthOffset.toLong())),
                    onPrev = { vm.setFilter { it.copy(monthOffset = it.monthOffset - 1) } },
                    onNext = { vm.setFilter { it.copy(monthOffset = it.monthOffset + 1) } },
                )
            }
            item {
                val all = WalletEntity(id = -1, name = stringResource(R.string.all_wallets), type = com.example.moneyapp.data.db.WalletType.OTHER)
                DropdownField(
                    label = stringResource(R.string.wallet),
                    options = listOf(all) + wallets,
                    selected = wallets.firstOrNull { it.id == f.walletId } ?: all,
                    optionLabel = { it.name },
                    onSelect = { w -> vm.setFilter { it.copy(walletId = w.id.takeIf { id -> id > 0 }) } },
                )
            }
            item {
                OutlinedTextField(
                    value = f.query,
                    onValueChange = { q -> vm.setFilter { it.copy(query = q) } },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    placeholder = { Text(stringResource(R.string.search_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = f.type == null, onClick = { vm.setFilter { it.copy(type = null) } }, label = { Text(stringResource(R.string.all)) })
                    TxType.entries.forEach { t ->
                        FilterChip(selected = f.type == t, onClick = { vm.setFilter { it.copy(type = t) } }, label = { Text(txTypeLabel(t)) })
                    }
                }
            }
            item {
                SectionCard {
                    SummaryLine(stringResource(R.string.inflow), "+" + Money.format(s.inflow), Brand.IncomeText)
                    SummaryLine(stringResource(R.string.outflow), Money.format(-s.outflow), Brand.Ink)
                    SummaryLine(stringResource(R.string.net), Money.formatSigned(s.inflow - s.outflow), Brand.Ink, bold = true)
                }
            }
            if (s.days.isEmpty()) {
                item { EmptyState(stringResource(R.string.no_transactions_filter)) }
            }
            items(s.days, key = { it.date.toString() }) { day ->
                Column {
                    Row(Modifier.padding(horizontal = 4.dp, vertical = 6.dp)) {
                        Text(formatDayHeader(day.date), fontWeight = FontWeight.Bold, fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                        Text(Money.formatSigned(day.net), fontWeight = FontWeight.Bold, fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    SectionCard(contentPadding = PaddingValues(horizontal = 14.dp, vertical = 2.dp)) {
                        day.rows.forEach { row -> TransactionItem(row, onClick = { nav.navigate(Routes.tx(row.id)) }) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryLine(label: String, value: String, color: androidx.compose.ui.graphics.Color, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(value, color = color, fontSize = 14.sp, fontWeight = if (bold) FontWeight.ExtraBold else FontWeight.SemiBold)
    }
}
