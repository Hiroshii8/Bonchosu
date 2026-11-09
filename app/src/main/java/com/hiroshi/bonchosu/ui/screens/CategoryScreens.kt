@file:OptIn(ExperimentalLayoutApi::class)

package com.hiroshi.bonchosu.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import com.hiroshi.bonchosu.data.db.CategoryEntity
import com.hiroshi.bonchosu.data.db.CategoryType
import com.hiroshi.bonchosu.ui.Routes
import com.hiroshi.bonchosu.ui.categoryLabel
import com.hiroshi.bonchosu.ui.components.CategoryBadge
import com.hiroshi.bonchosu.ui.components.ConfirmDialog
import com.hiroshi.bonchosu.ui.components.ScreenScaffold
import com.hiroshi.bonchosu.ui.components.SectionCard
import com.hiroshi.bonchosu.ui.components.SegmentedChoice
import com.hiroshi.bonchosu.ui.components.appContainer
import com.hiroshi.bonchosu.ui.theme.Brand
import com.hiroshi.bonchosu.ui.theme.CategoryPalette
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Composable
private fun typeLabel(t: CategoryType) = stringResource(if (t == CategoryType.EXPENSE) R.string.type_expense else R.string.type_income)

@Composable
fun CategoriesScreen(nav: NavController) {
    val c = appContainer()
    val categories by rememberCategories(c)
    var type by rememberSaveable { mutableStateOf(CategoryType.EXPENSE) }
    val shown = categories.filter { it.type == type && !it.archived }

    ScreenScaffold(
        title = stringResource(R.string.categories),
        onBack = { nav.popBackStack() },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { nav.navigate(Routes.category(type = type.name)) },
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text(stringResource(R.string.add_category)) },
                containerColor = Brand.Hero, contentColor = Brand.OnHero,
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { SegmentedChoice(CategoryType.entries, type, { typeLabel(it) }, { type = it }) }
            item {
                SectionCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
                    shown.forEachIndexed { i, cat ->
                        if (i > 0) HorizontalDivider(color = Brand.Border)
                        val label = categoryLabel(cat)
                        Row(
                            Modifier.fillMaxWidth().clickable { nav.navigate(Routes.category(cat.id)) }.padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            CategoryBadge(label, cat.colorIndex, size = 36.dp)
                            Text(label, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            if (cat.builtinKey != null) {
                                Text(stringResource(R.string.builtin), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

data class CategoryForm(
    val loaded: Boolean = false,
    val original: CategoryEntity? = null,
    val name: String = "",
    val type: CategoryType = CategoryType.EXPENSE,
    val colorIndex: Int = 0,
    /** Name as first shown (translated, for built-ins), to detect a rename. */
    val shownName: String = "",
    val error: Int? = null,
)

class CategoryEditViewModel(private val c: AppContainer) : ViewModel() {
    private val _form = MutableStateFlow(CategoryForm())
    val form = _form.asStateFlow()

    /** [displayName] = the translated name, so a built-in category edits from what the user sees. */
    fun load(id: Long, initialType: String, displayName: (CategoryEntity) -> String) {
        if (_form.value.loaded) return
        viewModelScope.launch {
            val cat = if (id > 0) c.categories.get(id) else null
            _form.value = if (cat == null) CategoryForm(
                loaded = true,
                type = CategoryType.entries.firstOrNull { it.name == initialType } ?: CategoryType.EXPENSE,
            ) else CategoryForm(true, cat, displayName(cat), cat.type, cat.colorIndex, shownName = displayName(cat))
        }
    }

    fun edit(change: (CategoryForm) -> CategoryForm) = _form.update { change(it).copy(error = null) }

    fun save(onDone: () -> Unit) {
        val f = _form.value
        if (f.name.isBlank()) { _form.update { it.copy(error = R.string.error_name) }; return }
        viewModelScope.launch {
            val o = f.original
            val renamed = o?.builtinKey != null && f.name.trim() != f.shownName
            val entity = (o ?: CategoryEntity(name = f.name.trim(), type = f.type)).copy(
                name = f.name.trim(), type = f.type, colorIndex = f.colorIndex,
                // Renaming a built-in turns it into a normal category so your name is shown in every language.
                builtinKey = if (renamed) null else o?.builtinKey,
            )
            c.categories.save(entity)
            onDone()
        }
    }

    fun delete(onDone: () -> Unit) = viewModelScope.launch {
        _form.value.original?.let { c.categories.remove(it) }
        onDone()
    }
}

@Composable
fun CategoryEditScreen(nav: NavController, id: Long, initialType: String) {
    val c = appContainer()
    val vm: CategoryEditViewModel = viewModel { CategoryEditViewModel(c) }
    val categories by rememberCategories(c)
    val existing = categories.firstOrNull { it.id == id }
    val existingLabel = existing?.let { categoryLabel(it) }
    LaunchedEffect(id, existingLabel) {
        if (id == 0L) vm.load(0, initialType) { it.name }
        else if (existingLabel != null) vm.load(id, initialType) { existingLabel }
    }
    val f by vm.form.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }

    ScreenScaffold(
        title = stringResource(if (id > 0) R.string.edit_category else R.string.add_category),
        onBack = { nav.popBackStack() },
        actions = { if (id > 0) IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, stringResource(R.string.delete)) } },
    ) { padding ->
        if (!f.loaded) return@ScreenScaffold
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CategoryBadge(f.name.ifBlank { "?" }, f.colorIndex, size = 48.dp)
                OutlinedTextField(
                    value = f.name, onValueChange = { n -> vm.edit { it.copy(name = n) } },
                    label = { Text(stringResource(R.string.name)) }, singleLine = true, modifier = Modifier.weight(1f),
                )
            }
            if (id == 0L) SegmentedChoice(CategoryType.entries, f.type, { typeLabel(it) }, { t -> vm.edit { it.copy(type = t) } })

            Text(stringResource(R.string.color), style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                CategoryPalette.pairs.forEachIndexed { i, (tint, ink) ->
                    val selected = i == f.colorIndex
                    val cd = stringResource(R.string.color_n, i + 1)
                    Surface(
                        onClick = { vm.edit { it.copy(colorIndex = i) } },
                        shape = CircleShape, color = tint,
                        border = BorderStroke(2.dp, if (selected) ink else Color.Transparent),
                        modifier = Modifier.size(44.dp).semantics { contentDescription = cd },
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (selected) Icon(Icons.Default.Check, null, tint = ink)
                        }
                    }
                }
            }
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
            title = stringResource(R.string.delete_category_q),
            text = stringResource(R.string.delete_category_body),
            confirmLabel = stringResource(R.string.delete),
            onConfirm = { vm.delete { nav.popBackStack() } },
            onDismiss = { confirmDelete = false },
        )
    }
}
