@file:OptIn(ExperimentalCoroutinesApi::class)

package com.example.moneyapp.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
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
import com.example.moneyapp.AppContainer
import com.example.moneyapp.R
import com.example.moneyapp.core.Money
import com.example.moneyapp.core.PeriodKind
import com.example.moneyapp.core.Periods
import com.example.moneyapp.data.db.CategoryTotal
import com.example.moneyapp.data.db.MonthTotal
import com.example.moneyapp.data.db.TxType
import com.example.moneyapp.ui.categoryLabel
import com.example.moneyapp.ui.components.EmptyState
import com.example.moneyapp.ui.components.PeriodSwitcher
import com.example.moneyapp.ui.components.ScreenScaffold
import com.example.moneyapp.ui.components.SectionCard
import com.example.moneyapp.ui.components.SectionTitle
import com.example.moneyapp.ui.components.SegmentedChoice
import com.example.moneyapp.ui.components.appContainer
import com.example.moneyapp.ui.components.currentLocale
import com.example.moneyapp.ui.components.formatDate
import com.example.moneyapp.ui.components.formatMonth
import com.example.moneyapp.ui.theme.Brand
import com.example.moneyapp.ui.theme.CategoryPalette
import com.example.moneyapp.ui.txTypeLabel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle

data class ReportQuery(val kind: PeriodKind = PeriodKind.MONTH, val offset: Int = 0, val breakdown: TxType = TxType.EXPENSE)

data class ReportState(
    val query: ReportQuery = ReportQuery(),
    val income: Long = 0,
    val expense: Long = 0,
    val breakdown: List<CategoryTotal> = emptyList(),
    val months: List<MonthTotal> = emptyList(),
)

class ReportsViewModel(c: AppContainer) : ViewModel() {
    private val query = MutableStateFlow(ReportQuery())

    // Last 6 months for the trend chart (independent of the selected period).
    private val sixMonths = Periods.month(YearMonth.now().minusMonths(5)).let { first ->
        com.example.moneyapp.core.DateRange(first.startMs, Periods.month(YearMonth.now()).endMs)
    }

    val state = query.flatMapLatest { q ->
        val range = Periods.of(q.kind, LocalDate.now(), q.offset)
        combine(
            c.transactions.observeCategoryTotals(TxType.INCOME, range),
            c.transactions.observeCategoryTotals(TxType.EXPENSE, range),
            c.transactions.observeMonthTotals(sixMonths),
        ) { inc, exp, months ->
            ReportState(
                query = q,
                income = inc.sumOf { it.total },
                expense = exp.sumOf { it.total },
                breakdown = if (q.breakdown == TxType.INCOME) inc else exp,
                months = months,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReportState())

    fun set(change: (ReportQuery) -> ReportQuery) = query.update(change)
}

@Composable
fun ReportsScreen() {
    val c = appContainer()
    val vm: ReportsViewModel = viewModel { ReportsViewModel(c) }
    val s by vm.state.collectAsStateWithLifecycle()
    val q = s.query

    ScreenScaffold(title = stringResource(R.string.tab_reports)) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SegmentedChoice(
                    PeriodKind.entries, q.kind,
                    { stringResource(when (it) { PeriodKind.WEEK -> R.string.week; PeriodKind.MONTH -> R.string.month; PeriodKind.YEAR -> R.string.year }) },
                    { k -> vm.set { it.copy(kind = k, offset = 0) } },
                )
            }
            item { PeriodSwitcher(periodLabel(q), { vm.set { it.copy(offset = it.offset - 1) } }, { vm.set { it.copy(offset = it.offset + 1) } }) }
            item {
                SectionCard {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Total(stringResource(R.string.income), "+" + Money.format(s.income), Brand.IncomeText, Modifier.weight(1f))
                        Total(stringResource(R.string.expense), Money.format(-s.expense), Brand.Ink, Modifier.weight(1f))
                    }
                    Row {
                        Text(stringResource(R.string.net), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                        Text(Money.formatSigned(s.income - s.expense), fontWeight = FontWeight.ExtraBold)
                    }
                }
            }
            item {
                SectionCard {
                    SectionTitle(stringResource(R.string.by_category))
                    SegmentedChoice(listOf(TxType.EXPENSE, TxType.INCOME), q.breakdown, { txTypeLabel(it) }, { t -> vm.set { it.copy(breakdown = t) } })
                    if (s.breakdown.isEmpty()) EmptyState(stringResource(R.string.no_data_period))
                    else CategoryBreakdown(s.breakdown)
                }
            }
            item {
                SectionCard {
                    SectionTitle(stringResource(R.string.last_six_months))
                    MonthBars(s.months)
                }
            }
        }
    }
}

@Composable
private fun periodLabel(q: ReportQuery): String {
    val today = LocalDate.now()
    return when (q.kind) {
        PeriodKind.MONTH -> formatMonth(today.plusMonths(q.offset.toLong()))
        PeriodKind.YEAR -> (today.year + q.offset).toString()
        PeriodKind.WEEK -> {
            val r = Periods.of(PeriodKind.WEEK, today, q.offset)
            formatDate(Periods.toDate(r.startMs)) + " – " + formatDate(Periods.toDate(r.endMs))
        }
    }
}

@Composable
private fun Total(label: String, value: String, color: Color, modifier: Modifier) {
    Column(modifier) {
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = color, maxLines = 1)
    }
}

/** Donut + legend. Top 6 categories, the rest grouped as "Other". */
@Composable
private fun CategoryBreakdown(totals: List<CategoryTotal>) {
    val sum = totals.sumOf { it.total }.coerceAtLeast(1)
    val top = totals.take(6)
    val restTotal = totals.drop(6).sumOf { it.total }
    val slices = top.mapIndexed { i, t -> t.total to CategoryPalette.chart[i % CategoryPalette.chart.size] } +
        (if (restTotal > 0) listOf(restTotal to CategoryPalette.chart.last()) else emptyList())
    val summary = stringResource(R.string.chart_summary, Money.format(sum))

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(Modifier.size(128.dp).semantics { contentDescription = summary }, contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = 18.dp.toPx()
                val inset = stroke / 2
                var start = -90f
                slices.forEach { (value, color) ->
                    val sweep = 360f * value / sum
                    drawArc(color, start, sweep - 1f, useCenter = false,
                        topLeft = Offset(inset, inset), size = Size(size.width - stroke, size.height - stroke), style = Stroke(stroke))
                    start += sweep
                }
            }
            Text(Money.formatShort(sum), fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            top.forEachIndexed { i, t ->
                LegendRow(categoryLabel(t.name, t.builtinKey), t.total, sum, CategoryPalette.chart[i % CategoryPalette.chart.size])
            }
            if (restTotal > 0) LegendRow(stringResource(R.string.other), restTotal, sum, CategoryPalette.chart.last())
        }
    }
}

@Composable
private fun LegendRow(name: String, total: Long, sum: Long, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(color))
        Column(Modifier.weight(1f)) {
            Text(name, fontSize = 13.sp, maxLines = 1)
            Text(Money.format(total), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text("${(total * 100 / sum)}%", fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}

/** Income (light) and expense (dark) bars per month. */
@Composable
private fun MonthBars(months: List<MonthTotal>) {
    val locale = currentLocale()
    val now = YearMonth.now()
    val data = (5 downTo 0).map { back ->
        val ym = now.minusMonths(back.toLong())
        val key = ym.toString() // "2026-10", same as SQLite's strftime('%Y-%m')
        val m = months.firstOrNull { it.month == key }
        Triple(ym.month.getDisplayName(TextStyle.SHORT, locale), m?.income ?: 0L, m?.expense ?: 0L)
    }
    val max = data.maxOf { maxOf(it.second, it.third) }.coerceAtLeast(1)

    Row(Modifier.fillMaxWidth().height(150.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
        data.forEach { (label, inc, exp) ->
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxHeight(), verticalArrangement = Arrangement.Bottom) {
                Text(Money.formatShort(exp), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.height(100.dp)) {
                    Bar(inc.toFloat() / max, Color(0xFF9CC7B6))
                    Bar(exp.toFloat() / max, Brand.Green)
                }
                Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        LegendDot(Color(0xFF9CC7B6), stringResource(R.string.income))
        LegendDot(Brand.Green, stringResource(R.string.expense))
    }
}

@Composable
private fun Bar(fraction: Float, color: Color) {
    Box(
        Modifier.width(12.dp).fillMaxHeight(fraction.coerceIn(0.02f, 1f))
            .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)).background(color)
    )
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(color))
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
