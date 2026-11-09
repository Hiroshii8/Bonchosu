package com.hiroshi.bonchosu.bill

import com.hiroshi.bonchosu.data.db.TxType
import com.hiroshi.bonchosu.statement.StatementRow
import java.time.LocalDate

// Pure Kotlin: decides whether a photo is a receipt and turns it into one expense.

data class ScannedReceipt(
    val row: StatementRow,
    /** False when the receipt shows no date and the photo's date (or today) was used. */
    val dateFound: Boolean,
)

object ReceiptScan {

    /**
     * One expense from a photo's OCR lines, or null when the photo doesn't look like a receipt.
     * A receipt needs a printed total, or at least two priced items to add up. The amount is the printed total
     * when there is one, else the items plus fees.
     */
    fun read(lines: List<String>, photoDate: LocalDate): ScannedReceipt? {
        val parsed = ReceiptParser.parse(lines)
        val itemsTotal = parsed.items.sumOf { it.lineTotal } + parsed.charges.sumOf { it.amount }
        val amount = parsed.printedTotal ?: itemsTotal.takeIf { parsed.items.size >= 2 } ?: return null
        if (amount <= 0) return null

        val merchant = parsed.merchant?.trim().orEmpty()
        // Item names help the category guess ("kopi susu" → Food) even when the shop name says nothing.
        val text = (listOf(merchant) + parsed.items.map { it.name }).filter { it.isNotBlank() }.joinToString(" · ")
        return ScannedReceipt(
            row = StatementRow(
                date = parsed.date ?: photoDate,
                amount = amount,
                type = TxType.EXPENSE,
                description = merchant,
                typeCertain = true,
                rawText = text,
            ),
            dateFound = parsed.date != null,
        )
    }

    /** Indexes of receipts that repeat an earlier one in the same batch (the same receipt photographed twice). */
    fun repeats(rows: List<StatementRow>): Set<Int> {
        val seen = mutableSetOf<Triple<LocalDate, Long, String>>()
        return rows.indices.filterNot { i -> seen.add(Triple(rows[i].date, rows[i].amount, rows[i].description.lowercase())) }.toSet()
    }
}
