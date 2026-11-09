package com.hiroshi.bonchosu.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
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
import com.hiroshi.bonchosu.core.Money
import com.hiroshi.bonchosu.data.db.TxSource
import com.hiroshi.bonchosu.data.settings.AccentColor
import com.hiroshi.bonchosu.data.settings.AppSettings
import com.hiroshi.bonchosu.data.settings.ThemeMode
import com.hiroshi.bonchosu.data.settings.UiSize
import com.hiroshi.bonchosu.ui.components.SegmentedChoice
import com.hiroshi.bonchosu.ui.categoryLabel
import com.hiroshi.bonchosu.ui.components.CategoryBadge
import com.hiroshi.bonchosu.ui.components.Overline
import com.hiroshi.bonchosu.ui.components.ScreenScaffold
import com.hiroshi.bonchosu.ui.components.SourceChip
import com.hiroshi.bonchosu.ui.components.appContainer
import com.hiroshi.bonchosu.ui.theme.AmountStyle
import com.hiroshi.bonchosu.ui.theme.Brand
import com.hiroshi.bonchosu.ui.theme.CardRadius
import com.hiroshi.bonchosu.ui.theme.accentSwatch
import com.hiroshi.bonchosu.ui.theme.dynamicColorSupported
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AppearanceViewModel(private val c: AppContainer) : ViewModel() {
    val settings = c.settings.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    // Each change is saved at once and the whole app re-colours straight away.
    fun setMode(mode: ThemeMode) = viewModelScope.launch { c.settings.setTheme(mode) }
    fun setAmoled(on: Boolean) = viewModelScope.launch { c.settings.setAmoled(on) }
    fun setDynamic(on: Boolean) = viewModelScope.launch { c.settings.setDynamicColor(on) }
    fun setAccent(accent: AccentColor) = viewModelScope.launch { c.settings.setAccent(accent) }
    fun setSize(size: UiSize) = viewModelScope.launch { c.settings.setUiSize(size) }
}

@Composable
fun AppearanceScreen(nav: NavController) {
    val c = appContainer()
    val vm: AppearanceViewModel = viewModel { AppearanceViewModel(c) }
    val s by vm.settings.collectAsStateWithLifecycle()

    ScreenScaffold(title = stringResource(R.string.appearance), onBack = { nav.popBackStack() }) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item { LivePreview() }

            item { Overline(stringResource(R.string.theme), Modifier.padding(top = 10.dp)) }
            item {
                Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ThemeMode.entries.forEach { mode ->
                        ThemeCard(mode, s.theme == mode, Modifier.weight(1f)) { vm.setMode(mode) }
                    }
                }
            }
            item {
                Surface(
                    shape = RoundedCornerShape(CardRadius), color = Brand.Surface, border = BorderStroke(1.dp, Brand.Border),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(horizontal = 18.dp)) {
                        ToggleRow(stringResource(R.string.amoled_title), stringResource(R.string.amoled_hint), s.amoled, true, vm::setAmoled)
                        HorizontalDivider(color = Brand.Border)
                        ToggleRow(
                            stringResource(R.string.dynamic_title),
                            stringResource(if (dynamicColorSupported) R.string.dynamic_hint else R.string.dynamic_unsupported),
                            s.dynamicColor && dynamicColorSupported, dynamicColorSupported, vm::setDynamic,
                        )
                    }
                }
            }

            item { Overline(stringResource(R.string.ui_size), Modifier.padding(top = 10.dp)) }
            item {
                SegmentedChoice(
                    options = UiSize.entries,
                    selected = s.uiSize,
                    label = { size ->
                        stringResource(
                            when (size) {
                                UiSize.SMALLER -> R.string.size_smaller
                                UiSize.SMALL -> R.string.size_small
                                UiSize.DEFAULT -> R.string.size_default
                                UiSize.LARGE -> R.string.size_large
                            }
                        )
                    },
                    onSelect = vm::setSize,
                )
            }
            item { Text(stringResource(R.string.size_hint), color = Brand.Muted, fontSize = 15.sp) }
            item { Overline(stringResource(R.string.accent_color), Modifier.padding(top = 10.dp)) }
            item {
                val usingWallpaper = s.dynamicColor && dynamicColorSupported
                Row(
                    Modifier.selectableGroup().alpha(if (usingWallpaper) 0.4f else 1f),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    AccentColor.entries.forEach { accent ->
                        AccentDot(accent, selected = !usingWallpaper && s.accent == accent, enabled = !usingWallpaper) { vm.setAccent(accent) }
                    }
                }
            }
            item {
                Text(
                    stringResource(if (s.dynamicColor && dynamicColorSupported) R.string.accent_dynamic_hint else R.string.accent_hint),
                    color = Brand.Muted, fontSize = 15.sp,
                )
            }
        }
    }
}

/** A small Home: the balance card and one transaction, in whatever theme is applied right now. */
@Composable
private fun LivePreview() {
    Surface(
        shape = RoundedCornerShape(28.dp), color = Brand.Surface, border = BorderStroke(1.dp, Brand.Border),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Overline(stringResource(R.string.live_preview))
            Surface(shape = RoundedCornerShape(20.dp), color = Brand.Hero, contentColor = Brand.OnHero, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(horizontal = 18.dp, vertical = 16.dp), verticalAlignment = Alignment.Bottom) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.total_balance), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text(Money.format(12_480_500), style = AmountStyle.copy(fontSize = 26.sp), maxLines = 1)
                    }
                    Text(stringResource(R.string.preview_budget, 70), fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CategoryBadge("Kopi Kenangan", 0, size = 40.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("Kopi Kenangan", fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        SourceChip(TxSource.NOTIFICATION, pending = false, walletName = "BCA")
                        Text(categoryLabel(null, "food"), color = Brand.Muted, fontSize = 13.sp, maxLines = 1)
                    }
                }
                Text(Money.format(-28_000), fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
            }
        }
    }
}

/** System / Light / Dark, each with a little two-tone swatch. */
@Composable
private fun ThemeCard(mode: ThemeMode, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val (left, right) = when (mode) {
        ThemeMode.SYSTEM -> Color(0xFFFFFFFF) to Color(0xFF0E1114)
        ThemeMode.LIGHT -> Color(0xFFFFFFFF) to Color(0xFFEEF0F3)
        ThemeMode.DARK -> Color(0xFF171B20) to Color(0xFF000000)
    }
    Surface(
        shape = RoundedCornerShape(CardRadius), color = Brand.Surface,
        border = BorderStroke(if (selected) 3.dp else 1.dp, if (selected) Brand.Accent else Brand.Border),
        modifier = modifier.selectable(selected = selected, onClick = onClick, role = Role.RadioButton),
    ) {
        Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                Modifier.fillMaxWidth().aspectRatio(1.75f).clip(RoundedCornerShape(12.dp))
                    .border(1.dp, Brand.Border, RoundedCornerShape(12.dp)),
            ) {
                Box(Modifier.weight(1f).fillMaxHeight().background(left))
                Box(Modifier.weight(1f).fillMaxHeight().background(right))
            }
            Text(
                stringResource(
                    when (mode) {
                        ThemeMode.SYSTEM -> R.string.theme_system_short
                        ThemeMode.LIGHT -> R.string.theme_light
                        ThemeMode.DARK -> R.string.theme_dark
                    }
                ),
                fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, maxLines = 1,
            )
        }
    }
}

@Composable
private fun ToggleRow(title: String, hint: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onChange)
            .padding(vertical = 16.dp)
            .alpha(if (enabled) 1f else 0.5f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Text(hint, color = Brand.Muted, fontSize = 14.sp)
        }
        // The whole row toggles; the switch only shows the state.
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

@Composable
private fun AccentDot(accent: AccentColor, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val name = stringResource(
        when (accent) {
            AccentColor.EMERALD -> R.string.accent_emerald
            AccentColor.BLUE -> R.string.accent_blue
            AccentColor.VIOLET -> R.string.accent_violet
            AccentColor.ORANGE -> R.string.accent_orange
        }
    )
    val swatch = accentSwatch(accent, Brand.isDark)
    Box(
        Modifier.size(64.dp).clip(CircleShape)
            .border(if (selected) 3.dp else 0.dp, if (selected) swatch else Color.Transparent, CircleShape)
            .padding(6.dp)
            .clip(CircleShape)
            .background(swatch)
            .selectable(selected = selected, enabled = enabled, onClick = onClick, role = Role.RadioButton)
            .semantics { contentDescription = name },
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Icon(Icons.Default.Check, null, tint = if (Brand.isDark) Color(0xFF111418) else Color.White, modifier = Modifier.size(26.dp))
    }
}
