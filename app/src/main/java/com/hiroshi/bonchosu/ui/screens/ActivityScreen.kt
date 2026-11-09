@file:OptIn(ExperimentalCoroutinesApi::class, ExperimentalMaterial3Api::class)

package com.hiroshi.bonchosu.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.FilterAlt
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
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
import com.hiroshi.bonchosu.core.Money
import com.hiroshi.bonchosu.core.PeriodKind
import com.hiroshi.bonchosu.core.Periods
import com.hiroshi.bonchosu.data.db.TransactionRow
import com.hiroshi.bonchosu.data.db.TxSource
import com.hiroshi.bonchosu.data.db.TxType
import com.hiroshi.bonchosu.data.db.WalletEntity
import com.hiroshi.bonchosu.data.db.WalletType
import com.hiroshi.bonchosu.ui.Routes
import com.hiroshi.bonchosu.ui.components.DropdownField
import com.hiroshi.bonchosu.ui.components.EmptyState
import com.hiroshi.bonchosu.ui.components.FilterPill
import com.hiroshi.bonchosu.ui.components.PeriodSwitcher
import com.hiroshi.bonchosu.ui.components.RoundIconButton
import com.hiroshi.bonchosu.ui.components.ScreenTopBar
import com.hiroshi.bonchosu.ui.components.TransactionGroup
import com.hiroshi.bonchosu.ui.components.appContainer
import com.hiroshi.bonchosu.ui.components.currentLocale
import com.hiroshi.bonchosu.ui.components.formatMonth
import com.hiroshi.bonchosu.ui.theme.Brand
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** The History chips. */
enum class HistoryKind { ALL, EXPENSE, INCOME, AUTO, TRANSFER }

data class ActivityFilter(
    val monthOffset: Int = 0,
    val walletId: Long? = null,
    val query: String = "",
    val kind: HistoryKind = HistoryKind.ALL,
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
        c.transactions.observe(range, f.walletId, f.query.trim()).map { rows ->
            val shown = rows.filter { r ->
                when (f.kind) {
                    HistoryKind.ALL -> true
                    HistoryKind.EXPENSE -> r.type == TxType.EXPENSE
                    HistoryKind.INCOME -> r.type == TxType.INCOME
                    HistoryKind.TRANSFER -> r.type == TxType.TRANSFER
                    HistoryKind.AUTO -> r.source == TxSource.NOTIFICATION
                }
            }
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
    var showFilters by remember { mutableStateOf(false) }
    val filtered = f.walletId != null || f.monthOffset != 0

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(stringResource(R.string.tab_activity)) {
            RoundIconButton(
                if (filtered) Icons.Filled.FilterAlt else Icons.Outlined.FilterAlt,
                stringResource(R.string.filters), { showFilters = true }, badge = filtered,
            )
        }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { SearchBox(f.query) { q -> vm.setFilter { it.copy(query = q) } } }
            item {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HistoryKind.entries.forEach { k ->
                        FilterPill(kindLabel(k), selected = f.kind == k, onClick = { vm.setFilter { it.copy(kind = k) } })
                    }
                }
            }
            if (filtered) {
                item {
                    Text(
                        formatMonth(LocalDate.now().plusMonths(f.monthOffset.toLong())) +
                            (wallets.firstOrNull { it.id == f.walletId }?.let { " · ${it.name}" } ?: ""),
                        color = Brand.Muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            if (s.days.isEmpty()) {
                item { EmptyState(stringResource(R.string.no_transactions_filter)) }
            }
            items(s.days, key = { it.date.toString() }) { day ->
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.padding(horizontal = 6.dp, vertical = 2.dp)) {
                        Text(dayLabel(day.date), fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Brand.Muted, modifier = Modifier.weight(1f))
                        Text(Money.formatSigned(day.net), fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Brand.Muted)
                    }
                    TransactionGroup(day.rows, onClick = { nav.navigate(Routes.tx(it.id)) })
                }
            }
        }
    }

    if (showFilters) {
        ModalBottomSheet(onDismissRequest = { showFilters = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Brand.Surface) {
            Column(Modifier.padding(horizontal = 20.dp).navigationBarsPadding().padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(stringResource(R.string.filters), style = androidx.compose.material3.MaterialTheme.typography.titleLarge)
                PeriodSwitcher(
                    label = formatMonth(LocalDate.now().plusMonths(f.monthOffset.toLong())),
                    onPrev = { vm.setFilter { it.copy(monthOffset = it.monthOffset - 1) } },
                    onNext = { vm.setFilter { it.copy(monthOffset = (it.monthOffset + 1).coerceAtMost(0)) } },
                )
                val all = WalletEntity(id = -1, name = stringResource(R.string.all_wallets), type = WalletType.OTHER)
                DropdownField(
                    label = stringResource(R.string.wallet),
                    options = listOf(all) + wallets,
                    selected = wallets.firstOrNull { it.id == f.walletId } ?: all,
                    optionLabel = { it.name },
                    onSelect = { w -> vm.setFilter { it.copy(walletId = w.id.takeIf { id -> id > 0 }) } },
                )
                Text(
                    stringResource(R.string.in_out, "+" + Money.format(s.inflow), Money.format(-s.outflow)),
                    color = Brand.Muted, fontSize = 14.sp,
                )
            }
        }
    }
}

/** Pill search field: "Search merchant, note, amount". */
@Composable
private fun SearchBox(query: String, onChange: (String) -> Unit) {
    Surface(shape = RoundedCornerShape(20.dp), color = Brand.Surface, border = BorderStroke(1.dp, Brand.Border), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 18.dp, end = 6.dp).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Search, null, tint = Brand.Muted, modifier = Modifier.size(22.dp))
            Box(Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 14.dp)) {
                if (query.isEmpty()) Text(stringResource(R.string.history_search_hint), color = Brand.Muted, fontSize = 16.sp, maxLines = 1)
                BasicTextField(
                    value = query, onValueChange = onChange, singleLine = true,
                    textStyle = TextStyle(color = Brand.Text, fontSize = 16.sp), cursorBrush = SolidColor(Brand.Accent),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (query.isNotEmpty()) IconButton(onClick = { onChange("") }) { Icon(Icons.Default.Close, stringResource(R.string.cancel), tint = Brand.Muted) }
        }
    }
}

@Composable
private fun kindLabel(k: HistoryKind) = stringResource(
    when (k) {
        HistoryKind.ALL -> R.string.all
        HistoryKind.EXPENSE -> R.string.type_expense
        HistoryKind.INCOME -> R.string.type_income
        HistoryKind.AUTO -> R.string.filter_auto
        HistoryKind.TRANSFER -> R.string.type_transfer
    }
)

/** "Today · Sun 8 Nov", "Yesterday · Sat 7 Nov", "Fri 6 Nov". */
@Composable
private fun dayLabel(date: LocalDate): String {
    val short = DateTimeFormatter.ofPattern(if (date.year == LocalDate.now().year) "EEE d MMM" else "EEE d MMM yyyy", currentLocale()).format(date)
    return when (date) {
        LocalDate.now() -> stringResource(R.string.day_today) + " · " + short
        LocalDate.now().minusDays(1) -> stringResource(R.string.day_yesterday) + " · " + short
        else -> short
    }
}
