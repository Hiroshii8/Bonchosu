@file:OptIn(ExperimentalMaterial3Api::class)

package com.hiroshi.bonchosu.ui.components

import android.text.format.DateFormat
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import com.hiroshi.bonchosu.data.db.TxStatus
import com.hiroshi.bonchosu.ui.theme.CardRadius
import com.hiroshi.bonchosu.ui.theme.OverlineStyle
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hiroshi.bonchosu.AppContainer
import com.hiroshi.bonchosu.BonchosuApplication
import com.hiroshi.bonchosu.R
import com.hiroshi.bonchosu.core.Money
import com.hiroshi.bonchosu.data.db.TransactionRow
import com.hiroshi.bonchosu.data.db.TxSource
import com.hiroshi.bonchosu.data.db.TxType
import com.hiroshi.bonchosu.ui.categoryLabel
import com.hiroshi.bonchosu.ui.theme.Brand
import com.hiroshi.bonchosu.ui.theme.CategoryPalette
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

// ---------- Plumbing ----------

@Composable
fun appContainer(): AppContainer = (LocalContext.current.applicationContext as BonchosuApplication).container

@Composable
fun currentLocale(): Locale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()

@Composable
fun formatDate(date: LocalDate): String =
    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(currentLocale()).format(date)

@Composable
fun formatDayHeader(date: LocalDate): String =
    DateTimeFormatter.ofPattern("EEEE, d MMMM", currentLocale()).format(date)

@Composable
fun formatMonth(date: LocalDate): String =
    DateTimeFormatter.ofPattern("MMMM yyyy", currentLocale()).format(date)

// ---------- Layout ----------

/** Standard screen: a round back button, a big bold title, optional actions and FAB. */
@Composable
fun ScreenScaffold(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        topBar = { ScreenTopBar(title, onBack, actions) },
        floatingActionButton = floatingActionButton,
        containerColor = MaterialTheme.colorScheme.background,
        content = content,
    )
}

@Composable
fun ScreenTopBar(title: String, onBack: (() -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (onBack != null) RoundIconButton(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back), onBack)
        Text(
            title, style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), content = actions)
    }
}

/** 48dp circle with a thin border: back, search, bell, filter. */
@Composable
fun RoundIconButton(icon: ImageVector, contentDescription: String?, onClick: () -> Unit, modifier: Modifier = Modifier, badge: Boolean = false) {
    Box(modifier) {
        Surface(
            onClick = onClick, shape = CircleShape, color = Brand.Surface,
            border = BorderStroke(1.dp, Brand.Border), modifier = Modifier.size(48.dp),
        ) {
            Box(contentAlignment = Alignment.Center) { Icon(icon, contentDescription, tint = Brand.Text, modifier = Modifier.size(22.dp)) }
        }
        if (badge) Box(Modifier.align(Alignment.TopEnd).padding(top = 6.dp, end = 6.dp).size(10.dp).clip(CircleShape).background(Brand.Amber))
    }
}

@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    color: Color = Brand.Surface,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(CardRadius), color = color,
        border = if (color == Brand.Surface) BorderStroke(1.dp, Brand.Border) else null,
    ) {
        Column(Modifier.padding(contentPadding), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, trailing: @Composable () -> Unit = {}) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f).semantics { heading() })
        trailing()
    }
}

/** Small upper-case label above a group ("THEME", "ACCENT COLOR"). */
@Composable
fun Overline(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(currentLocale()), style = OverlineStyle, color = Brand.Muted, modifier = modifier.semantics { heading() })
}

/** Pill-shaped filter chip: filled with the accent when selected. */
@Composable
fun FilterPill(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick, shape = CircleShape,
        color = if (selected) Brand.Accent else Brand.Surface,
        contentColor = if (selected) Brand.OnAccent else Brand.Text,
        border = if (selected) null else BorderStroke(1.dp, Brand.Border),
        modifier = modifier.height(40.dp).semantics { this.selected = selected },
    ) {
        Box(Modifier.padding(horizontal = 18.dp), contentAlignment = Alignment.Center) {
            Text(label, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1)
        }
    }
}

/** The main action of a screen: full width, accent filled. */
@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(
        onClick = onClick, enabled = enabled, shape = RoundedCornerShape(18.dp),
        modifier = modifier.fillMaxWidth().height(56.dp),
    ) { Text(text, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold) }
}

@Composable
fun EmptyState(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
    }
}

// ---------- Money & categories ----------

/** Rounded square in the category's fixed colour, with its initial in white. */
@Composable
fun CategoryBadge(label: String, colorIndex: Int?, size: Dp = 44.dp) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(size * 0.3f)).background(CategoryPalette.tint(colorIndex)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label.take(1).uppercase(),
            color = CategoryPalette.ink(colorIndex),
            fontWeight = FontWeight.ExtraBold,
            fontSize = (size.value * 0.38f).sp,
        )
    }
}

/** Where a transaction came from: AUTO · BCA, SCAN, MANUAL, REPEAT, or REVIEW while it waits for you. */
@Composable
fun SourceChip(source: TxSource, pending: Boolean, walletName: String) {
    val review = pending
    val text = when {
        review -> stringResource(R.string.chip_review)
        source == TxSource.NOTIFICATION -> stringResource(R.string.chip_auto, walletName)
        source == TxSource.IMPORT || source == TxSource.RECEIPT -> stringResource(R.string.chip_scan)
        source == TxSource.RECURRING -> stringResource(R.string.chip_repeat)
        else -> stringResource(R.string.chip_manual)
    }
    Surface(shape = RoundedCornerShape(8.dp), color = if (review) Brand.AmberSoft else Brand.Surface2) {
        Text(
            text.uppercase(currentLocale()), color = if (review) Brand.AmberInk else Brand.Muted,
            fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.3.sp, maxLines = 1,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
        )
    }
}

@Composable
fun amountColor(type: TxType): Color = when (type) {
    TxType.INCOME -> Brand.Income
    TxType.EXPENSE -> Brand.Text
    TxType.TRANSFER -> Brand.Muted
}

fun signedAmount(type: TxType, amount: Long): String = when (type) {
    TxType.INCOME -> "+" + Money.format(amount)
    TxType.EXPENSE -> Money.format(-amount)
    TxType.TRANSFER -> Money.format(amount)
}

/**
 * One transaction: badge, title, source chip + category, amount.
 * [caption] goes under the amount (a time, or "Yesterday"); [showWallet] adds the wallet after the category.
 */
@Composable
fun TransactionItem(
    row: TransactionRow,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    caption: String? = null,
    showWallet: Boolean = false,
) {
    val title = when {
        row.type == TxType.TRANSFER -> stringResource(R.string.transfer_title, row.walletName, row.toWalletName ?: "?")
        row.note.isNotBlank() -> row.note
        else -> row.merchant?.takeIf { it.isNotBlank() } ?: categoryLabel(row.categoryName, row.categoryKey)
    }
    val category = if (row.type == TxType.TRANSFER) stringResource(R.string.type_transfer)
    else categoryLabel(row.categoryName, row.categoryKey) + if (showWallet) " · " + row.walletName else ""
    val badgeLabel = row.merchant?.takeIf { it.isNotBlank() } ?: row.note.takeIf { it.isNotBlank() }
        ?: categoryLabel(row.categoryName, row.categoryKey)

    Row(
        modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        CategoryBadge(
            label = if (row.type == TxType.TRANSFER) "⇄" else badgeLabel,
            colorIndex = if (row.type == TxType.TRANSFER) 10 else row.categoryColor,
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SourceChip(row.source, row.status == TxStatus.PENDING_REVIEW, row.walletName)
                Text(category, color = Brand.Muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(signedAmount(row.type, row.amount), color = amountColor(row.type), fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
            if (caption != null) Text(caption, color = Brand.Muted, fontSize = 12.sp)
        }
    }
}

/** A list of transactions in one bordered card, with thin dividers between rows. */
@Composable
fun TransactionGroup(rows: List<TransactionRow>, onClick: (TransactionRow) -> Unit, caption: @Composable (TransactionRow) -> String? = { null }) {
    SectionCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 2.dp)) {
        rows.forEachIndexed { i, row ->
            if (i > 0) HorizontalDivider(color = Brand.Border)
            TransactionItem(row, onClick = { onClick(row) }, caption = caption(row))
        }
    }
}

// ---------- Inputs ----------

/** Shows "1250000" as "1.250.000" while the field keeps plain digits. */
class ThousandsTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        val out = StringBuilder()
        val origToTrans = IntArray(raw.length + 1)
        for (i in raw.indices) {
            if (i > 0 && (raw.length - i) % 3 == 0) out.append('.')
            origToTrans[i] = out.length
            out.append(raw[i])
        }
        origToTrans[raw.length] = out.length
        val transToOrig = IntArray(out.length + 1)
        var o = 0
        for (t in 0..out.length) {
            while (o < raw.length && origToTrans[o] < t) o++
            transToOrig[t] = o
        }
        return TransformedText(AnnotatedString(out.toString()), object : OffsetMapping {
            override fun originalToTransformed(offset: Int) = origToTrans[offset.coerceIn(0, raw.length)]
            override fun transformedToOriginal(offset: Int) = transToOrig[offset.coerceIn(0, out.length)]
        })
    }
}

/** Rupiah amount input: digits only, grouped with dots, "Rp" prefix. Value is the plain digit string. */
@Composable
fun AmountField(digits: String, onChange: (String) -> Unit, modifier: Modifier = Modifier, label: String = stringResource(R.string.amount)) {
    OutlinedTextField(
        value = digits,
        onValueChange = { v -> onChange(v.filter(Char::isDigit).trimStart('0').take(13)) },
        label = { Text(label) },
        prefix = { Text("Rp ") },
        singleLine = true,
        textStyle = MaterialTheme.typography.headlineSmall,
        visualTransformation = ThousandsTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier.fillMaxWidth(),
    )
}

/** A read-only text field that opens a menu of [options]. */
@Composable
fun <T> DropdownField(
    label: String,
    options: List<T>,
    selected: T?,
    optionLabel: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = selected?.let { optionLabel(it) } ?: placeholder,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(optionLabel(option)) },
                    onClick = { onSelect(option); expanded = false },
                )
            }
        }
    }
}

/** Pill track with the chosen option raised on it (Expense / Income / Transfer, Week / Month / Year). */
@Composable
fun <T> SegmentedChoice(options: List<T>, selected: T, label: @Composable (T) -> String, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Brand.Surface2).padding(5.dp).selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { option ->
            val on = option == selected
            Box(
                Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(14.dp))
                    .background(if (on) (if (Brand.isDark) Brand.Bg else Brand.Surface) else Color.Transparent)
                    .selectable(selected = on, onClick = { onSelect(option) }, role = Role.Tab),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label(option), maxLines = 1, fontSize = 15.sp,
                    fontWeight = if (on) FontWeight.ExtraBold else FontWeight.SemiBold,
                    color = if (on) Brand.Text else Brand.Muted,
                )
            }
        }
    }
}

/** Date input that opens the Material date picker. */
@Composable
fun DateField(label: String, date: LocalDate, onChange: (LocalDate) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedTextField(
            value = formatDate(date),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            modifier = Modifier.fillMaxWidth(),
        )
        // Transparent layer on top: read-only text fields swallow clicks otherwise.
        Box(Modifier.matchParentSize().clickable { open = true })
    }
    if (open) DatePickerPopup(date, onChange) { open = false }
}

/** The Material date picker as a dialog. */
@Composable
fun DatePickerPopup(date: LocalDate, onPick: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    // The picker works in UTC midnight millis.
    val state = rememberDatePickerState(initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                onDismiss()
            }) { Text(stringResource(R.string.ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
        colors = DatePickerDefaults.colors(containerColor = Brand.Surface),
    ) { DatePicker(state = state, colors = DatePickerDefaults.colors(containerColor = Brand.Surface)) }
}

/** The Material time picker as a dialog (12 or 24 hour, following the phone). */
@Composable
fun TimePickerPopup(time: LocalTime, onPick: (LocalTime) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val state = rememberTimePickerState(time.hour, time.minute, DateFormat.is24HourFormat(context))
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onPick(LocalTime.of(state.hour, state.minute)); onDismiss() }) { Text(stringResource(R.string.ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
        containerColor = Brand.Surface,
        text = { TimePicker(state = state) },
    )
}

/** Time input with the Material time picker (12 or 24 hour, following the phone), drawn in the app's theme. */
@Composable
fun TimeField(label: String, time: LocalTime, onChange: (LocalTime) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedTextField(
            value = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(currentLocale()).format(time),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            modifier = Modifier.fillMaxWidth(),
        )
        Box(Modifier.matchParentSize().clickable { open = true })
    }
    if (open) TimePickerPopup(time, onChange) { open = false }
}

/** "‹ October 2026 ›" */
@Composable
fun PeriodSwitcher(label: String, onPrev: () -> Unit, onNext: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPrev) { Icon(Icons.Default.ChevronLeft, stringResource(R.string.previous_period)) }
        Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
        IconButton(onClick = onNext) { Icon(Icons.Default.ChevronRight, stringResource(R.string.next_period)) }
    }
}

@Composable
fun ConfirmDialog(title: String, text: String, confirmLabel: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = { onConfirm(); onDismiss() }) { Text(confirmLabel, color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** Thin progress bar; amber near the limit, red when over. */
@Composable
fun BudgetBar(fraction: Float, near: Boolean, over: Boolean, modifier: Modifier = Modifier) {
    val color = when {
        over -> MaterialTheme.colorScheme.error
        near -> Brand.AmberBar
        else -> Brand.Accent
    }
    Box(modifier.fillMaxWidth().height(10.dp).clip(CircleShape).background(Brand.Surface2)) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(10.dp).clip(CircleShape).background(color))
    }
}
