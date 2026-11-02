package com.example.moneyapp.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.moneyapp.AppContainer
import com.example.moneyapp.R
import com.example.moneyapp.data.db.CategoryEntity
import com.example.moneyapp.data.db.WalletEntity
import com.example.moneyapp.ui.categoryLabel
import com.example.moneyapp.ui.components.appContainer

/** All categories (archived included, for names); filter `!archived` for pickers. */
@Composable
fun rememberCategories(c: AppContainer = appContainer()): State<List<CategoryEntity>> {
    val flow = remember(c) { c.categories.observeAll() }
    return flow.collectAsStateWithLifecycle(emptyList())
}

@Composable
fun rememberWallets(c: AppContainer = appContainer()): State<List<WalletEntity>> {
    val flow = remember(c) { c.wallets.observeActive() }
    return flow.collectAsStateWithLifecycle(emptyList())
}

/** Category name for an id; null id = budget/filter over all categories. */
@Composable
fun rememberCategoryName(c: AppContainer, categoryId: Long?): String {
    if (categoryId == null) return stringResource(R.string.all_expenses)
    val categories by rememberCategories(c)
    val cat = categories.firstOrNull { it.id == categoryId }
    return if (cat == null) stringResource(R.string.uncategorized) else categoryLabel(cat)
}
