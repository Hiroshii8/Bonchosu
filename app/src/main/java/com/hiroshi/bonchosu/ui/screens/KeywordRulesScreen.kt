package com.hiroshi.bonchosu.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.hiroshi.bonchosu.capture.Keywords
import com.hiroshi.bonchosu.data.db.CategoryEntity
import com.hiroshi.bonchosu.data.db.CategoryType
import com.hiroshi.bonchosu.data.db.KeywordRuleRow
import com.hiroshi.bonchosu.ui.categoryLabel
import com.hiroshi.bonchosu.ui.components.CategoryBadge
import com.hiroshi.bonchosu.ui.components.DropdownField
import com.hiroshi.bonchosu.ui.components.EmptyState
import com.hiroshi.bonchosu.ui.components.ScreenScaffold
import com.hiroshi.bonchosu.ui.components.SectionCard
import com.hiroshi.bonchosu.ui.components.appContainer
import com.hiroshi.bonchosu.ui.theme.Brand
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class KeywordForm(
    val keyword: String = "",
    val categoryId: Long? = null,
    /** After saving: the word, and how many review items it filled in. */
    val saved: Pair<String, Int>? = null,
)

class KeywordRulesViewModel(private val c: AppContainer) : ViewModel() {
    val rules = c.keywordRules.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val categories = c.categories.observeAll()
        .map { all -> all.filter { !it.archived }.sortedWith(compareBy({ it.type }, { it.name.lowercase() })) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val form = MutableStateFlow(KeywordForm())

    fun edit(change: (KeywordForm) -> KeywordForm) = form.update { change(it).copy(saved = null) }

    fun add() {
        val f = form.value
        val categoryId = f.categoryId ?: return
        val keyword = Keywords.normalize(f.keyword)
        if (keyword.isEmpty()) return
        viewModelScope.launch {
            val filled = c.keywordRules.add(keyword, categoryId)
            // Keep the category picked: people often add several words for one category in a row.
            form.value = KeywordForm(categoryId = categoryId, saved = keyword to filled)
        }
    }

    fun delete(rule: KeywordRuleRow) = viewModelScope.launch { c.keywordRules.delete(rule.id) }
}

@Composable
fun KeywordRulesScreen(nav: NavController) {
    val c = appContainer()
    val vm: KeywordRulesViewModel = viewModel { KeywordRulesViewModel(c) }
    val rules by vm.rules.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val f by vm.form.collectAsStateWithLifecycle()

    ScreenScaffold(title = stringResource(R.string.keywords_title), onBack = { nav.popBackStack() }) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).imePadding(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Text(stringResource(R.string.keywords_intro), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            item { AddRuleCard(f, categories, vm) }
            f.saved?.let { (word, filled) ->
                item {
                    Surface(color = Brand.AccentSoft, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                        Text(
                            if (filled > 0) pluralStringResource(R.plurals.keywords_saved_filled, filled, word, filled)
                            else stringResource(R.string.keywords_saved, word),
                            color = Brand.Accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(12.dp),
                        )
                    }
                }
            }
            item {
                Text(
                    stringResource(R.string.keywords_yours, rules.size),
                    style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 4.dp),
                )
            }
            if (rules.isEmpty()) {
                item { EmptyState(stringResource(R.string.keywords_empty)) }
            } else {
                item {
                    SectionCard(contentPadding = PaddingValues(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)) {
                        rules.forEachIndexed { i, rule ->
                            if (i > 0) HorizontalDivider(color = Brand.Border)
                            RuleRow(rule) { vm.delete(rule) }
                        }
                    }
                }
            }
            item { Text(stringResource(R.string.keywords_order_hint), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
private fun AddRuleCard(f: KeywordForm, categories: List<CategoryEntity>, vm: KeywordRulesViewModel) {
    SectionCard {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = f.keyword, onValueChange = { k -> vm.edit { it.copy(keyword = k) } },
                label = { Text(stringResource(R.string.keywords_word)) },
                placeholder = { Text(stringResource(R.string.keywords_word_example)) },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            val expense = stringResource(R.string.type_expense)
            val income = stringResource(R.string.type_income)
            DropdownField(
                label = stringResource(R.string.keywords_category),
                options = categories,
                selected = categories.firstOrNull { it.id == f.categoryId },
                optionLabel = { "${categoryLabel(it)} · ${if (it.type == CategoryType.INCOME) income else expense}" },
                onSelect = { cat -> vm.edit { it.copy(categoryId = cat.id) } },
            )
            Button(
                onClick = vm::add,
                enabled = f.keyword.isNotBlank() && f.categoryId != null,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) { Text(stringResource(R.string.keywords_add)) }
        }
    }
}

@Composable
private fun RuleRow(rule: KeywordRuleRow, onDelete: () -> Unit) {
    val category = categoryLabel(rule.categoryName, rule.categoryKey)
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        CategoryBadge(category, rule.categoryColor, size = 36.dp)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text("“${rule.keyword}”", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(stringResource(R.string.keywords_goes_to, category), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, stringResource(R.string.delete), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}
