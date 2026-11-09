@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.hiroshi.bonchosu.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.Home
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.hiroshi.bonchosu.R
import com.hiroshi.bonchosu.ui.screens.ActivityScreen
import com.hiroshi.bonchosu.ui.screens.AppearanceScreen
import com.hiroshi.bonchosu.ui.screens.BackupScreen
import com.hiroshi.bonchosu.ui.screens.BillDetailScreen
import com.hiroshi.bonchosu.ui.screens.BillEditScreen
import com.hiroshi.bonchosu.ui.screens.BillsScreen
import com.hiroshi.bonchosu.ui.screens.BudgetEditScreen
import com.hiroshi.bonchosu.ui.screens.BudgetsScreen
import com.hiroshi.bonchosu.ui.screens.CaptureAppsScreen
import com.hiroshi.bonchosu.ui.screens.CaptureSetupScreen
import com.hiroshi.bonchosu.ui.screens.CategoriesScreen
import com.hiroshi.bonchosu.ui.screens.CategoryEditScreen
import com.hiroshi.bonchosu.ui.screens.HomeCustomizeScreen
import com.hiroshi.bonchosu.ui.screens.HomeScreen
import com.hiroshi.bonchosu.ui.screens.KeywordRulesScreen
import com.hiroshi.bonchosu.ui.screens.RecurringEditScreen
import com.hiroshi.bonchosu.ui.screens.RecurringScreen
import com.hiroshi.bonchosu.ui.screens.ReportsScreen
import com.hiroshi.bonchosu.ui.screens.ReviewScreen
import com.hiroshi.bonchosu.ui.screens.SettingsScreen
import com.hiroshi.bonchosu.ui.screens.StatementImportScreen
import com.hiroshi.bonchosu.ui.screens.TransactionEditScreen
import com.hiroshi.bonchosu.ui.screens.WalletEditScreen
import com.hiroshi.bonchosu.ui.screens.WalletsScreen
import com.hiroshi.bonchosu.ui.theme.Brand

/** Route strings and builders, in one place. */
object Routes {
    const val HOME = "home"
    const val ACTIVITY = "activity"
    const val BUDGETS = "budgets"
    const val REPORTS = "reports"
    const val REVIEW = "review"
    const val SETTINGS = "settings"
    const val WALLETS = "wallets"
    const val CATEGORIES = "categories"
    const val RECURRING = "recurring"
    const val CAPTURE_SETUP = "capture-setup"
    const val STATEMENT_IMPORT = "statement-import"
    const val HOME_CUSTOMIZE = "home-customize"
    const val CAPTURE_APPS = "capture-apps"
    const val BACKUP = "backup"
    const val RECEIPT_SCAN = "receipt-scan"
    const val KEYWORD_RULES = "keyword-rules"
    const val APPEARANCE = "appearance"
    const val BILLS = "bills"
    const val BILL_NEW = "bill-new"
    const val BILL_DETAIL = "bill?id={id}"

    fun bill(id: Long) = "bill?id=$id"

    const val TX_EDIT = "tx?id={id}&type={type}"
    const val WALLET_EDIT = "wallet?id={id}"
    const val CATEGORY_EDIT = "category?id={id}&type={type}"
    const val BUDGET_EDIT = "budget?id={id}"
    const val RECURRING_EDIT = "recurring-edit?id={id}"

    fun tx(id: Long = 0, type: String = "") = "tx?id=$id&type=$type"
    fun wallet(id: Long = 0) = "wallet?id=$id"
    fun category(id: Long = 0, type: String = "") = "category?id=$id&type=$type"
    fun budget(id: Long = 0) = "budget?id=$id"
    fun recurring(id: Long = 0) = "recurring-edit?id=$id"

    val tabs = setOf(HOME, ACTIVITY, REPORTS, SETTINGS)
}


private val idArg = navArgument("id") { type = NavType.LongType; defaultValue = 0L }
private val typeArg = navArgument("type") { type = NavType.StringType; defaultValue = "" }

@Composable
fun AppRoot() {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route

    Scaffold(
        bottomBar = { if (route in Routes.tabs) BottomBar(route, nav) },
        contentWindowInsets = WindowInsets(0),
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = Routes.HOME,
            modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding),
            // Material motion: fade through between tabs, shared axis into pages, a rising sheet for add/edit.
            enterTransition = Motion.enter(Routes.TX_EDIT),
            exitTransition = Motion.exit(Routes.TX_EDIT),
            popEnterTransition = Motion.popEnter(Routes.TX_EDIT),
            popExitTransition = Motion.popExit(Routes.TX_EDIT),
        ) {
            composable(Routes.HOME) { HomeScreen(nav) }
            composable(Routes.ACTIVITY) { ActivityScreen(nav) }
            composable(Routes.BUDGETS) { BudgetsScreen(nav) }
            composable(Routes.REPORTS) { ReportsScreen() }
            composable(Routes.REVIEW) { ReviewScreen(nav) }
            composable(Routes.SETTINGS) { SettingsScreen(nav) }
            composable(Routes.WALLETS) { WalletsScreen(nav) }
            composable(Routes.CATEGORIES) { CategoriesScreen(nav) }
            composable(Routes.RECURRING) { RecurringScreen(nav) }
            composable(Routes.CAPTURE_SETUP) { CaptureSetupScreen(nav) }
            composable(Routes.STATEMENT_IMPORT) { StatementImportScreen(nav) }
            composable(Routes.HOME_CUSTOMIZE) { HomeCustomizeScreen(nav) }
            composable(Routes.CAPTURE_APPS) { CaptureAppsScreen(nav) }
            composable(Routes.BACKUP) { BackupScreen(nav) }
            composable(Routes.RECEIPT_SCAN) { StatementImportScreen(nav, receipts = true) }
            composable(Routes.KEYWORD_RULES) { KeywordRulesScreen(nav) }
            composable(Routes.APPEARANCE) { AppearanceScreen(nav) }
            composable(Routes.BILLS) { BillsScreen(nav) }
            composable(Routes.BILL_NEW) { BillEditScreen(nav) }
            composable(Routes.BILL_DETAIL, listOf(idArg)) { BillDetailScreen(nav, it.arguments?.getLong("id") ?: 0L) }

            composable(Routes.TX_EDIT, listOf(idArg, typeArg)) {
                TransactionEditScreen(nav, it.arguments?.getLong("id") ?: 0L, it.arguments?.getString("type").orEmpty())
            }
            composable(Routes.WALLET_EDIT, listOf(idArg)) {
                WalletEditScreen(nav, it.arguments?.getLong("id") ?: 0L)
            }
            composable(Routes.CATEGORY_EDIT, listOf(idArg, typeArg)) {
                CategoryEditScreen(nav, it.arguments?.getLong("id") ?: 0L, it.arguments?.getString("type").orEmpty())
            }
            composable(Routes.BUDGET_EDIT, listOf(idArg)) { BudgetEditScreen(nav, it.arguments?.getLong("id") ?: 0L) }
            composable(Routes.RECURRING_EDIT, listOf(idArg)) { RecurringEditScreen(nav, it.arguments?.getLong("id") ?: 0L) }
        }
    }
}

private data class Tab(val route: String, val label: Int, val icon: ImageVector)

private val tabs = listOf(
    Tab(Routes.HOME, R.string.tab_home, Icons.Outlined.Home),
    Tab(Routes.ACTIVITY, R.string.tab_activity, Icons.AutoMirrored.Filled.List),
    Tab(Routes.REPORTS, R.string.tab_reports, Icons.AutoMirrored.Filled.ShowChart),
    Tab(Routes.SETTINGS, R.string.tab_settings, Icons.Default.Tune),
)

@Composable
private fun BottomBar(current: String?, nav: NavHostController) {
    // Always land on the tab's main page: no saving or restoring whatever was open inside it.
    fun go(route: String) {
        // Already on this tab: stay put. Navigating again would pop and re-open the same page, replaying the transition.
        if (route == current) return
        nav.navigate(route) {
            popUpTo(nav.graph.findStartDestination().id)
            launchSingleTop = true
        }
    }

    // The + button sits half above the bar, ringed in the page colour, like a notch.
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Surface(color = Brand.Surface, modifier = Modifier.padding(top = 22.dp)) {
            Column(Modifier.windowInsetsPadding(WindowInsets.navigationBars)) {
                HorizontalDivider(color = Brand.Border)
                Row(
                    Modifier.fillMaxWidth().height(72.dp).padding(horizontal = 4.dp).selectableGroup(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    tabs.take(2).forEach { TabItem(it, current == it.route, Modifier.weight(1f)) { go(it.route) } }
                    Spacer(Modifier.weight(1f))
                    tabs.drop(2).forEach { TabItem(it, current == it.route, Modifier.weight(1f)) { go(it.route) } }
                }
            }
        }
        AddButton { nav.navigate(Routes.tx()) }
    }
}

@Composable
private fun TabItem(tab: Tab, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val color = if (selected) Brand.Accent else Brand.Muted
    Column(
        modifier
            .selectable(selected = selected, onClick = onClick, role = Role.Tab)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(tab.icon, contentDescription = null, tint = color, modifier = Modifier.size(26.dp))
        Text(stringResource(tab.label), color = color, fontSize = 13.sp, fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
private fun AddButton(onClick: () -> Unit) {
    Surface(shape = CircleShape, color = Brand.Bg, modifier = Modifier.size(76.dp)) {
        Box(contentAlignment = Alignment.Center) {
            Surface(onClick = onClick, shape = CircleShape, color = Brand.Accent, modifier = Modifier.size(64.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_transaction), tint = Brand.OnAccent, modifier = Modifier.size(32.dp))
                }
            }
        }
    }
}
