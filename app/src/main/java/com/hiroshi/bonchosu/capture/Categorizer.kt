package com.hiroshi.bonchosu.capture

import com.hiroshi.bonchosu.data.db.TxType

// Pure Kotlin: guesses a category for a captured transaction.

sealed interface CategoryGuess {
    val confident: Boolean

    /** From your own past corrections: the strongest signal. */
    data class Learned(val categoryId: Long) : CategoryGuess { override val confident = true }

    /** From a word you set yourself (Settings → Keyword rules). */
    data class Custom(val categoryId: Long) : CategoryGuess { override val confident = true }

    /** From the built-in keyword list; resolve the key to a category id. */
    data class Builtin(val key: String) : CategoryGuess { override val confident = true }

    data object Unknown : CategoryGuess { override val confident = false }
}

/** Your own word → category rule. [type] is the category's side (expense or income); a rule only matches that side. */
data class KeywordRule(val keyword: String, val categoryId: Long, val type: TxType)

object Keywords {
    /** How a typed word is stored and compared: "  Kopi   KENANGAN " → "kopi kenangan". */
    fun normalize(raw: String): String = raw.lowercase().trim().replace(Regex("""\s+"""), " ")

    /** Whole words only, so "bca" doesn't match "abcabc", but "kopi" matches "Kopi Kenangan". */
    fun matches(keyword: String, lowerText: String): Boolean =
        keyword.isNotEmpty() && Regex("""(?<![\p{L}\p{N}])${Regex.escape(keyword)}(?![\p{L}\p{N}])""").containsMatchIn(lowerText)
}

/** What the guesser knows, loaded once per batch of transactions. */
class CategoryHints(
    private val learned: Map<String, Long>,
    private val keywords: List<KeywordRule>,
    /** Built-in key → category id, for categories you haven't hidden. */
    private val builtinIds: Map<String, Long>,
) {
    /** The category to use (null = none fits) and whether it's sure enough to save without review. */
    fun guess(type: TxType, merchant: String?, rawText: String): Pair<Long?, Boolean> {
        val text = listOfNotNull(merchant, rawText).joinToString(" ")
        return when (val g = Categorizer.guess(type, merchant?.let(MerchantKey::of), text, learned, keywords)) {
            is CategoryGuess.Learned -> g.categoryId to true
            is CategoryGuess.Custom -> g.categoryId to true
            is CategoryGuess.Builtin -> builtinIds[g.key].let { it to (it != null) }
            CategoryGuess.Unknown -> null to false
        }
    }
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
     * Order: your corrections for this exact merchant, then your own words, then the built-in list.
     * @param learned merchantKey → categoryId, from confirmed captures.
     * @param merchantKey normalised merchant (MerchantKey.of), if any.
     * @param custom your own word rules; the longest matching word wins.
     */
    fun guess(
        type: TxType,
        merchantKey: String?,
        rawText: String,
        learned: Map<String, Long>,
        custom: List<KeywordRule> = emptyList(),
    ): CategoryGuess {
        merchantKey?.let { learned[it] }?.let { return CategoryGuess.Learned(it) }

        val text = rawText.lowercase()
        custom.filter { it.type == type && Keywords.matches(it.keyword, text) }
            .maxByOrNull { it.keyword.length }
            ?.let { return CategoryGuess.Custom(it.categoryId) }

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
