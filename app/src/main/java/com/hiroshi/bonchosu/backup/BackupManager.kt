package com.hiroshi.bonchosu.backup

import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import com.hiroshi.bonchosu.data.db.AppDatabase
import com.hiroshi.bonchosu.data.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class RestoreException(val problem: RestoreProblem) : Exception(problem.name)

/**
 * Backup = one .zip you save wherever you like (Google Drive, Downloads…) through Android's file picker:
 * manifest.txt, money.db (+ money.db-wal) and settings.txt. Nothing is uploaded by the app itself.
 *
 * Restoring never swaps the database under a running app: the file is checked and staged, the app restarts,
 * and [applyPendingRestore] puts it in place before anything opens the database.
 */
class BackupManager(
    private val context: Context,
    private val db: AppDatabase,
    private val settings: SettingsRepository,
) {
    private val dbFile get() = context.getDatabasePath(DB_NAME)

    suspend fun backup(target: Uri) = withContext(Dispatchers.IO) {
        val sqlite = db.openHelper.writableDatabase
        // Fold the write-ahead log into the main file first, so the copy is (nearly) self-contained.
        sqlite.query("PRAGMA wal_checkpoint(TRUNCATE)").use { it.moveToFirst() }
        val manifest = BackupManifest(BackupManifest.FORMAT, sqlite.version, System.currentTimeMillis())
        // Stored before exporting, so a restored copy still knows when it was backed up.
        settings.setLastBackupAt(manifest.createdAt)
        val settingsText = SettingsCodec.encode(settings.exportAll())

        val out = requireNotNull(context.contentResolver.openOutputStream(target, "wt")) { "can't write backup" }
        ZipOutputStream(out.buffered()).use { zip ->
            zip.putEntry(MANIFEST, manifest.encode().toByteArray())
            zip.putEntry(SETTINGS, settingsText.toByteArray())
            // Holding a write transaction blocks other writers while the db and its log are copied as a pair.
            sqlite.beginTransaction()
            try {
                zip.putEntry(DB_NAME, dbFile)
                File(dbFile.path + "-wal").takeIf { it.exists() && it.length() > 0 }?.let { zip.putEntry("$DB_NAME-wal", it) }
            } finally {
                sqlite.endTransaction()
            }
        }
    }

    /** Checks the file and stages it for [applyPendingRestore]. Nothing changes until the app restarts. */
    suspend fun stageRestore(source: Uri) = withContext(Dispatchers.IO) {
        val staging = File(context.cacheDir, "restore-incoming").apply { deleteRecursively(); mkdirs() }
        try {
            var manifest: BackupManifest? = null
            val input = requireNotNull(context.contentResolver.openInputStream(source)) { "can't read backup" }
            ZipInputStream(input.buffered()).use { zip ->
                generateSequence { zip.nextEntry }.forEach { entry ->
                    val name = File(entry.name).name // never trust paths inside the zip
                    when (name) {
                        MANIFEST -> manifest = BackupManifest.decode(zip.readBytes().decodeToString())
                        SETTINGS, DB_NAME, "$DB_NAME-wal" -> File(staging, name).outputStream().use { zip.copyTo(it) }
                    }
                }
            }
            val appVersion = db.openHelper.readableDatabase.version
            BackupRules.check(manifest, appVersion)?.let { throw RestoreException(it) }
            val staged = File(staging, DB_NAME)
            if (!staged.exists()) throw RestoreException(RestoreProblem.NOT_A_BACKUP)

            // Opening the copy read-write merges its log into it and proves it's a real database of ours.
            val ok = runCatching {
                SQLiteDatabase.openDatabase(staged.path, null, SQLiteDatabase.OPEN_READWRITE).use { copy ->
                    copy.version in 1..appVersion &&
                        copy.rawQuery("SELECT COUNT(*) FROM sqlite_master WHERE name IN ('wallets', 'transactions')", null)
                            .use { it.moveToFirst() && it.getInt(0) == 2 }
                }
            }.getOrDefault(false)
            if (!ok) throw RestoreException(RestoreProblem.NOT_A_BACKUP)

            val pending = pendingDir(context).apply { deleteRecursively(); mkdirs() }
            staged.copyTo(File(pending, DB_NAME))
            File(staging, SETTINGS).takeIf { it.exists() }?.copyTo(File(pending, SETTINGS))
        } finally {
            staging.deleteRecursively()
        }
    }

    /** Restarts the app so the staged backup is applied before the database opens. */
    fun restartApp() {
        val launch = requireNotNull(context.packageManager.getLaunchIntentForPackage(context.packageName))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        context.startActivity(launch)
        Runtime.getRuntime().exit(0)
    }

    private fun ZipOutputStream.putEntry(name: String, bytes: ByteArray) {
        putNextEntry(ZipEntry(name)); write(bytes); closeEntry()
    }

    private fun ZipOutputStream.putEntry(name: String, file: File) {
        putNextEntry(ZipEntry(name)); file.inputStream().use { it.copyTo(this) }; closeEntry()
    }

    companion object {
        private const val DB_NAME = "money.db"
        private const val MANIFEST = "manifest.txt"
        private const val SETTINGS = "settings.txt"

        private fun pendingDir(context: Context) = File(context.filesDir, "restore-pending")

        /**
         * Called from Application.onCreate before anything opens the database. Swaps in a staged backup,
         * keeping the previous database in files/pre-restore so a failed swap is put back.
         * Returns true if a backup was applied.
         */
        fun applyPendingRestore(context: Context, settings: SettingsRepository): Boolean {
            val pending = pendingDir(context)
            val incoming = File(pending, DB_NAME)
            if (!incoming.exists()) return false
            val dbFile = context.getDatabasePath(DB_NAME)
            val sidecars = listOf("", "-wal", "-shm", "-journal").map { File(dbFile.path + it) }
            val previous = File(context.filesDir, "pre-restore").apply { deleteRecursively(); mkdirs() }
            sidecars.filter { it.exists() }.forEach { it.copyTo(File(previous, it.name)) }
            try {
                sidecars.forEach { it.delete() }
                dbFile.parentFile?.mkdirs()
                incoming.copyTo(dbFile)
                File(pending, SETTINGS).takeIf { it.exists() }?.let { file ->
                    runBlocking { settings.importAll(SettingsCodec.decode(file.readText())) }
                }
            } catch (e: Exception) {
                sidecars.forEach { it.delete() }
                previous.listFiles()?.forEach { it.copyTo(File(dbFile.parentFile, it.name), overwrite = true) }
                pending.deleteRecursively()
                return false
            }
            pending.deleteRecursively()
            return true
        }
    }
}
