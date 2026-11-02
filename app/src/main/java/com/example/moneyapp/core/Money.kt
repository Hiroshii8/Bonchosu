package com.example.moneyapp.core

/**
 * Amounts are stored as whole rupiah in a Long (no floating point, ever).
 * Formatting always uses Indonesian grouping ("Rp 1.250.000"), whatever the UI language,
 * because that's how rupiah amounts are written.
 */
object Money {

    fun format(amount: Long, withSymbol: Boolean = true): String {
        val negative = amount < 0
        val digits = kotlin.math.abs(amount).toString()
        val grouped = digits.reversed().chunked(3).joinToString(".").reversed()
        val body = if (withSymbol) "Rp $grouped" else grouped
        return if (negative) "−$body" else body
    }

    /** "+Rp 50.000" / "−Rp 50.000" / "Rp 0". */
    fun formatSigned(amount: Long): String = when {
        amount > 0 -> "+" + format(amount)
        else -> format(amount)
    }

    /** Short form for charts: "1,2 jt", "850 rb". */
    fun formatShort(amount: Long): String {
        val a = kotlin.math.abs(amount)
        val sign = if (amount < 0) "−" else ""
        return when {
            a >= 1_000_000_000 -> sign + oneDecimal(a / 1_000_000_000.0) + " M"
            a >= 1_000_000 -> sign + oneDecimal(a / 1_000_000.0) + " jt"
            a >= 1_000 -> sign + (a / 1_000).toString() + " rb"
            else -> sign + a.toString()
        }
    }

    private fun oneDecimal(v: Double): String {
        val tenths = Math.round(v * 10)
        val whole = tenths / 10
        val frac = tenths % 10
        return if (frac == 0L) whole.toString() else "$whole,$frac"
    }

    /**
     * Parses rupiah text as written in bank notifications and statements:
     * "1.250.000,00", "50.000", "1,250,000.00", "Rp35.000." → whole rupiah.
     * Returns null if there are no digits.
     */
    fun parseRupiah(raw: String): Long? {
        var s = raw.trim().trimEnd('.', ',', ' ')
        // Drop a 1–2 digit decimal part (",00" / ".00" / ",5").
        s = s.replace(Regex("""[.,]\d{1,2}$"""), "")
        val digits = s.filter { it.isDigit() }
        if (digits.isEmpty() || digits.length > 15) return null
        return digits.toLongOrNull()
    }
}
