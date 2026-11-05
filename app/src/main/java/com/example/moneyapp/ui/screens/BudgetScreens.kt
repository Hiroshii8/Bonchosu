@file:OptIn(ExperimentalLayoutApi::class)

package com.hiroshi.moneyapp.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.FilterChip
import com.hiroshi.moneyapp.core.BudgetAlerts
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import com.hiroshi.moneyapp.AppContainer
import com.hiroshi.moneyapp.R
import com.hiroshi.moneyapp.core.BudgetProgress
import com.hiroshi.moneyapp.core.Money
import com.hiroshi.moneyapp.data.db.BudgetEntity
import com.hiroshi.moneyapp.data.db.BudgetPeriod
import com.hiroshi.moneyapp.data.db.CategoryEntity
import com.hiroshi.moneyapp.data.db.CategoryType
import com.hiroshi.moneyapp.data.db.WalletEntity
import com.hiroshi.moneyapp.data.db.WalletType
import com.hiroshi.moneyapp.ui.Routes
import com.hiroshi.moneyapp.ui.budgetPeriodLabel
import com.hiroshi.moneyapp.ui.categoryLabel
import com.hiroshi.moneyapp.ui.components.AmountField
import com.hiroshi.moneyapp.ui.components.BudgetBar
import com.hiroshi.moneyapp.ui.components.CategoryBadge
import com.hiroshi.moneyapp.ui.components.ConfirmDialog
import com.hiroshi.moneyapp.ui.components.DropdownField
import com.hiroshi.moneyapp.ui.components.EmptyState
import com.hiroshi.moneyapp.ui.components.ScreenScaffold
import com.hiroshi.moneyapp.ui.components.SectionCard
import com.hiroshi.moneyapp.ui.components.SegmentedChoice
import com.hiroshi.moneyapp.ui.components.appContainer
import com.hiroshi.moneyapp.ui.theme.Brand
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// ---------- List ----------

class BudgetsViewModel(c: AppContainer) : ViewModel() {
    val items = c.budgets.observeProgress()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

@Composable
fun BudgetsScreen(nav: NavController) {
    val c = appContainer()
    val vm: BudgetsViewModel = viewModel { BudgetsViewModel(c) }
    val items by vm.items.collectAsStateWithLifecycle()
    val categories by rememberCategories(c)
    val wallets by rememberWallets(c)

    ScreenScaffold(
        title = stringResource(R.string.budgets),
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { nav.navigate(Routes.budget()) },
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text(stringResource(R.string.new_budget)) },
                containerColor = Brand.Hero,
                contentColor = androidx.compose.ui.graphics.Color.White,
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (items.isEmpty()) item { EmptyState(stringResource(R.string.no_budgets_hint)) }
            items(items, key = { it.first.id }) { (budget, progress) ->
                val cat = categories.firstOrNull { it.id == budget.categoryId }
                val wallet = wallets.firstOrNull { it.id == budget.walletId }
                BudgetCard(budget, progress, cat, wallet) { nav.navigate(Routes.budget(budget.id)) }
            }
        }
    }
}

@Composable
private fun BudgetCard(b: BudgetEntity, p: BudgetProgress, cat: CategoryEntity?, wallet: WalletEntity?, onClick: () -> Unit) {
    val name = if (b.categoryId == null) stringResource(R.string.all_expenses) else cat?.let { categoryLabel(it) } ?: stringResource(R.string.uncategorized)
    SectionCard(Modifier.clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CategoryBadge(name, cat?.colorIndex ?: 1, size = 38.dp)
            Column(Modifier.weight(1f)) {
                Text(name, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text(
                    budgetPeriodLabel(b.period) + " · " + (wallet?.name ?: stringResource(R.string.all_wallets)),
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(Money.format(p.limit), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        BudgetBar(p.fraction, p.nearLimit, p.overBudget)
        Row {
            Text(stringResource(R.string.spent_amount, Money.format(p.spent)), fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            val (text, color) = when {
                p.overBudget -> stringResource(R.string.over_by, Money.format(-p.remaining)) to MaterialTheme.colorScheme.error
                p.nearLimit -> stringResource(R.string.only_left, Money.format(p.remaining)) to Brand.Amber
                else -> stringResource(R.string.left_amount, Money.format(p.remaining)) to Brand.Ink
            }
            Text(text, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = color)
        }
        if (!p.overBudget) {
            Text(stringResource(R.string.per_day_left, Money.format(p.perDayLeft)), fontSize = 12.sp, color = Brand.Green, fontWeight = FontWeight.SemiBold)
        }
    }
}

// ---------- Edit ----------

data class BudgetForm(
    val loaded: Boolean = false,
    val id: Long = 0,
    val categoryId: Long? = null,
    val walletId: Long? = null,
    val digits: String = "",
    val period: BudgetPeriod = BudgetPeriod.MONTHLY,
    val alertLevels: List<Int> = BudgetAlerts.DEFAULT_LEVELS,
    val error: Int? = null,
)

class BudgetEditViewModel(private val c: AppContainer) : ViewModel() {
    private val _form = MutableStateFlow(BudgetForm())
    val form = _form.asStateFlow()

    fun load(id: Long) {
        if (_form.value.loaded) return
        viewModelScope.launch {
            val b = if (id > 0) c.budgets.get(id) else null
            _form.value = if (b == null) BudgetForm(loaded = true)
            else BudgetForm(true, b.id, b.categoryId, b.walletId, b.amount.toString(), b.period, BudgetAlerts.parseLevels(b.alertLevels))
        }
    }

    fun edit(change: (BudgetForm) -> BudgetForm) = _form.update { change(it).copy(error = null) }

    fun save(onDone: () -> Unit) {
        val f = _form.value
        val amount = f.digits.toLongOrNull() ?: 0
        if (amount <= 0) { _form.update { it.copy(error = R.string.error_amount) }; return }
        viewModelScope.launch {
            c.budgets.save(
                BudgetEntity(
                    id = f.id, categoryId = f.categoryId, walletId = f.walletId, amount = amount, period = f.period,
                    alertLevels = BudgetAlerts.formatLevels(f.alertLevels),
                )
            )
            onDone()
        }
    }

    fun delete(onDone: () -> Unit) = viewModelScope.launch {
        if (_form.value.id > 0) c.budgets.delete(_form.value.id)
        onDone()
    }
}

@Composable
fun BudgetEditScreen(nav: NavController, id: Long) {
    val c = appContainer()
    val vm: BudgetEditViewModel = viewModel { BudgetEditViewModel(c) }
    LaunchedEffect(id) { vm.load(id) }
    val f by vm.form.collectAsStateWithLifecycle()
    val categories by rememberCategories(c)
    val wallets by rememberWallets(c)
    var confirmDelete by remember { mutableStateOf(false) }
    val settingsFlow = remember(c) { c.settings.settings }
    val alertsOn = settingsFlow.collectAsStateWithLifecycle(null).value?.budgetAlerts == true

    // Sentinel entries meaning "all".
    val allCats = CategoryEntity(id = -1, name = stringResource(R.string.all_expenses), type = CategoryType.EXPENSE, colorIndex = 1)
    val allWallets = WalletEntity(id = -1, name = stringResource(R.string.all_wallets), type = WalletType.OTHER)

    ScreenScaffold(
        title = stringResource(if (id > 0) R.string.edit_budget else R.string.new_budget),
        onBack = { nav.popBackStack() },
        actions = { if (id > 0) IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, stringResource(R.string.delete)) } },
    ) { padding ->
        if (!f.loaded) return@ScreenScaffold
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            DropdownField(
                label = stringResource(R.string.category),
                options = listOf(allCats) + categories.filter { it.type == CategoryType.EXPENSE && !it.archived },
                selected = categories.firstOrNull { it.id == f.categoryId } ?: allCats,
                optionLabel = { if (it.id == -1L) it.name else categoryLabel(it) },
                onSelect = { cat -> vm.edit { it.copy(categoryId = cat.id.takeIf { id -> id > 0 }) } },
            )
            AmountField(f.digits, { d -> vm.edit { it.copy(digits = d) } }, label = stringResource(R.string.budget_limit))
            SegmentedChoice(BudgetPeriod.entries.reversed(), f.period, { budgetPeriodLabel(it) }, { p -> vm.edit { it.copy(period = p) } })
            DropdownField(
                label = stringResource(R.string.wallet),
                options = listOf(allWallets) + wallets,
                selected = wallets.firstOrNull { it.id == f.walletId } ?: allWallets,
                optionLabel = { it.name },
                onSelect = { w -> vm.edit { it.copy(walletId = w.id.takeIf { id -> id > 0 }) } },
            )
            Text(stringResource(R.string.budget_hint), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

            Text(stringResource(R.string.budget_alerts), style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BudgetAlerts.LEVEL_CHOICES.forEach { level ->
                    FilterChip(
                        selected = level in f.alertLevels,
                        onClick = { vm.edit { it.copy(alertLevels = if (level in it.alertLevels) it.alertLevels - level else it.alertLevels + level) } },
                        label = { Text("$level%") },
                    )
                }
            }
            Text(
                stringResource(if (alertsOn) R.string.budget_alerts_hint else R.string.budget_alerts_off_hint),
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            f.error?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error, fontSize = 13.sp) }
            Button(
                onClick = { vm.save { nav.popBackStack() } },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
            ) { Text(stringResource(R.string.save)) }
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = stringResource(R.string.delete_budget_q),
            text = stringResource(R.string.delete_budget_body),
            confirmLabel = stringResource(R.string.delete),
            onConfirm = { vm.delete { nav.popBackStack() } },
            onDismiss = { confirmDelete = false },
        )
    }
}
