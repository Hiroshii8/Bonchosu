package com.hiroshi.moneyapp.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import com.hiroshi.moneyapp.AppContainer
import com.hiroshi.moneyapp.R
import com.hiroshi.moneyapp.core.BudgetProgress
import com.hiroshi.moneyapp.core.Money
import com.hiroshi.moneyapp.core.MonthSummary
import com.hiroshi.moneyapp.core.Periods
import com.hiroshi.moneyapp.data.db.BudgetEntity
import com.hiroshi.moneyapp.data.db.TransactionRow
import com.hiroshi.moneyapp.data.db.TxType
import com.hiroshi.moneyapp.data.settings.AppSettings
import com.hiroshi.moneyapp.data.settings.HomeLayout
import com.hiroshi.moneyapp.data.settings.HomeSection
import com.hiroshi.moneyapp.data.settings.SummaryFigure
import com.hiroshi.moneyapp.ui.Routes
import com.hiroshi.moneyapp.ui.components.BudgetBar
import com.hiroshi.moneyapp.ui.components.ScreenScaffold
import com.hiroshi.moneyapp.ui.components.SectionCard
import com.hiroshi.moneyapp.ui.components.SectionTitle
import com.hiroshi.moneyapp.ui.components.TransactionItem
import com.hiroshi.moneyapp.ui.components.appContainer
import com.hiroshi.moneyapp.ui.components.formatMonth
import com.hiroshi.moneyapp.ui.theme.AmountStyle
import com.hiroshi.moneyapp.ui.theme.Brand
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

data class HomeState(
    val month: YearMonth = YearMonth.now(),
    val total: Long = 0,
    val walletCount: Int = 0,
    val summary: MonthSummary = MonthSummary(0, 0, 0, null),
    val pendingCount: Int = 0,
    val settings: AppSettings = AppSettings(),
    /** The "all expenses" budget, if you made one. */
    val overall: BudgetProgress? = null,
    val budgets: List<Pair<BudgetEntity, BudgetProgress>> = emptyList(),
    val recent: List<TransactionRow> = emptyList(),
)

private class MonthData(val month: YearMonth, val summary: MonthSummary, val rows: List<TransactionRow>)

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(private val c: AppContainer) : ViewModel() {
    private val month = MutableStateFlow(YearMonth.now())

    private val monthData = month.flatMapLatest { m ->
        combine(c.transactions.observe(Periods.month(m)), c.transactions.observe(Periods.month(m.minusMonths(1)))) { rows, previous ->
            val counted = rows.filter { !it.excludeFromReport }
            val spent = counted.filter { it.type == TxType.EXPENSE }.sumOf { it.amount }
            val previousSpent = previous.filter { !it.excludeFromReport && it.type == TxType.EXPENSE }.sumOf { it.amount }
            val income = counted.filter { it.type == TxType.INCOME }.sumOf { it.amount }
            MonthData(m, MonthSummary.of(m, income, spent, previousSpent, LocalDate.now()), rows)
        }
    }

    val state = combine(
        c.wallets.observeWithBalance(),
        monthData,
        c.settings.settings,
        c.transactions.observePendingCount(),
        c.budgets.observeProgress(),
    ) { wallets, data, settings, pending, budgets ->
        HomeState(
            month = data.month,
            total = wallets.filter { !it.wallet.excludeFromTotal }.sumOf { it.balance },
            walletCount = wallets.size,
            summary = data.summary,
            pendingCount = pending,
            settings = settings,
            overall = budgets.firstOrNull { it.first.categoryId == null && it.first.walletId == null }?.second,
            budgets = budgets.filter { it.first.categoryId != null }.sortedByDescending { it.second.fraction }.take(3),
            recent = data.rows.take(5),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState())

    fun toggleHide() = viewModelScope.launch { c.settings.setHideBalances(!state.value.settings.hideBalances) }
    fun previousMonth() = month.update { it.minusMonths(1) }
    fun nextMonth() = month.update { m -> if (m < YearMonth.now()) m.plusMonths(1) else m }
}

@Composable
fun HomeScreen(nav: NavController) {
    val c = appContainer()
    val vm: HomeViewModel = viewModel { HomeViewModel(c) }
    val s by vm.state.collectAsStateWithLifecycle()

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = vm::previousMonth) { Icon(Icons.Default.ChevronLeft, stringResource(R.string.previous_period)) }
                Column(Modifier.weight(1f)) {
                    val current = s.month == YearMonth.now()
                    Text(stringResource(if (current) R.string.this_month else R.string.month), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                    Text(formatMonth(s.month.atDay(1)), style = MaterialTheme.typography.titleLarge)
                }
                IconButton(onClick = vm::nextMonth, enabled = s.month < YearMonth.now()) {
                    Icon(Icons.Default.ChevronRight, stringResource(R.string.next_period))
                }
                IconButton(onClick = { nav.navigate(Routes.WALLETS) }) { Icon(Icons.Default.AccountBalanceWallet, stringResource(R.string.wallets)) }
                IconButton(onClick = { nav.navigate(Routes.SETTINGS) }) { Icon(Icons.Default.Settings, stringResource(R.string.settings)) }
            }
        }

        item { SummaryCard(s, vm::toggleHide) }

        s.settings.home.visible.forEach { section ->
            when (section) {
                HomeSection.REVIEW -> if (s.pendingCount > 0) item(key = section) { ReviewBanner(s.pendingCount) { nav.navigate(Routes.REVIEW) } }
                HomeSection.SPLIT_BILL -> item(key = section) {
                    ShortcutCard(stringResource(R.string.bill_title), stringResource(R.string.bill_home_hint)) { nav.navigate(Routes.BILLS) }
                }
                HomeSection.IMPORT_STATEMENT -> item(key = section) {
                    ShortcutCard(stringResource(R.string.statement_title), stringResource(R.string.statement_home_hint)) { nav.navigate(Routes.STATEMENT_IMPORT) }
                }
                HomeSection.BUDGETS -> item(key = section) { BudgetsCard(s, nav) }
                HomeSection.RECENT -> item(key = section) { RecentCard(s, nav) }
            }
        }

        item {
            TextButton(onClick = { nav.navigate(Routes.HOME_CUSTOMIZE) }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Tune, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(6.dp))
                Text(stringResource(R.string.customize_home))
            }
        }
    }
}

@Composable
private fun SummaryCard(s: HomeState, onToggleHide: () -> Unit) {
    val hide = s.settings.hideBalances
    val hidden = "Rp ••••••"
    Surface(color = Brand.Hero, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.total_balance_n, s.walletCount), color = Brand.GreenOnDark, fontSize = 13.sp)
                    Text(if (hide) hidden else Money.format(s.total), style = AmountStyle, color = Color.White)
                }
                IconButton(onClick = onToggleHide) {
                    Icon(
                        if (hide) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                        stringResource(if (hide) R.string.show_balances else R.string.hide_balances),
                        tint = Brand.GreenOnDark,
                    )
                }
            }
            val sum = s.summary
            val stats = s.settings.summaryFigures.mapNotNull { figure ->
                when (figure) {
                    SummaryFigure.INCOME -> stringResource(R.string.income) to "+" + Money.format(sum.income)
                    SummaryFigure.SPENT -> stringResource(R.string.spent) to Money.format(-sum.spent)
                    SummaryFigure.NET -> stringResource(R.string.figure_net) to signed(sum.net)
                    SummaryFigure.DAILY_AVERAGE -> sum.dailyAverage?.let { stringResource(R.string.figure_daily_average) to Money.format(it) }
                    SummaryFigure.VS_LAST_MONTH -> stringResource(R.string.figure_vs_last_month) to signed(sum.vsLastMonth)
                }
            }
            stats.chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    pair.forEach { (label, value) -> Stat(label, if (hide) hidden else value, Modifier.weight(1f)) }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

private fun signed(amount: Long) = if (amount > 0) "+" + Money.format(amount) else Money.format(amount)

@Composable
private fun ReviewBanner(count: Int, onClick: () -> Unit) {
    Surface(onClick = onClick, color = Brand.AmberSoft, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = CircleShape, color = Brand.Amber, modifier = Modifier.size(36.dp)) {
                Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Bolt, null, tint = Color.White, modifier = Modifier.size(18.dp)) }
            }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(stringResource(R.string.review_banner_title, count), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(stringResource(R.string.review_banner_body), color = Brand.AmberInk, fontSize = 12.sp)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Brand.AmberInk)
        }
    }
}

@Composable
private fun ShortcutCard(title: String, hint: String, onClick: () -> Unit) {
    Surface(onClick = onClick, color = Brand.Card, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(hint, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun BudgetsCard(s: HomeState, nav: NavController) {
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

@Composable
private fun RecentCard(s: HomeState, nav: NavController) {
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

@Composable
private fun Stat(label: String, value: String, modifier: Modifier) {
    Surface(color = Brand.GreenRaised, shape = RoundedCornerShape(12.dp), modifier = modifier) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(label, color = Brand.GreenOnDark, fontSize = 12.sp, maxLines = 1)
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

// ---------- Customise Home ----------

class HomeCustomizeViewModel(private val c: AppContainer) : ViewModel() {
    val settings = c.settings.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private fun layout(change: (HomeLayout) -> HomeLayout) = viewModelScope.launch {
        c.settings.setHomeLayout(change(c.settings.current().home))
    }

    fun setVisible(section: HomeSection, visible: Boolean) = layout { l -> l.copy(hidden = if (visible) l.hidden - section else l.hidden + section) }

    fun move(section: HomeSection, by: Int) = layout { l ->
        val from = l.order.indexOf(section)
        val to = (from + by).coerceIn(0, l.order.lastIndex)
        l.copy(order = l.order.toMutableList().apply { add(to, removeAt(from)) })
    }

    /** At least one figure stays, so the card never shows an empty row. */
    fun toggleFigure(figure: SummaryFigure) = viewModelScope.launch {
        val current = c.settings.current().summaryFigures
        val next = if (figure in current) current - figure else SummaryFigure.entries.filter { it in current || it == figure }
        if (next.isNotEmpty()) c.settings.setSummaryFigures(next)
    }
}

@Composable
fun HomeCustomizeScreen(nav: NavController) {
    val c = appContainer()
    val vm: HomeCustomizeViewModel = viewModel { HomeCustomizeViewModel(c) }
    val settings by vm.settings.collectAsStateWithLifecycle()

    ScreenScaffold(title = stringResource(R.string.customize_home), onBack = { nav.popBackStack() }) { padding ->
        val s = settings ?: return@ScreenScaffold
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Text(stringResource(R.string.customize_sections), style = MaterialTheme.typography.titleMedium) }
            item {
                SectionCard(contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
                    s.home.order.forEachIndexed { i, section ->
                        if (i > 0) HorizontalDivider(color = Brand.Line)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(sectionLabel(section), fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.weight(1f).padding(start = 8.dp))
                            IconButton(onClick = { vm.move(section, -1) }, enabled = i > 0) {
                                Icon(Icons.Default.KeyboardArrowUp, stringResource(R.string.move_up))
                            }
                            IconButton(onClick = { vm.move(section, 1) }, enabled = i < s.home.order.lastIndex) {
                                Icon(Icons.Default.KeyboardArrowDown, stringResource(R.string.move_down))
                            }
                            Switch(checked = section !in s.home.hidden, onCheckedChange = { vm.setVisible(section, it) })
                        }
                    }
                }
            }
            item { Text(stringResource(R.string.customize_review_hint), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }

            item { Text(stringResource(R.string.customize_figures), style = MaterialTheme.typography.titleMedium) }
            item {
                SectionCard(contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)) {
                    SummaryFigure.entries.forEach { figure ->
                        Row(
                            Modifier.fillMaxWidth().clickable { vm.toggleFigure(figure) },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = figure in s.summaryFigures, onCheckedChange = { vm.toggleFigure(figure) })
                            Text(figureLabel(figure), fontSize = 14.sp)
                        }
                    }
                }
            }
            item { Text(stringResource(R.string.customize_figures_hint), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
private fun sectionLabel(section: HomeSection) = stringResource(
    when (section) {
        HomeSection.REVIEW -> R.string.section_review
        HomeSection.SPLIT_BILL -> R.string.bill_title
        HomeSection.IMPORT_STATEMENT -> R.string.statement_title
        HomeSection.BUDGETS -> R.string.budgets
        HomeSection.RECENT -> R.string.recent
    }
)

@Composable
private fun figureLabel(figure: SummaryFigure) = stringResource(
    when (figure) {
        SummaryFigure.INCOME -> R.string.income
        SummaryFigure.SPENT -> R.string.spent
        SummaryFigure.NET -> R.string.figure_net
        SummaryFigure.DAILY_AVERAGE -> R.string.figure_daily_average
        SummaryFigure.VS_LAST_MONTH -> R.string.figure_vs_last_month
    }
)
