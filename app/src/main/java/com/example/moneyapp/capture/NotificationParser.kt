package com.hiroshi.moneyapp.capture

import com.hiroshi.moneyapp.core.Money
import com.hiroshi.moneyapp.data.db.TxType
import com.hiroshi.moneyapp.data.db.WalletType

// Pure Kotlin, no Android imports: unit-test it on the JVM with real notification samples.

data class ParsedCapture(
    val amount: Long,
    /** EXPENSE or INCOME as seen from the source wallet. */
    val type: TxType,
    val merchant: String?,
    /** Display name of the source, e.g. "BCA". Also the key that links it to a wallet. */
    val source: String,
    val walletType: WalletType,
    /** Looks like moving your own money (top-up, transfer between own accounts): always sent to review. */
    val transferCandidate: Boolean,
    val rawText: String,
    val timestamp: Long,
)

interface NotificationParser {
    val source: String
    val walletType: WalletType
    fun parse(title: String, text: String, postTime: Long): ParsedCapture?
}

/**
 * Keyword + regex parser that handles the common Indonesian and English bank / e-wallet wording.
 * Write a dedicated parser for a bank whose wording needs special handling.
 */
class KeywordParser(
    override val source: String,
    override val walletType: WalletType,
) : NotificationParser {

    override fun parse(title: String, text: String, postTime: Long): ParsedCapture? {
        val full = "$title. $text".trim()

        val lower = full.lowercase()
        if (IGNORE.any { it.containsMatchIn(lower) }) return null

        val topUp = TOP_UP.any { it.containsMatchIn(lower) }
        val type = when {
            // A top-up seen by the e-wallet is money coming in; seen by the bank it's money going out.
            topUp -> if (walletType == WalletType.EWALLET) TxType.INCOME else TxType.EXPENSE
            CREDIT.any { it.containsMatchIn(lower) } -> TxType.INCOME
            DEBIT.any { it.containsMatchIn(lower) } -> TxType.EXPENSE
            else -> return null
        }

        val amountRaw = AMOUNT.find(full)?.groupValues?.get(1) ?: return null
        val amount = Money.parseRupiah(amountRaw) ?: return null
        if (amount <= 0) return null

        val merchant = MERCHANT.find(full)?.groupValues?.get(1)?.trim()?.takeIf { it.length >= 3 }
        val ownTransfer = OWN_TRANSFER.any { it.containsMatchIn(lower) }

        return ParsedCapture(
            amount = amount,
            type = type,
            merchant = merchant,
            source = source,
            walletType = walletType,
            transferCandidate = topUp || ownTransfer,
            rawText = full,
            timestamp = postTime,
        )
    }

    companion object {
        private fun words(vararg w: String) = w.map { Regex("""\b${Regex.escape(it)}\b""") }

        /** Not a completed money movement: skip. */
        val IGNORE = words(
            "otp", "kode verifikasi", "kode otp", "jangan berikan", "jangan bagikan", "promo", "diskon",
            "login", "gagal", "dibatalkan", "ditolak", "failed", "declined", "pengingat", "reminder",
            "jatuh tempo", "akan didebet", "menunggu pembayaran",
            "verification code", "security code", "do not share", "don't share", "discount", "log in", "sign in",
            "unsuccessful", "cancelled", "canceled", "rejected", "due date", "will be debited",
            "waiting for payment", "awaiting payment",
        )

        val TOP_UP = words("top up", "topup", "top-up", "isi saldo", "isi ulang saldo", "add money")

        val CREDIT = words(
            "dana masuk", "uang masuk", "transfer masuk", "kamu menerima", "anda menerima", "telah menerima",
            "menerima transfer", "diterima", "received", "incoming", "refund", "pengembalian dana", "cashback masuk",
            "money in", "credited", "deposit", "refunded",
        )

        val DEBIT = words(
            "debit", "didebet", "debet", "pembayaran", "pembelian", "transfer ke", "transfer keluar", "tarik tunai",
            "qris", "transaksi", "payment", "purchase", "bayar", "membayar", "paid",
            "melakukan transfer", "transfer real-time", "transfer realtime", "transfer bi-fast", "transfer online",
            "transfer to", "transfer of", "outgoing transfer", "withdrawal", "transaction", "debited", "charged",
            "spent", "sent", "pay",
        )

        val OWN_TRANSFER = words("rekening sendiri", "own account", "pindah dana", "between accounts")

        // "Rp 1.250.000,00", "Rp50.000", "IDR 1,250,000.00"
        val AMOUNT = Regex("""(?i)(?:rp\.?|idr)\s?([\d.,]+)""")

        // Text after "di"/"ke"/"at"/"to"/"merchant" up to an amount/date word or punctuation.
        // "to your account" is the destination of incoming money, not a merchant.
        val MERCHANT = Regex(
            """(?i)(?:\bdi|\bke|\bat|\bto(?!\s+your\b)|\bmerchant:?)\s+([A-Za-z0-9 .&'\-]{3,40}?)(?=\s+(?:sebesar|senilai|rp|idr|pada|tgl|tanggal|via|berhasil|of|on|from|for|using|was|is|has|successful|successfully|completed)\b|[.,:]|$)"""
        )
    }
}

/**
 * Superbank wording:
 * - Expense: title "Transfer ke X", text "Transfer sebesar Rp.50,000.00 dari Tabungan kamu berhasil"
 * - Income: the title or text contains "masuk"
 * The English equivalents ("Transfer to X", "received", ...) are guesses until checked against real samples.
 * Anything else goes to the generic keyword parser.
 */
class SuperbankParser : NotificationParser {
    override val source = "Superbank"
    override val walletType = WalletType.BANK

    private val fallback = KeywordParser(source, walletType)

    override fun parse(title: String, text: String, postTime: Long): ParsedCapture? {
        val full = "$title. $text".trim()
        val lower = full.lowercase()
        if (KeywordParser.IGNORE.any { it.containsMatchIn(lower) }) return null

        // Checked first so a recipient like "Masuk Jaya" or "Incoming Ltd" still counts as an expense.
        val recipient = TRANSFER_OUT_TITLE.find(title.trim())?.groupValues?.get(1)?.trim()
        val type = when {
            recipient != null -> TxType.EXPENSE
            MONEY_IN.containsMatchIn(lower) -> TxType.INCOME
            else -> return fallback.parse(title, text, postTime)
        }

        // Text first: the amount regex would also match the end of a name like "Corp 5" in the title.
        val amountRaw = (KeywordParser.AMOUNT.find(text) ?: KeywordParser.AMOUNT.find(title))
            ?.groupValues?.get(1) ?: return null
        val amount = Money.parseRupiah(amountRaw) ?: return null
        if (amount <= 0) return null

        return ParsedCapture(
            amount = amount,
            type = type,
            merchant = recipient?.takeIf { it.isNotEmpty() },
            source = source,
            walletType = walletType,
            transferCandidate = KeywordParser.OWN_TRANSFER.any { it.containsMatchIn(lower) },
            rawText = full,
            timestamp = postTime,
        )
    }

    private companion object {
        val TRANSFER_OUT_TITLE = Regex("""(?i)^transfer (?:ke|to)\s+(.+)$""")
        val MONEY_IN = Regex("""\b(?:masuk|received|incoming|money in|credited|deposit)\b""")
    }
}

object ParserRegistry {
    /**
     * Package name → parser. ⚠️ Verify package names on your own phone with the debug log in
     * TransactionListenerService (they're a best guess and banks sometimes ship new apps).
     */
    private val parsers: Map<String, NotificationParser> = buildMap {
        fun bank(name: String, vararg pkgs: String) = pkgs.forEach { put(it, KeywordParser(name, WalletType.BANK)) }
        fun wallet(name: String, vararg pkgs: String) = pkgs.forEach { put(it, KeywordParser(name, WalletType.EWALLET)) }


        bank("BCA", "com.bca", "com.bca.mybca.omni.android")
        bank("Mandiri", "id.bmri.livin")
        bank("BRI", "id.co.bri.brimo")
        bank("BNI", "src.com.bni")
        bank("Danamon", "com.dbank.mobile")
        wallet("GoPay", "com.gojek.gopay", "com.gojek.app")
        wallet("OVO", "ovo.id")
        wallet("DANA", "id.dana")
        wallet("ShopeePay", "com.shopee.id")
        bank("Seabank", "id.co.bankbkemobile.digitalbank")
        listOf("id.co.bankfama.android").forEach { put(it, SuperbankParser()) }
    }

    fun forPackage(pkg: String): NotificationParser? = parsers[pkg]

    /** Distinct source names, for the Settings / wallet-link pickers. */
    val sources: List<String> get() = parsers.values.map { it.source }.distinct()

    /** Packages with built-in support: suggested in the app picker, and used until you pick apps yourself. */
    val knownPackages: Set<String> get() = parsers.keys

    /**
     * The parser for a notification. With [chosen] apps (picked by you), only those are read: a bank with its own
     * wording rules keeps them, any other app gets the keyword rules under its own name. [chosen] null means you
     * haven't picked apps yet, so the built-in list applies.
     */
    fun resolve(pkg: String, chosen: List<CaptureApp>?, walletType: WalletType?): NotificationParser? {
        val builtIn = parsers[pkg]
        if (chosen == null) return builtIn
        val app = chosen.firstOrNull { it.packageName == pkg } ?: return null
        if (builtIn != null && builtIn !is KeywordParser) return builtIn
        return KeywordParser(app.label, walletType ?: builtIn?.walletType ?: WalletType.BANK)
    }
}

/** An app you picked for auto-capture. [walletId] null = a wallet named after the app, made on first capture. */
data class CaptureApp(val packageName: String, val label: String, val walletId: Long?) {
    companion object {
        private const val SEP = '\t'

        fun encode(app: CaptureApp) = listOf(app.packageName, app.label.replace(SEP, ' '), app.walletId?.toString().orEmpty()).joinToString(SEP.toString())

        fun decode(raw: String): CaptureApp? {
            val parts = raw.split(SEP)
            if (parts.size < 2 || parts[0].isBlank()) return null
            return CaptureApp(parts[0], parts[1], parts.getOrNull(2)?.toLongOrNull())
        }
    }
}
