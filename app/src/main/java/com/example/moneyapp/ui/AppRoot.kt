@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.example.moneyapp.ui

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import com.example.moneyapp.R
import com.example.moneyapp.ui.screens.ActivityScreen
import com.example.moneyapp.ui.screens.BillDetailScreen
import com.example.moneyapp.ui.screens.BillEditScreen
import com.example.moneyapp.ui.screens.BillsScreen
import com.example.moneyapp.ui.screens.BudgetEditScreen
import com.example.moneyapp.ui.screens.BudgetsScreen
import com.example.moneyapp.ui.screens.CaptureSetupScreen
import com.example.moneyapp.ui.screens.CategoriesScreen
import com.example.moneyapp.ui.screens.CategoryEditScreen
import com.example.moneyapp.ui.screens.HomeScreen
import com.example.moneyapp.ui.screens.RecurringEditScreen
import com.example.moneyapp.ui.screens.RecurringScreen
import com.example.moneyapp.ui.screens.ReportsScreen
import com.example.moneyapp.ui.screens.ReviewScreen
import com.example.moneyapp.ui.screens.SettingsScreen
import com.example.moneyapp.ui.screens.StatementImportScreen
import com.example.moneyapp.ui.screens.TransactionEditScreen
import com.example.moneyapp.ui.screens.WalletEditScreen
import com.example.moneyapp.ui.screens.WalletsScreen
import com.example.moneyapp.ui.theme.Brand

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

    val tabs = setOf(HOME, ACTIVITY, BUDGETS, REPORTS)
}

private const val SCREEN_FADE_MS = 200

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
            // The library default is a 700 ms fade, which feels sluggish. 0 here means no animation.
            enterTransition = { fadeIn(tween(SCREEN_FADE_MS)) },
            exitTransition = { fadeOut(tween(SCREEN_FADE_MS)) },
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
            composable(Routes.BILLS) { BillsScreen(nav) }
            composable(Routes.BILL_NEW) { BillEditScreen(nav) }
            composable(Routes.BILL_DETAIL, listOf(idArg)) { BillDetailScreen(nav, it.arguments?.getLong("id") ?: 0L) }

            composable(Routes.TX_EDIT, listOf(idArg, typeArg)) {
                TransactionEditScreen(nav, it.arguments?.getLong("id") ?: 0L, it.arguments?.getString("type").orEmpty())
            }
            composable(Routes.WALLET_EDIT, listOf(idArg)) { WalletEditScreen(nav, it.arguments?.getLong("id") ?: 0L) }
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
    Tab(Routes.HOME, R.string.tab_home, Icons.Default.Home),
    Tab(Routes.ACTIVITY, R.string.tab_activity, Icons.AutoMirrored.Filled.List),
    Tab(Routes.BUDGETS, R.string.tab_budgets, Icons.Default.Savings),
    Tab(Routes.REPORTS, R.string.tab_reports, Icons.Default.PieChart),
)

@Composable
private fun BottomBar(current: String?, nav: NavHostController) {
    fun go(route: String) = nav.navigate(route) {
        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }

    Surface(color = Color.White) {
        Column(Modifier.windowInsetsPadding(WindowInsets.navigationBars)) {
            HorizontalDivider(color = Brand.Line)
            Row(
                Modifier.fillMaxWidth().height(68.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                tabs.take(2).forEach { TabItem(it, current == it.route) { go(it.route) } }
                AddButton { nav.navigate(Routes.tx()) }
                tabs.drop(2).forEach { TabItem(it, current == it.route) { go(it.route) } }
            }
        }
    }
}

@Composable
private fun TabItem(tab: Tab, selected: Boolean, onClick: () -> Unit) {
    val color = if (selected) Brand.Green else Brand.InkMuted
    Column(
        Modifier
            .widthIn(min = 64.dp)
            .selectable(selected = selected, onClick = onClick, role = Role.Tab)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(tab.icon, contentDescription = null, tint = color)
        Text(stringResource(tab.label), color = color, fontSize = 11.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold)
    }
}

@Composable
private fun AddButton(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = Brand.Green,
        shadowElevation = 6.dp,
        modifier = Modifier.size(56.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_transaction), tint = Color.White, modifier = Modifier.size(28.dp))
        }
    }
}
