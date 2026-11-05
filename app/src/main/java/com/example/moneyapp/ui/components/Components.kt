@file:OptIn(ExperimentalMaterial3Api::class)

package com.hiroshi.moneyapp.ui.components

import android.app.TimePickerDialog
import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import com.hiroshi.moneyapp.AppContainer
import com.hiroshi.moneyapp.MoneyApplication
import com.hiroshi.moneyapp.R
import com.hiroshi.moneyapp.core.Money
import com.hiroshi.moneyapp.data.db.TransactionRow
import com.hiroshi.moneyapp.data.db.TxSource
import com.hiroshi.moneyapp.data.db.TxType
import com.hiroshi.moneyapp.ui.categoryLabel
import com.hiroshi.moneyapp.ui.theme.Brand
import com.hiroshi.moneyapp.ui.theme.CategoryPalette
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

// ---------- Plumbing ----------

@Composable
fun appContainer(): AppContainer = (LocalContext.current.applicationContext as MoneyApplication).container

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

/** Standard screen: top bar with optional back button, actions and FAB. */
@Composable
fun ScreenScaffold(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                        }
                    }
                },
                actions = actions,
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        floatingActionButton = floatingActionButton,
        containerColor = MaterialTheme.colorScheme.background,
        content = content,
    )
}

@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    color: Color = Brand.Card,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), color = color) {
        Column(Modifier.padding(contentPadding), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, trailing: @Composable () -> Unit = {}) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        trailing()
    }
}

@Composable
fun EmptyState(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
    }
}

// ---------- Money & categories ----------

/** Rounded square with the category's initial, coloured from the palette. */
@Composable
fun CategoryBadge(label: String, colorIndex: Int?, size: Dp = 40.dp) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(12.dp)).background(CategoryPalette.tint(colorIndex)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label.take(1).uppercase(),
            color = CategoryPalette.ink(colorIndex),
            fontWeight = FontWeight.ExtraBold,
            fontSize = (size.value * 0.36f).sp,
        )
    }
}

@Composable
fun amountColor(type: TxType): Color = when (type) {
    TxType.INCOME -> Brand.IncomeText
    TxType.EXPENSE -> Brand.Ink
    TxType.TRANSFER -> Brand.InkMuted
}

fun signedAmount(type: TxType, amount: Long): String = when (type) {
    TxType.INCOME -> "+" + Money.format(amount)
    TxType.EXPENSE -> Money.format(-amount)
    TxType.TRANSFER -> Money.format(amount)
}

@Composable
fun TransactionItem(row: TransactionRow, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val title = when {
        row.type == TxType.TRANSFER -> stringResource(R.string.transfer_title, row.walletName, row.toWalletName ?: "?")
        row.note.isNotBlank() -> row.note
        else -> row.merchant?.takeIf { it.isNotBlank() } ?: categoryLabel(row.categoryName, row.categoryKey)
    }
    val meta = if (row.type == TxType.TRANSFER) stringResource(R.string.type_transfer)
    else categoryLabel(row.categoryName, row.categoryKey) + " · " + row.walletName

    Row(
        modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CategoryBadge(
            label = if (row.type == TxType.TRANSFER) "⇄" else categoryLabel(row.categoryName, row.categoryKey),
            colorIndex = if (row.type == TxType.TRANSFER) 10 else row.categoryColor,
        )
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (row.source == TxSource.NOTIFICATION) {
                    val cd = stringResource(R.string.auto_captured)
                    Icon(Icons.Default.Bolt, contentDescription = null, tint = Brand.Green,
                        modifier = Modifier.size(13.dp).semantics { contentDescription = cd })
                }
                Text(meta, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Text(
            signedAmount(row.type, row.amount),
            color = amountColor(row.type),
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
        )
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

@Composable
fun <T> SegmentedChoice(options: List<T>, selected: T, label: @Composable (T) -> String, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    SingleChoiceSegmentedButtonRow(modifier.fillMaxWidth()) {
        options.forEachIndexed { i, option ->
            SegmentedButton(
                selected = option == selected,
                onClick = { onSelect(option) },
                shape = SegmentedButtonDefaults.itemShape(i, options.size),
            ) { Text(label(option), maxLines = 1) }
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
    if (open) {
        // The picker works in UTC midnight millis.
        val state = rememberDatePickerState(initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { onChange(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                    open = false
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.cancel)) } },
        ) { DatePicker(state = state) }
    }
}

/** Time input that opens the system time picker (12 or 24 hour, following the phone's setting). */
@Composable
fun TimeField(label: String, time: LocalTime, onChange: (LocalTime) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Box(modifier) {
        OutlinedTextField(
            value = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(currentLocale()).format(time),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            modifier = Modifier.fillMaxWidth(),
        )
        Box(Modifier.matchParentSize().clickable {
            TimePickerDialog(
                context,
                { _, hour, minute -> onChange(LocalTime.of(hour, minute)) },
                time.hour, time.minute, DateFormat.is24HourFormat(context),
            ).show()
        })
    }
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
        else -> Brand.Green
    }
    Box(modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(Brand.Line)) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(8.dp).clip(RoundedCornerShape(4.dp)).background(color))
    }
}
