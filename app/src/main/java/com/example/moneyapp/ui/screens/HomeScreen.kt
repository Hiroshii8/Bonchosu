package com.example.moneyapp.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.example.moneyapp.core.BudgetProgress
import com.example.moneyapp.core.Money
import com.example.moneyapp.core.Periods
import com.example.moneyapp.data.db.BudgetEntity
import com.example.moneyapp.data.db.TransactionRow
import com.example.moneyapp.data.db.TxType
import com.example.moneyapp.ui.Routes
import com.example.moneyapp.ui.components.BudgetBar
import com.example.moneyapp.ui.components.SectionCard
import com.example.moneyapp.ui.components.SectionTitle
import com.example.moneyapp.ui.components.TransactionItem
import com.example.moneyapp.ui.components.appContainer
import com.example.moneyapp.ui.components.formatMonth
import com.example.moneyapp.ui.theme.AmountStyle
import com.example.moneyapp.ui.theme.Brand
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

data class HomeState(
    val total: Long = 0,
    val walletCount: Int = 0,
    val monthIncome: Long = 0,
    val monthExpense: Long = 0,
    val pendingCount: Int = 0,
    val hideBalances: Boolean = false,
    /** The "all expenses" budget, if you made one. */
    val overall: BudgetProgress? = null,
    val budgets: List<Pair<BudgetEntity, BudgetProgress>> = emptyList(),
    val recent: List<TransactionRow> = emptyList(),
)

class HomeViewModel(private val c: AppContainer) : ViewModel() {
    private val month = Periods.month(YearMonth.now())

    val state = combine(
        c.wallets.observeWithBalance(),
        c.transactions.observe(month),
        c.settings.settings,
        c.transactions.observePendingCount(),
        c.budgets.observeProgress(),
    ) { wallets, monthRows, settings, pending, budgets ->
        val counted = monthRows.filter { !it.excludeFromReport }
        HomeState(
            total = wallets.filter { !it.wallet.excludeFromTotal }.sumOf { it.balance },
            walletCount = wallets.size,
            monthIncome = counted.filter { it.type == TxType.INCOME }.sumOf { it.amount },
            monthExpense = counted.filter { it.type == TxType.EXPENSE }.sumOf { it.amount },
            pendingCount = pending,
            hideBalances = settings.hideBalances,
            overall = budgets.firstOrNull { it.first.categoryId == null && it.first.walletId == null }?.second,
            budgets = budgets.filter { it.first.categoryId != null }.sortedByDescending { it.second.fraction }.take(3),
            recent = monthRows.take(5),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState())

    fun toggleHide() = viewModelScope.launch { c.settings.setHideBalances(!state.value.hideBalances) }
}

@Composable
fun HomeScreen(nav: NavController) {
    val c = appContainer()
    val vm: HomeViewModel = viewModel { HomeViewModel(c) }
    val s by vm.state.collectAsStateWithLifecycle()
    val hidden = "Rp ••••••"

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.this_month), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                    Text(formatMonth(LocalDate.now()), style = MaterialTheme.typography.titleLarge)
                }
                IconButton(onClick = { nav.navigate(Routes.WALLETS) }) {
                    Icon(Icons.Default.AccountBalanceWallet, stringResource(R.string.wallets))
                }
                IconButton(onClick = { nav.navigate(Routes.SETTINGS) }) {
                    Icon(Icons.Default.Settings, stringResource(R.string.settings))
                }
            }
        }

        item {
            Surface(color = Brand.Green, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.total_balance_n, s.walletCount), color = Brand.GreenOnDark, fontSize = 13.sp)
                            Text(if (s.hideBalances) hidden else Money.format(s.total), style = AmountStyle, color = Color.White)
                        }
                        IconButton(onClick = { vm.toggleHide() }) {
                            Icon(
                                if (s.hideBalances) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                stringResource(if (s.hideBalances) R.string.show_balances else R.string.hide_balances),
                                tint = Brand.GreenOnDark,
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Stat(stringResource(R.string.income), if (s.hideBalances) hidden else "+" + Money.format(s.monthIncome), Modifier.weight(1f))
                        Stat(stringResource(R.string.spent), if (s.hideBalances) hidden else Money.format(-s.monthExpense), Modifier.weight(1f))
                    }
                }
            }
        }

        // Feature-flagged: the review banner only exists when auto-capture is on.
        if (s.pendingCount > 0) { // statement imports land here too, with or without auto-capture
            item {
                Surface(
                    onClick = { nav.navigate(Routes.REVIEW) },
                    color = Brand.AmberSoft,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = CircleShape, color = Brand.Amber, modifier = Modifier.size(36.dp)) {
                            Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Bolt, null, tint = Color.White, modifier = Modifier.size(18.dp)) }
                        }
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text(stringResource(R.string.review_banner_title, s.pendingCount), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(stringResource(R.string.review_banner_body), color = Brand.AmberInk, fontSize = 12.sp)
                        }
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Brand.AmberInk)
                    }
                }
            }
        }

        item {
            Surface(
                onClick = { nav.navigate(Routes.BILLS) },
                color = Color.White,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.bill_title), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(stringResource(R.string.bill_home_hint), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    }
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        item {
            SectionCard {
                SectionTitle(stringResource(R.string.budgets)) {
                    TextButton(onClick = { nav.navigate(Routes.BUDGETS) }) { Text(stringResource(R.string.details)) }
                }
                val overall = s.overall
                if (overall == null && s.budgets.isEmpty()) {
                    Text(stringResource(R.string.no_budgets_hint), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                    TextButton(onClick = { nav.navigate(Routes.budget()) }, contentPadding = PaddingValues(0.dp)) {
                        Text(stringResource(R.string.create_budget))
                    }
                }
                if (overall != null) {
                    BudgetBar(overall.fraction, overall.nearLimit, overall.overBudget)
                    Row {
                        Text(
                            stringResource(R.string.spent_of, Money.format(overall.spent), Money.format(overall.limit)),
                            color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, modifier = Modifier.weight(1f),
                        )
                        Text(stringResource(R.string.per_day_left, Money.format(overall.perDayLeft)), fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    }
                }
                s.budgets.forEach { (b, p) -> MiniBudget(b, p) }
            }
        }

        item {
            SectionCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)) {
                SectionTitle(stringResource(R.string.recent)) {
                    TextButton(onClick = { nav.navigate(Routes.ACTIVITY) }) { Text(stringResource(R.string.see_all)) }
                }
                if (s.recent.isEmpty()) {
                    Text(
                        stringResource(R.string.no_transactions_month),
                        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp,
                        modifier = Modifier.padding(bottom = 12.dp),
                    )
                }
                s.recent.forEach { row -> TransactionItem(row, onClick = { nav.navigate(Routes.tx(row.id)) }) }
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier) {
    Surface(color = Brand.GreenRaised, shape = RoundedCornerShape(12.dp), modifier = modifier) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(label, color = Brand.GreenOnDark, fontSize = 12.sp)
            Text(value, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1)
        }
    }
}

@Composable
private fun MiniBudget(b: BudgetEntity, p: BudgetProgress) {
    val c = appContainer()
    val name = rememberCategoryName(c, b.categoryId)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row {
            Text(name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text("${(p.fraction * 100).toInt()}%", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        BudgetBar(p.fraction, p.nearLimit, p.overBudget)
    }
}
