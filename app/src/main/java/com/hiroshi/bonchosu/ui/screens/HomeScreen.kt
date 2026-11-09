package com.hiroshi.bonchosu.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LocalTextStyle
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.withStyle
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.filled.NorthEast
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SouthWest
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.hiroshi.bonchosu.ui.components.RoundIconButton
import com.hiroshi.bonchosu.ui.components.TransactionGroup
import com.hiroshi.bonchosu.ui.components.currentLocale
import com.hiroshi.bonchosu.ui.theme.CardRadius
import java.time.Instant
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
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
import com.hiroshi.bonchosu.AppContainer
import com.hiroshi.bonchosu.R
import com.hiroshi.bonchosu.core.BudgetProgress
import com.hiroshi.bonchosu.core.Money
import com.hiroshi.bonchosu.core.MonthSummary
import com.hiroshi.bonchosu.core.Periods
import com.hiroshi.bonchosu.data.db.BudgetEntity
import com.hiroshi.bonchosu.data.db.TransactionRow
import com.hiroshi.bonchosu.data.db.TxType
import com.hiroshi.bonchosu.data.settings.AppSettings
import com.hiroshi.bonchosu.data.settings.HomeLayout
import com.hiroshi.bonchosu.data.settings.HomeSection
import com.hiroshi.bonchosu.data.settings.SummaryFigure
import com.hiroshi.bonchosu.ui.Routes
import com.hiroshi.bonchosu.ui.components.BudgetBar
import com.hiroshi.bonchosu.ui.components.ScreenScaffold
import com.hiroshi.bonchosu.ui.components.SectionCard
import com.hiroshi.bonchosu.ui.components.SectionTitle
import com.hiroshi.bonchosu.ui.components.TransactionItem
import com.hiroshi.bonchosu.ui.components.appContainer
import com.hiroshi.bonchosu.ui.components.formatMonth
import com.hiroshi.bonchosu.ui.theme.AmountStyle
import com.hiroshi.bonchosu.ui.theme.Brand
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
    fun setMonth(m: YearMonth) = month.update { if (m > YearMonth.now()) YearMonth.now() else m }
}

private const val HIDDEN = "Rp ••••••"

@Composable
fun HomeScreen(nav: NavController) {
    val c = appContainer()
    val vm: HomeViewModel = viewModel { HomeViewModel(c) }
    val s by vm.state.collectAsStateWithLifecycle()

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { Header(s, vm::setMonth, nav) }
        item { BalanceCard(s, vm::toggleHide) { nav.navigate(Routes.WALLETS) } }

        s.settings.home.visible.forEach { section ->
            when (section) {
                HomeSection.REVIEW -> if (s.pendingCount > 0) item(key = section) { ReviewBanner(s.pendingCount) { nav.navigate(Routes.REVIEW) } }
                HomeSection.BUDGETS -> item(key = section) { BudgetCard(s, nav) }
                HomeSection.QUICK_ACTIONS -> item(key = section) { QuickActions(s.settings.autoCaptureEnabled, nav) }
                HomeSection.RECENT -> item(key = section) { Recent(s, nav) }
                HomeSection.SPLIT_BILL -> item(key = section) {
                    ShortcutCard(Icons.AutoMirrored.Outlined.ReceiptLong, stringResource(R.string.bill_title), stringResource(R.string.bill_home_hint)) { nav.navigate(Routes.BILLS) }
                }
                HomeSection.SCAN_RECEIPTS -> item(key = section) {
                    ShortcutCard(Icons.Outlined.DocumentScanner, stringResource(R.string.receipts_title), stringResource(R.string.receipts_home_hint)) { nav.navigate(Routes.RECEIPT_SCAN) }
                }
                HomeSection.IMPORT_STATEMENT -> item(key = section) {
                    ShortcutCard(Icons.Outlined.Description, stringResource(R.string.statement_title), stringResource(R.string.statement_home_hint)) { nav.navigate(Routes.STATEMENT_IMPORT) }
                }
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

/** "Good evening / November 2026 ⌄" with search and the review bell. */
@Composable
private fun Header(s: HomeState, onMonth: (YearMonth) -> Unit, nav: NavController) {
    var picking by remember { mutableStateOf(false) }
    val hour = LocalTime.now().hour
    val greeting = stringResource(
        when (hour) {
            in 4..10 -> R.string.greeting_morning
            in 11..17 -> R.string.greeting_afternoon
            else -> R.string.greeting_evening
        }
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(greeting, color = Brand.Muted, fontSize = 15.sp)
            Box {
                Row(
                    Modifier.clip(RoundedCornerShape(10.dp)).clickable(onClickLabel = stringResource(R.string.pick_month)) { picking = true },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(formatMonth(s.month.atDay(1)), style = MaterialTheme.typography.headlineMedium)
                    Icon(Icons.Default.KeyboardArrowDown, null, modifier = Modifier.padding(start = 4.dp).size(26.dp))
                }
                DropdownMenu(expanded = picking, onDismissRequest = { picking = false }) {
                    (0 until 12).map { YearMonth.now().minusMonths(it.toLong()) }.forEach { m ->
                        DropdownMenuItem(
                            text = { Text(formatMonth(m.atDay(1)), fontWeight = if (m == s.month) FontWeight.ExtraBold else FontWeight.Normal) },
                            onClick = { onMonth(m); picking = false },
                        )
                    }
                }
            }
        }
        RoundIconButton(Icons.Default.Search, stringResource(R.string.search), { nav.navigate(Routes.ACTIVITY) })
        Spacer(Modifier.size(10.dp))
        RoundIconButton(
            Icons.Outlined.Notifications, stringResource(R.string.notifications_review), { nav.navigate(Routes.REVIEW) },
            badge = s.pendingCount > 0,
        )
    }
}

/** The filled accent card: total balance, then the month's figures you picked. */
@Composable
private fun BalanceCard(s: HomeState, onToggleHide: () -> Unit, onWallets: () -> Unit) {
    val hide = s.settings.hideBalances
    Surface(color = Brand.Hero, contentColor = Brand.OnHero, shape = RoundedCornerShape(28.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 24.dp, vertical = 24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    pluralStringResource(R.plurals.total_balance_accounts, s.walletCount, s.walletCount), color = Brand.OnHero, fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f).clickable(onClick = onWallets),
                )
                IconButton(onClick = onToggleHide, modifier = Modifier.size(32.dp)) {
                    Icon(
                        if (hide) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                        stringResource(if (hide) R.string.show_balances else R.string.hide_balances),
                    )
                }
            }
            Text(
                if (hide) HIDDEN else Money.format(s.total), style = AmountStyle.copy(fontSize = 40.sp),
                maxLines = 1, modifier = Modifier.padding(top = 10.dp, bottom = 18.dp),
            )
            HorizontalDivider(color = Brand.OnHero.copy(alpha = 0.25f))
            val sum = s.summary
            val figures = s.settings.summaryFigures.mapNotNull { figure ->
                when (figure) {
                    SummaryFigure.INCOME -> Triple(Icons.Default.SouthWest, stringResource(R.string.income), Money.format(sum.income))
                    SummaryFigure.SPENT -> Triple(Icons.Default.NorthEast, stringResource(R.string.expense), Money.format(sum.spent))
                    SummaryFigure.NET -> Triple(null, stringResource(R.string.figure_net), signed(sum.net))
                    SummaryFigure.DAILY_AVERAGE -> sum.dailyAverage?.let { Triple(null, stringResource(R.string.figure_daily_average), Money.format(it)) }
                    SummaryFigure.VS_LAST_MONTH -> Triple(null, stringResource(R.string.figure_vs_last_month), signed(sum.vsLastMonth))
                }
            }
            figures.chunked(2).forEach { pair ->
                Row(Modifier.padding(top = 16.dp)) {
                    pair.forEach { (icon, label, value) -> Figure(icon, label, if (hide) HIDDEN else value, Modifier.weight(1f)) }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun Figure(icon: ImageVector?, label: String, value: String, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (icon != null) Icon(icon, null, tint = Brand.OnHeroMuted, modifier = Modifier.size(18.dp))
            Text(label, color = Brand.OnHeroMuted, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
        Text(value, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, maxLines = 1)
    }
}

private fun signed(amount: Long) = if (amount > 0) "+" + Money.format(amount) else Money.format(amount)

@Composable
private fun ReviewBanner(count: Int, onClick: () -> Unit) {
    Surface(onClick = onClick, color = Brand.AmberSoft, shape = RoundedCornerShape(CardRadius), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(12.dp), color = Brand.Amber, modifier = Modifier.size(40.dp)) {
                Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Bolt, null, tint = Brand.AmberSoft, modifier = Modifier.size(20.dp)) }
            }
            Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                Text(stringResource(R.string.review_banner_title, count), fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, color = Brand.AmberInk)
                Text(stringResource(R.string.review_banner_body), color = Brand.AmberInk, fontSize = 13.sp)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Brand.AmberInk)
        }
    }
}

/** "Monthly budget · 70% used · Rp 1.784.700 left · 22 days to go", or the category budgets closest to their limit. */
@Composable
private fun BudgetCard(s: HomeState, nav: NavController) {
    val hide = s.settings.hideBalances
    Surface(
        onClick = { nav.navigate(Routes.BUDGETS) }, color = Brand.Surface, shape = RoundedCornerShape(CardRadius),
        border = BorderStroke(1.dp, Brand.Border), modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val overall = s.overall
            if (overall != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.monthly_budget), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    Text(stringResource(R.string.budget_used, (overall.fraction * 100).toInt()), color = Brand.Muted, fontSize = 16.sp)
                }
                BudgetBar(overall.fraction, overall.nearLimit, overall.overBudget)
                Row {
                    val money = if (hide) HIDDEN else Money.format(kotlin.math.abs(overall.remaining))
                    Text(
                        stringResource(if (overall.overBudget) R.string.budget_over else R.string.budget_left, money),
                        fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f),
                        color = if (overall.overBudget) Brand.Expense else Brand.Text,
                    )
                    Text(pluralStringResource(R.plurals.days_to_go, overall.daysLeft, overall.daysLeft), color = Brand.Muted, fontSize = 15.sp)
                }
            } else {
                Text(stringResource(R.string.budgets), style = MaterialTheme.typography.titleLarge)
            }
            if (overall == null && s.budgets.isEmpty()) {
                Text(stringResource(R.string.no_budgets_hint), color = Brand.Muted, fontSize = 14.sp)
                Text(stringResource(R.string.create_budget), color = Brand.Accent, fontWeight = FontWeight.Bold, fontSize = 14.sp,
                    modifier = Modifier.clickable { nav.navigate(Routes.budget()) })
            }
            s.budgets.forEach { (b, p) -> MiniBudget(b, p) }
        }
    }
}

@Composable
private fun QuickActions(autoCapture: Boolean, nav: NavController) {
    // Same height for all three tiles, whichever label wraps the most.
    Row(Modifier.height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        // "Auto-capture · On": may wrap after the hyphen (never mid-word), with the state in the accent colour.
        val auto = buildAnnotatedString {
            append(stringResource(R.string.quick_auto).replace("-", "-​"))
            withStyle(SpanStyle(color = if (autoCapture) Brand.Accent else Brand.Muted)) {
                append(" · " + stringResource(if (autoCapture) R.string.state_on else R.string.state_off))
            }
        }
        QuickAction(Icons.Outlined.Edit, AnnotatedString(stringResource(R.string.quick_add)), Modifier.weight(1f)) { nav.navigate(Routes.tx()) }
        QuickAction(Icons.Outlined.DocumentScanner, AnnotatedString(stringResource(R.string.quick_scan)), Modifier.weight(1f)) { nav.navigate(Routes.RECEIPT_SCAN) }
        QuickAction(Icons.Outlined.Bolt, auto, Modifier.weight(1f)) {
            nav.navigate(if (autoCapture) Routes.CAPTURE_APPS else Routes.CAPTURE_SETUP)
        }
    }
}

@Composable
private fun QuickAction(icon: ImageVector, label: AnnotatedString, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick, color = Brand.Surface, shape = RoundedCornerShape(CardRadius),
        border = BorderStroke(1.dp, Brand.Border), modifier = modifier.fillMaxHeight().heightIn(min = 124.dp),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(Brand.AccentSoft), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = Brand.OnAccentSoft, modifier = Modifier.size(22.dp))
            }
            Text(label, fontWeight = FontWeight.Bold, fontSize = 15.sp, lineHeight = 19.sp, style = LocalTextStyle.current.copy(lineBreak = LineBreak.Paragraph))
        }
    }
}

@Composable
private fun ShortcutCard(icon: ImageVector, title: String, hint: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick, color = Brand.Surface, shape = RoundedCornerShape(CardRadius),
        border = BorderStroke(1.dp, Brand.Border), modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(Brand.AccentSoft), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = Brand.OnAccentSoft, modifier = Modifier.size(22.dp))
            }
            Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text(hint, color = Brand.Muted, fontSize = 13.sp)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Brand.Muted)
        }
    }
}

@Composable
private fun Recent(s: HomeState, nav: NavController) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle(stringResource(R.string.recent)) {
            Text(
                stringResource(R.string.see_all), color = Brand.Accent, fontWeight = FontWeight.Bold, fontSize = 16.sp,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { nav.navigate(Routes.ACTIVITY) }.padding(4.dp),
            )
        }
        if (s.recent.isEmpty()) {
            SectionCard { Text(stringResource(R.string.no_transactions_month), color = Brand.Muted, fontSize = 14.sp) }
        } else {
            TransactionGroup(s.recent, onClick = { nav.navigate(Routes.tx(it.id)) }, caption = { whenLabel(it.occurredAt) })
        }
    }
}

/** "08:12" today, "Yesterday", or "6 Nov". */
@Composable
fun whenLabel(ms: Long): String {
    val date = Periods.toDate(ms)
    val today = LocalDate.now()
    return when (date) {
        today -> DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(currentLocale())
            .format(Instant.ofEpochMilli(ms).atZone(Periods.zone()).toLocalTime())
        today.minusDays(1) -> stringResource(R.string.day_yesterday)
        else -> DateTimeFormatter.ofPattern("d MMM", currentLocale()).format(date)
    }
}

@Composable
private fun MiniBudget(b: BudgetEntity, p: BudgetProgress) {
    val c = appContainer()
    val name = rememberCategoryName(c, b.categoryId)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row {
            Text(name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text(stringResource(R.string.budget_used, (p.fraction * 100).toInt()), fontSize = 14.sp, color = Brand.Muted)
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
                        if (i > 0) HorizontalDivider(color = Brand.Border)
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
        HomeSection.QUICK_ACTIONS -> R.string.quick_actions
        HomeSection.SCAN_RECEIPTS -> R.string.receipts_title
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
