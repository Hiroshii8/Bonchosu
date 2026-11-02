package com.example.moneyapp.statement

import com.example.moneyapp.core.Money
import com.example.moneyapp.data.db.TxType
import java.time.LocalDate

// Pure Kotlin, no Android imports: unit-test it on the JVM with real statement samples.

data class StatementRow(
    val date: LocalDate,
    val amount: Long,
    val type: TxType,
    val description: String,
    /** False when the direction is only a guess: the preview highlights it so you can flip it. */
    val typeCertain: Boolean,
    val rawText: String,
)

data class ParsedStatement(
    val rows: List<StatementRow>,
    val openingBalance: Long? = null,
    val closingBalance: Long? = null,
) {
    /** Opening + income − expenses = closing. Null when the statement doesn't print both balances. */
    val balancesMatch: Boolean?
        get() {
            val open = openingBalance ?: return null
            val close = closingBalance ?: return null
            val net = rows.sumOf { if (it.type == TxType.INCOME) it.amount else -it.amount }
            return open + net == close
        }
}

/**
 * Reads transactions from statement text without knowing the bank, using what every layout shares:
 * a row starts with a date and carries an amount (plus usually a running balance).
 *
 * Direction, strongest evidence first: running balance arithmetic, DB/CR marker, +/− sign,
 * debit/credit columns, then wording. Without any of those it's an expense marked as uncertain.
 */
object StatementParser {

    fun parse(lines: List<String>, today: LocalDate = LocalDate.now()): ParsedStatement {
        val cleaned = lines.map { it.replace(Regex("""\s+"""), " ").trim() }.filter { it.isNotEmpty() }
        val period = cleaned.firstNotNullOfOrNull(::findPeriod)
        val creditFirst = cleaned.any(::isCreditFirstHeader)

        val drafts = mutableListOf<Draft>()
        var open: Draft? = null
        var opening: Long? = null
        var closing: Long? = null
        // App history screenshots print the date as its own heading above the rows.
        var headingDate: LocalDate? = null
        var pendingDescription = ""

        for (line in cleaned) {
            val summary = summaryKind(line)
            if (summary != null) {
                val value = findAmounts(line).lastOrNull()?.value
                if (summary == Summary.OPENING && opening == null) opening = value
                if (summary == Summary.CLOSING) closing = value
                open = null
                continue
            }
            if (isNoise(line)) {
                open = null
                pendingDescription = ""
                continue
            }

            val dated = leadingDate(line, period, today)
            if (dated != null) {
                val rest = line.substring(dated.end).trim()
                if (findAmounts(rest).isEmpty() && (rest.isEmpty() || TIME_ONLY.matches(rest))) {
                    headingDate = dated.date
                    open = null
                    pendingDescription = ""
                    continue
                }
                open = Draft(dated.date, rest).also(drafts::add)
                headingDate = null
                pendingDescription = ""
                continue
            }

            val current = open
            val heading = headingDate
            when {
                current != null && current.text.isBlank() -> current.text = line
                current != null && findAmounts(current.text).isEmpty() -> current.text += " $line"
                current != null -> if (findAmounts(line).isEmpty()) current.extra += " $line" else open = null
                heading != null && findAmounts(line).isNotEmpty() -> {
                    drafts += Draft(heading, "$pendingDescription $line".trim())
                    pendingDescription = ""
                }
                heading != null -> pendingDescription = line
            }
        }

        val rows = mutableListOf<StatementRow>()
        var balance = opening
        for (draft in drafts) {
            val row = toRow(draft, balance, creditFirst) ?: continue
            rows += row.first
            balance = row.second ?: balance?.let {
                if (row.first.type == TxType.INCOME) it + row.first.amount else it - row.first.amount
            }
        }
        return ParsedStatement(rows, opening, closing)
    }

    /** Joins OCR boxes that sit on the same visual line, left to right, top to bottom. */
    fun groupIntoLines(boxes: List<TextBox>): List<String> {
        val lines = mutableListOf<MutableList<TextBox>>()
        for (box in boxes.sortedBy { it.centerY }) {
            val line = lines.lastOrNull()
            val tolerance = maxOf(box.height, line?.first()?.height ?: 0) / 2
            if (line != null && box.centerY - line.first().centerY <= tolerance) line += box else lines += mutableListOf(box)
        }
        return lines.map { line -> line.sortedBy { it.left }.joinToString(" ") { it.text } }
    }

    data class TextBox(val text: String, val left: Int, val centerY: Int, val height: Int)

    // ---------- Row building ----------

    private class Draft(val date: LocalDate, var text: String, var extra: String = "")

    /** Returns the row and the balance printed on it. */
    private fun toRow(draft: Draft, previousBalance: Long?, creditFirst: Boolean): Pair<StatementRow, Long?>? {
        val amounts = findAmounts(draft.text)
        val first = amounts.firstOrNull() ?: return null

        var amount = first.value
        var balance: Long? = null
        var columnType: TxType? = null
        when {
            first.signed || first.hasCurrency -> Unit // "-Rp50.000": a single amount, not a table row
            amounts.size >= 3 -> {
                val (a, b, c) = amounts.takeLast(3).map { it.value }
                balance = c
                when {
                    a > 0 && b == 0L -> { amount = a; columnType = if (creditFirst) TxType.INCOME else TxType.EXPENSE }
                    a == 0L && b > 0 -> { amount = b; columnType = if (creditFirst) TxType.EXPENSE else TxType.INCOME }
                    else -> amount = amounts[amounts.size - 3].value
                }
            }
            amounts.size == 2 -> {
                val (a, b) = amounts.map { it.value }
                when {
                    b == 0L -> { amount = a; columnType = if (creditFirst) TxType.INCOME else TxType.EXPENSE }
                    a == 0L -> { amount = b; columnType = if (creditFirst) TxType.EXPENSE else TxType.INCOME }
                    else -> balance = b
                }
            }
        }
        if (amount <= 0) return null

        val lower = "${draft.text} ${draft.extra}".lowercase()
        val fromBalance = if (previousBalance != null && balance != null) {
            when (balance) {
                previousBalance - amount -> TxType.EXPENSE
                previousBalance + amount -> TxType.INCOME
                else -> null
            }
        } else null
        val type = fromBalance
            ?: markerType(draft.text, amounts)
            ?: first.sign
            ?: columnType
            ?: wordingType(lower)

        val description = cleanDescription(draft.text.substring(0, first.start) + " " + draft.extra)
        return StatementRow(
            date = draft.date,
            amount = amount,
            type = type ?: TxType.EXPENSE,
            description = description,
            typeCertain = type != null,
            rawText = "${draft.text} ${draft.extra}".trim(),
        ) to balance
    }

    private fun markerType(text: String, amounts: List<Amount>): TxType? {
        for (a in amounts) {
            val marker = AFTER_AMOUNT_MARKER.find(text.substring(a.end))?.groupValues?.get(1)?.lowercase() ?: continue
            return if (marker in DEBIT_MARKERS) TxType.EXPENSE else TxType.INCOME
        }
        val beforeAmount = text.substring(0, amounts.first().start).lowercase()
        return when {
            DEBIT_WORD_MARKER.containsMatchIn(beforeAmount) -> TxType.EXPENSE
            CREDIT_WORD_MARKER.containsMatchIn(beforeAmount) -> TxType.INCOME
            else -> null
        }
    }

    private fun wordingType(lower: String): TxType? = when {
        INCOME_WORDS.containsMatchIn(lower) -> TxType.INCOME
        EXPENSE_WORDS.containsMatchIn(lower) -> TxType.EXPENSE
        else -> null
    }

    /** Drops bank jargon and reference numbers so the description reads like a name. */
    private fun cleanDescription(raw: String): String =
        raw.split(' ')
            .filter { it.isNotBlank() && it.lowercase() !in JARGON && !looksLikeReference(it) }
            .joinToString(" ")
            .trim(' ', '-', ':', '|')
            .take(MAX_DESCRIPTION)

    private fun looksLikeReference(word: String) =
        word.count(Char::isDigit) >= 4 || ('/' in word && word.any(Char::isDigit)) || TIME_ONLY.matches(word)

    // ---------- Amounts ----------

    private class Amount(val value: Long, val start: Int, val end: Int, val sign: TxType?, val hasCurrency: Boolean) {
        val signed get() = sign != null
    }

    private fun findAmounts(text: String): List<Amount> = AMOUNT.findAll(text).mapNotNull { m ->
        val digits = m.groups["number"]?.value ?: return@mapNotNull null
        val value = Money.parseRupiah(digits) ?: return@mapNotNull null
        val sign = when (m.groups["sign"]?.value) {
            "+" -> TxType.INCOME
            "-", "−", "–" -> TxType.EXPENSE
            else -> null
        }
        Amount(value, m.range.first, m.range.last + 1, sign, m.groups["currency"] != null)
    }.toList()

    // ---------- Dates ----------

    private class Dated(val date: LocalDate, val end: Int)

    private fun leadingDate(line: String, period: YearMonthHint?, today: LocalDate): Dated? {
        NUMERIC_YMD.find(line)?.let { m ->
            val (y, mo, d) = m.destructured
            return dateOf(y.toInt(), mo.toInt(), d.toInt())?.let { Dated(it, m.range.last + 1) }
        }
        NUMERIC_DATE.find(line)?.let { m ->
            val (d, mo, y) = m.destructured
            val month = mo.toInt()
            val year = if (y.isEmpty()) inferYear(month, period, today) else fullYear(y)
            return dateOf(year, month, d.toInt())?.let { Dated(it, m.range.last + 1) }
        }
        NAMED_DATE.find(line)?.let { m ->
            val (d, name, y) = m.destructured
            val month = monthNumber(name) ?: return null
            val year = if (y.isEmpty()) inferYear(month, period, today) else fullYear(y)
            return dateOf(year, month, d.toInt())?.let { Dated(it, m.range.last + 1) }
        }
        return null
    }

    private fun fullYear(y: String) = if (y.length == 2) 2000 + y.toInt() else y.toInt()

    /** Rows like "01/11" have no year: take it from the statement period, or the most recent past date. */
    private fun inferYear(month: Int, period: YearMonthHint?, today: LocalDate): Int {
        if (period != null) return if (month > period.month) period.year - 1 else period.year
        return if (month > today.monthValue) today.year - 1 else today.year
    }

    private fun dateOf(y: Int, m: Int, d: Int): LocalDate? = runCatching { LocalDate.of(y, m, d) }.getOrNull()

    private fun monthNumber(name: String): Int? {
        val key = name.lowercase().take(3)
        return MONTHS[key]
    }

    private data class YearMonthHint(val year: Int, val month: Int)

    private fun findPeriod(line: String): YearMonthHint? {
        if (!PERIOD_WORD.containsMatchIn(line.lowercase())) return null
        PERIOD_NAMED.find(line)?.let { m ->
            val month = monthNumber(m.groupValues[1]) ?: return@let
            return YearMonthHint(m.groupValues[2].toInt(), month)
        }
        return PERIOD_NUMERIC.findAll(line).lastOrNull()?.let { m ->
            val month = m.groupValues[1].toInt()
            if (month in 1..12) YearMonthHint(m.groupValues[2].toInt(), month) else null
        }
    }

    // ---------- Line classification ----------

    private enum class Summary { OPENING, CLOSING, OTHER }

    private fun summaryKind(line: String): Summary? {
        val lower = line.lowercase()
        return when {
            OPENING_WORDS.containsMatchIn(lower) -> Summary.OPENING
            CLOSING_WORDS.containsMatchIn(lower) -> Summary.CLOSING
            OTHER_SUMMARY_WORDS.containsMatchIn(lower) -> Summary.OTHER
            else -> null
        }
    }

    private fun isNoise(line: String): Boolean = NOISE.containsMatchIn(line.lowercase())

    private fun isCreditFirstHeader(line: String): Boolean {
        val lower = line.lowercase()
        val debit = HEADER_DEBIT.find(lower)?.range?.first ?: return false
        val credit = HEADER_CREDIT.find(lower)?.range?.first ?: return false
        return credit < debit
    }

    private const val MAX_DESCRIPTION = 60

    private val MONTHS = mapOf(
        "jan" to 1, "feb" to 2, "peb" to 2, "mar" to 3, "apr" to 4, "mei" to 5, "may" to 5, "jun" to 6, "jul" to 7,
        "agu" to 8, "agt" to 8, "ags" to 8, "aug" to 8, "sep" to 9, "okt" to 10, "oct" to 10, "nov" to 11, "nop" to 11,
        "des" to 12, "dec" to 12,
    )
    private const val MONTH_NAME =
        "jan[a-z]*|feb[a-z]*|peb[a-z]*|mar[a-z]*|apr[a-z]*|mei|may|jun[a-z]*|jul[a-z]*|agu[a-z]*|agt|ags|aug[a-z]*|sep[a-z]*|okt[a-z]*|oct[a-z]*|nov[a-z]*|nop[a-z]*|des[a-z]*|dec[a-z]*"

    private val NUMERIC_YMD = Regex("""^(\d{4})[/-](\d{1,2})[/-](\d{1,2})\b""")
    private val NUMERIC_DATE = Regex("""^(\d{1,2})[/-](\d{1,2})(?:[/-](\d{4}|\d{2}))?(?![\d/,.-])""")
    private val NAMED_DATE = Regex("""(?i)^(\d{1,2})[ -]($MONTH_NAME)\b(?:[ -](\d{4}|\d{2})\b)?""")
    private val TIME_ONLY = Regex("""\d{1,2}[:.]\d{2}(?:[:.]\d{2})?(?:\s?(?:wib|wita|wit|am|pm))?""", RegexOption.IGNORE_CASE)

    private val PERIOD_WORD = Regex("""\b(?:periode|period|bulan|statement date|tanggal laporan)\b""")
    private val PERIOD_NAMED = Regex("""(?i)\b($MONTH_NAME)\s+(\d{4})\b""")
    // Matches "11/2026" and the tail of "30/11/2026": both end in month + year.
    private val PERIOD_NUMERIC = Regex("""\b(\d{1,2})[/-](\d{4})\b""")

    /**
     * "50,000.00", "50.000,00", "1.250.000", "0.00", "Rp50.000", "-Rp 50.000", "+ Rp.50,000.00".
     * Bare integers need a currency in front: otherwise they're almost always reference numbers.
     */
    private val AMOUNT = Regex(
        """(?i)(?<![\w/.,-])(?<sign>[-+−–]\s?)?(?<currency>(?:rp\.?|idr)\s?)?(?<number>\d{1,3}(?:[.,]\d{3})+(?:[.,]\d{1,2})?|\d+[.,]\d{2}|(?<=rp|rp\.|rp |rp\. |idr|idr )\d+)(?![\w/]|[.,]\d)"""
    )

    private val AFTER_AMOUNT_MARKER = Regex("""(?i)^\s*(db|dr|d|cr|kr|k|c)\b""")
    private val DEBIT_MARKERS = setOf("db", "dr", "d")
    private val DEBIT_WORD_MARKER = Regex("""\b(?:db|debet|debit)\b""")
    private val CREDIT_WORD_MARKER = Regex("""\b(?:cr|kr|kredit|credit)\b""")

    private val INCOME_WORDS = Regex(
        """\b(?:masuk|setoran|setor|gaji|bunga|interest|pengembalian|refund|cashback|diterima|received|deposit|salary|payroll)\b"""
    )
    private val EXPENSE_WORDS = Regex(
        """\b(?:keluar|tarik tunai|pembayaran|pembelian|bayar|biaya|pajak|qris|withdrawal|payment|purchase|fee|tax|transfer ke|transfer to|paid)\b"""
    )

    private val JARGON = setOf(
        "trsf", "trf", "e-banking", "ebanking", "db", "cr", "kr", "dr", "otomatis", "switching", "bi-fast", "bifast",
    )

    private val OPENING_WORDS = Regex("""\b(?:saldo awal|opening balance|beginning balance|saldo sebelumnya|previous balance)\b""")
    private val CLOSING_WORDS = Regex("""\b(?:saldo akhir|closing balance|ending balance)\b""")
    private val OTHER_SUMMARY_WORDS = Regex(
        """\b(?:mutasi debet|mutasi kredit|mutasi cr|mutasi db|total debet|total debit|total kredit|total credit|total mutasi|total transaksi|jumlah transaksi)\b"""
    )
    private val NOISE = Regex(
        """\b(?:halaman|page \d|bersambung|continued|keterangan|description|tanggal transaksi|transaction date|nomor rekening|no\. rekening|account number|www\.|http|terdaftar dan diawasi|otoritas jasa keuangan)\b"""
    )
    private val HEADER_DEBIT = Regex("""\b(?:debet|debit|db)\b""")
    private val HEADER_CREDIT = Regex("""\b(?:kredit|credit|cr)\b""")
}
