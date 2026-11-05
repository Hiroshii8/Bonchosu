package com.hiroshi.moneyapp.bill

import com.hiroshi.moneyapp.core.Money
import java.time.LocalDate

// Pure Kotlin, no Android imports: unit-test it on the JVM with real receipt samples.

data class ReceiptItem(val name: String, val quantity: Int, val lineTotal: Long)
data class ReceiptCharge(val name: String, val amount: Long, val equally: Boolean)

data class ParsedReceipt(
    val merchant: String?,
    val date: LocalDate?,
    val items: List<ReceiptItem>,
    val charges: List<ReceiptCharge>,
    /** The total printed on the receipt, to compare against what was read. */
    val printedTotal: Long?,
    /** The printed tax is already inside the prices, so it isn't one of the [charges]. */
    val taxIncluded: Boolean,
)

/**
 * Turns OCR lines into items and fees, using keywords and the receipt's own arithmetic. Expect mistakes on
 * crumpled paper: the app always shows the result for checking and compares it with the printed total.
 */
object ReceiptParser {

    fun parse(rawLines: List<String>): ParsedReceipt {
        val lines = rawLines.map(::clean).filter { it.isNotEmpty() }
        val items = mutableListOf<DraftItem>()
        val charges = mutableListOf<DraftCharge>()
        var merchant: String? = null
        var date: LocalDate? = null
        var subtotal: Long? = null
        var total: Long? = null
        var taxIncludedNote = false
        var pendingName: String? = null

        for (line in lines) {
            val lower = line.lowercase()
            if (date == null) date = findDate(line)
            if (TAX_INCLUDED_NOTE.containsMatchIn(lower)) { taxIncludedNote = true; continue }

            val amounts = findAmounts(line)
            val kind = classify(lower)

            if (total != null) continue // after the total come payment, change and thank-yous
            when (kind) {
                Kind.NOISE -> { pendingName = null; continue }
                Kind.SUBTOTAL -> { subtotal = amounts.lastOrNull()?.value ?: subtotal; pendingName = null; continue }
                Kind.TOTAL -> { amounts.lastOrNull()?.let { total = it.value }; pendingName = null; continue }
                Kind.ITEM -> Unit
                else -> {
                    amounts.lastOrNull()?.let { a ->
                        charges += DraftCharge(chargeName(line, a), if (kind == Kind.DISCOUNT) -a.value else a.value, kind)
                    }
                    pendingName = null
                    continue
                }
            }

            if (amounts.isEmpty()) {
                if (items.isEmpty() && merchant == null && charges.isEmpty() && LETTERS.containsMatchIn(line)) merchant = line
                else if (LETTERS.containsMatchIn(line)) pendingName = line
                continue
            }
            parseItemLine(line, amounts, pendingName)?.let { items += it }
            pendingName = null
        }

        val resolved = resolveQuantities(items, subtotal, charges, total)
        val itemsSum = resolved.sumOf { it.lineTotal }
        val allCharges = charges.sumOf { it.amount }
        val taxes = charges.filter { it.kind == Kind.TAX }.sumOf { it.amount }
        val taxInsidePrices = taxes > 0 && (taxIncludedNote || (total != null && itemsSum + allCharges != total && itemsSum + allCharges - taxes == total))
        val kept = if (taxInsidePrices) charges.filter { it.kind != Kind.TAX } else charges

        return ParsedReceipt(
            merchant = merchant,
            date = date,
            items = resolved,
            charges = kept.map { ReceiptCharge(it.name, it.amount, it.kind == Kind.DELIVERY) },
            printedTotal = total,
            taxIncluded = taxInsidePrices || taxIncludedNote,
        )
    }

    // ---------- Items ----------

    private class DraftItem(val name: String, val quantity: Int, val amount: Long, val ambiguous: Boolean)
    private class DraftCharge(val name: String, val amount: Long, val kind: Kind)

    private fun parseItemLine(line: String, amounts: List<Amount>, pendingName: String?): DraftItem? {
        val last = amounts.last()
        var name = line.substring(0, amounts.first().start).trim()
        var quantity = 1
        var ambiguous = false

        QTY_MARK.find(name)?.let { m ->
            quantity = (m.groupValues[1].ifEmpty { m.groupValues[2] }).toInt()
            name = name.removeRange(m.range).trim()
        } ?: LEADING_QTY.find(name)?.let { m ->
            quantity = m.groupValues[1].toInt()
            name = name.substring(m.range.last + 1).trim()
        } ?: TRAILING_QTY.find(name)?.let { m ->
            quantity = m.groupValues[1].toInt()
            name = name.substring(0, m.range.first).trim()
        }

        // "2 x 25.000  50.000", or "25.000  50.000" where 50.000 is two of them.
        if (amounts.size >= 2) {
            val unit = amounts[amounts.size - 2].value
            if (unit > 0 && last.value % unit == 0L && last.value >= unit) quantity = (last.value / unit).toInt()
        } else if (quantity > 1) {
            ambiguous = true // "2 Nasi Goreng 25.000": unit price or line total? The subtotal decides.
        }

        val finalName = cleanName(name).ifEmpty { pendingName?.let(::cleanName).orEmpty() }
        if (finalName.isEmpty()) return null
        return DraftItem(finalName, quantity.coerceAtLeast(1), last.value, ambiguous)
    }

    /** Amounts default to line totals; if only reading them as unit prices fits the receipt, use that. */
    private fun resolveQuantities(items: List<DraftItem>, subtotal: Long?, charges: List<DraftCharge>, total: Long?): List<ReceiptItem> {
        val asLineTotal = items.sumOf { it.amount }
        val asUnitPrice = items.sumOf { if (it.ambiguous) it.amount * it.quantity else it.amount }
        val fees = charges.sumOf { it.amount }
        val taxes = charges.filter { it.kind == Kind.TAX }.sumOf { it.amount }
        val targets = listOfNotNull(subtotal, total?.minus(fees), total?.minus(fees - taxes))
        val useUnit = asUnitPrice != asLineTotal && asLineTotal !in targets && asUnitPrice in targets
        return items.map { ReceiptItem(it.name, it.quantity, if (useUnit && it.ambiguous) it.amount * it.quantity else it.amount) }
    }

    private fun cleanName(name: String) = name.trim(' ', '-', '.', ':', '*', '#', '@', 'x').trim()

    private fun chargeName(line: String, amount: Amount): String =
        line.substring(0, amount.start).replace(PERCENT, "").trim(' ', '-', ':', '(', ')', '.').ifEmpty { line.trim() }

    // ---------- Line classification ----------

    private enum class Kind { ITEM, NOISE, SUBTOTAL, TOTAL, TAX, SERVICE, DISCOUNT, ROUNDING, DELIVERY }

    private fun classify(lower: String): Kind = when {
        SUBTOTAL.containsMatchIn(lower) -> Kind.SUBTOTAL
        PAYMENT.containsMatchIn(lower) -> Kind.NOISE
        TOTAL.containsMatchIn(lower) -> Kind.TOTAL
        TAX.containsMatchIn(lower) -> Kind.TAX
        SERVICE.containsMatchIn(lower) -> Kind.SERVICE
        DISCOUNT.containsMatchIn(lower) -> Kind.DISCOUNT
        ROUNDING.containsMatchIn(lower) -> Kind.ROUNDING
        DELIVERY.containsMatchIn(lower) -> Kind.DELIVERY
        NOISE.containsMatchIn(lower) || findDate(lower) != null || TIME.containsMatchIn(lower) -> Kind.NOISE
        else -> Kind.ITEM
    }

    // ---------- Amounts and dates ----------

    private class Amount(val value: Long, val start: Int)

    private fun findAmounts(line: String): List<Amount> = AMOUNT.findAll(line).mapNotNull { m ->
        val value = Money.parseRupiah(m.groupValues[1]) ?: return@mapNotNull null
        if (value < MIN_AMOUNT) null else Amount(value, m.range.first)
    }.toList()

    private fun findDate(line: String): LocalDate? {
        val numeric = DATE.find(line)
        val named = if (numeric == null) NAMED_DATE.find(line) else null
        val (day, month, yearText) = when {
            numeric != null -> Triple(numeric.groupValues[1].toInt(), numeric.groupValues[2].toInt(), numeric.groupValues[3])
            named != null -> Triple(named.groupValues[1].toInt(), MONTHS[named.groupValues[2].lowercase().take(3)] ?: return null, named.groupValues[3])
            else -> return null
        }
        val year = yearText.let { if (it.length == 2) 2000 + it.toInt() else it.toInt() }
        return runCatching { LocalDate.of(year, month, day) }.getOrNull()
    }

    /** OCR reads zeros as letters next to digits ("25.0O0") and drops spaces into numbers ("25. 000"). */
    private fun clean(line: String) = line.replace(Regex("""\s+"""), " ").trim()
        .replace(Regex("""(?<=\d)[Oo]|[Oo](?=\d)"""), "0")
        .replace(Regex("""(?<=\d[.,]) (?=\d{3}(?!\d))"""), "")

    private const val MIN_AMOUNT = 100L

    private val AMOUNT = Regex("""(?i)(?<![\w.,/:-])[-(]?(?:rp\.?\s?)?(\d{1,3}(?:[.,]\d{3})+(?:[.,]\d{1,2})?|\d{3,})(?![\d%/:]|[.,]\d)""")
    private val DATE = Regex("""\b(\d{1,2}) ?[/-] ?(\d{1,2}) ?[/-] ?(\d{4}|\d{2})\b""")
    private val NAMED_DATE = Regex(
        """(?i)\b(\d{1,2})[ -](jan[a-z]*|feb[a-z]*|peb[a-z]*|mar[a-z]*|apr[a-z]*|mei|may|jun[a-z]*|jul[a-z]*|agu[a-z]*|agt|ags|aug[a-z]*|sep[a-z]*|okt[a-z]*|oct[a-z]*|nov[a-z]*|nop[a-z]*|des[a-z]*|dec[a-z]*)[ -](\d{4}|\d{2})\b"""
    )
    private val MONTHS = mapOf(
        "jan" to 1, "feb" to 2, "peb" to 2, "mar" to 3, "apr" to 4, "mei" to 5, "may" to 5, "jun" to 6, "jul" to 7,
        "agu" to 8, "agt" to 8, "ags" to 8, "aug" to 8, "sep" to 9, "okt" to 10, "oct" to 10, "nov" to 11, "nop" to 11,
        "des" to 12, "dec" to 12,
    )
    private val TIME = Regex("""\b\d{1,2}:\d{2}\b""")
    private val LETTERS = Regex("""\p{L}{2,}""")
    private val PERCENT = Regex("""\d+(?:[.,]\d+)?\s?%""")

    private val QTY_MARK = Regex("""(?i)(?:^|\s)(?:(\d{1,2})\s?[x@]|x\s?(\d{1,2}))(?=\s|$)""")
    private val LEADING_QTY = Regex("""^(\d{1,2})\s+(?=\p{L})""")
    private val TRAILING_QTY = Regex("""\s(\d{1,2})$""")

    private val TAX_INCLUDED_NOTE = Regex("""\b(?:sudah )?termasuk (?:pajak|ppn|pb1)\b|\b(?:incl?\.?|include[sd]?|including) (?:of )?(?:tax|vat|ppn|pb1|pajak)\b""")
    private val SUBTOTAL = Regex("""\bsub[ -]?total\b|\bsubtot\b""")
    private val PAYMENT = Regex("""\b(?:tunai|kembali(?:an)?|cash|change|card|kartu|debit|credit|kredit|qris|gopay|ovo|dana|shopeepay|edc|bayar tunai|tendered|payment)\b""")
    private val TOTAL = Regex("""\b(?:grand total|total|jumlah|amount due|total bayar|tagihan)\b""")
    private val TAX = Regex("""\b(?:pb ?1|ppn|pbjt|pajak|tax|vat)\b""")
    private val SERVICE = Regex("""\b(?:service|servis|svc|layanan)\b""")
    private val DISCOUNT = Regex("""\b(?:diskon|disc|discount|potongan|promo|voucher)\b""")
    private val ROUNDING = Regex("""\b(?:pembulatan|rounding)\b""")
    private val DELIVERY = Regex("""\b(?:ongkir|ongkos kirim|delivery|pengiriman|packing|packaging|kemasan|take ?away fee)\b""")
    private val NOISE = Regex(
        """\b(?:telp?|tlp|phone|npwp|kasir|cashier|server|meja|table|tamu|guest|pax|order|bill|struk|receipt|nota|no\.|invoice|terima kasih|thank|wifi|password|www|instagram|alamat|jl\.?|jalan|item\(s\)|qty)\b"""
    )
}
