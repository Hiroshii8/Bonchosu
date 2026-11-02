package com.example.moneyapp.capture

import com.example.moneyapp.data.db.TxType

// Pure Kotlin: guesses a category for a captured transaction.

sealed interface CategoryGuess {
    val confident: Boolean

    /** From your own past corrections: the strongest signal. */
    data class Learned(val categoryId: Long) : CategoryGuess { override val confident = true }

    /** From the built-in keyword list; resolve the key to a category id. */
    data class Builtin(val key: String) : CategoryGuess { override val confident = true }

    data object Unknown : CategoryGuess { override val confident = false }
}

object MerchantKey {
    /** Normalised merchant used as the learning key: "KOPI SENJA  " → "kopi senja". */
    fun of(merchant: String): String =
        merchant.lowercase().replace(Regex("""[^a-z0-9 ]"""), " ").replace(Regex("""\s+"""), " ").trim()

    /** Display form: "KOPI SENJA" → "Kopi Senja"; mixed-case names are left alone. */
    fun pretty(raw: String): String {
        val s = raw.trim().replace(Regex("""\s+"""), " ")
        if (s != s.uppercase()) return s
        return s.lowercase().split(" ").joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }
    }
}

object Categorizer {

    /**
     * @param learned merchantKey → categoryId, from confirmed captures.
     * @param merchantKey normalised merchant (MerchantKey.of), if any.
     */
    fun guess(type: TxType, merchantKey: String?, rawText: String, learned: Map<String, Long>): CategoryGuess {
        merchantKey?.let { learned[it] }?.let { return CategoryGuess.Learned(it) }

        val text = rawText.lowercase()
        val rules = if (type == TxType.INCOME) INCOME_RULES else EXPENSE_RULES
        for ((key, patterns) in rules) {
            if (patterns.any { it.containsMatchIn(text) }) return CategoryGuess.Builtin(key)
        }
        return CategoryGuess.Unknown
    }

    private fun words(vararg w: String) = w.map { Regex("""\b${Regex.escape(it)}\b""") }

    // Order matters: first match wins ("grabfood" must hit food before "grab" hits transport).
    private val EXPENSE_RULES: List<Pair<String, List<Regex>>> = listOf(
        "fees" to words("biaya adm", "biaya admin", "biaya transfer", "admin fee", "materai", "biaya layanan", "transfer fee", "service fee", "stamp duty"),
        "food" to words(
            "grabfood", "gofood", "shopeefood", "kopi", "coffee", "cafe", "kafe", "resto", "restoran", "restaurant",
            "bakso", "warung", "starbucks", "kfc", "mcd", "mcdonald", "janji jiwa", "chatime", "pizza", "burger",
        ),
        "groceries" to words(
            "indomaret", "alfamart", "alfamidi", "superindo", "hypermart", "transmart", "lotte mart", "ranch market",
            "sayurbox", "astro", "fresh mart", "groceries", "grocery", "supermarket",
        ),
        "transport" to words(
            "gojek", "goride", "gocar", "grab", "grabbike", "grabcar", "maxim", "bluebird", "krl", "kai", "mrt",
            "transjakarta", "tol", "e-toll", "pertamina", "spbu", "shell", "parkir", "parking", "toll", "fuel", "taxi",
        ),
        "bills" to words(
            "pln", "token listrik", "listrik", "pdam", "telkom", "indihome", "bpjs", "pulsa", "paket data",
            "telkomsel", "indosat", "smartfren", "biznet", "first media", "iuran", "cicilan", "electricity", "water bill", "internet bill", "phone credit",
            "data package", "installment",
        ),
        "entertainment" to words(
            "netflix", "spotify", "youtube", "disney", "vidio", "cgv", "xxi", "cinema", "bioskop", "steam", "playstation",
        ),
        "health" to words("apotek", "kimia farma", "guardian", "watsons", "halodoc", "klinik", "rumah sakit", "dokter", "pharmacy", "clinic", "hospital", "doctor"),
        "education" to words("sekolah", "kursus", "udemy", "coursera", "ruangguru", "gramedia", "spp", "school", "tuition", "course"),
        "shopping" to words("tokopedia", "shopee", "lazada", "blibli", "tiktok shop", "zalora", "uniqlo", "ikea", "ace hardware"),
    )

    private val INCOME_RULES: List<Pair<String, List<Regex>>> = listOf(
        "salary" to words("gaji", "payroll", "salary"),
        "bonus" to words("bonus", "thr"),
        "interest" to words("bunga", "interest"),
    )
}
