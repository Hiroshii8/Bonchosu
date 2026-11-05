package com.hiroshi.moneyapp.capture

import com.hiroshi.moneyapp.data.TransactionRepository
import com.hiroshi.moneyapp.data.db.AppDatabase
import com.hiroshi.moneyapp.data.db.TransactionEntity
import com.hiroshi.moneyapp.data.db.TxSource
import com.hiroshi.moneyapp.data.db.TxStatus
import com.hiroshi.moneyapp.data.db.WalletEntity
import com.hiroshi.moneyapp.data.settings.SettingsRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Turns one notification into (at most) one transaction:
 * flag check → parse → find/create wallet → de-duplicate → categorize → save (pending or confirmed).
 */
class CaptureProcessor(
    private val db: AppDatabase,
    private val settings: SettingsRepository,
    private val transactions: TransactionRepository,
) {
    // Banks often post the same notification twice in a row; serialize so the dedup check sees both.
    private val mutex = Mutex()

    /** Returns the new transaction id, or null if nothing was saved. */
    suspend fun handle(packageName: String, title: String, text: String, postTime: Long): Long? {
        val s = settings.current()
        if (!s.autoCaptureEnabled) return null // feature flag: off means we never touch notifications

        // Only apps you picked are read; with none picked yet, nothing is.
        val apps = s.captureApps ?: return null
        val linked = apps.firstOrNull { it.packageName == packageName }?.walletId?.let { db.walletDao().get(it) }
        val parser = ParserRegistry.resolve(packageName, apps, linked?.type) ?: return null
        val parsed = parser.parse(title, text, postTime) ?: return null
        return mutex.withLock { save(parsed, s.autoSaveConfident, linked) }
    }

    private suspend fun save(p: ParsedCapture, autoSave: Boolean, linked: WalletEntity?): Long? {
        val wallet = linked ?: walletFor(p)

        // Same wallet, type and amount within ±3 minutes = the same transaction
        // (a repeated notification, or something you already typed in by hand).
        if (db.transactionDao().countSimilar(wallet.id, p.type, p.amount, p.timestamp - DEDUP_WINDOW_MS, p.timestamp + DEDUP_WINDOW_MS) > 0) {
            return null
        }

        val merchant = p.merchant?.let(MerchantKey::pretty)
        val guess = Categorizer.guess(p.type, merchant?.let(MerchantKey::of), p.rawText, transactions.merchantRules())
        val categoryId = when (guess) {
            is CategoryGuess.Learned -> guess.categoryId
            is CategoryGuess.Builtin -> db.categoryDao().byKey(guess.key)?.takeIf { !it.archived }?.id
            CategoryGuess.Unknown -> null
        }
        val confident = guess.confident && categoryId != null && !p.transferCandidate

        return db.transactionDao().insert(
            TransactionEntity(
                type = p.type,
                amount = p.amount,
                walletId = wallet.id,
                categoryId = categoryId,
                occurredAt = p.timestamp,
                merchant = merchant,
                source = TxSource.NOTIFICATION,
                status = if (autoSave && confident) TxStatus.CONFIRMED else TxStatus.PENDING_REVIEW,
                rawText = p.rawText,
            )
        )
    }

    /** The wallet linked to this source, or a new one named after it (you set its starting balance later). */
    private suspend fun walletFor(p: ParsedCapture): WalletEntity {
        val dao = db.walletDao()
        dao.findByCaptureSource(p.source)?.let { return it }
        val id = dao.insert(WalletEntity(name = p.source, type = p.walletType, captureSource = p.source))
        return requireNotNull(dao.get(id))
    }

    companion object {
        const val DEDUP_WINDOW_MS = 3 * 60 * 1000L
    }
}
