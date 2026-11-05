package com.hiroshi.moneyapp.backup

import java.util.Base64

// Pure Kotlin, no Android imports: unit-tested on the JVM.

/** What's inside a backup .zip besides the database itself. */
data class BackupManifest(val format: Int, val dbVersion: Int, val createdAt: Long) {

    fun encode() = "format=$format\ndbVersion=$dbVersion\ncreatedAt=$createdAt\n"

    companion object {
        /** Bump when the file layout changes in a way older apps can't read. */
        const val FORMAT = 1

        fun decode(text: String): BackupManifest? {
            val values = text.lines().mapNotNull { line -> line.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0].trim() to it[1].trim() } }.toMap()
            return BackupManifest(
                format = values["format"]?.toIntOrNull() ?: return null,
                dbVersion = values["dbVersion"]?.toIntOrNull() ?: return null,
                createdAt = values["createdAt"]?.toLongOrNull() ?: 0L,
            )
        }
    }
}

/**
 * Settings as one line per value: "type<TAB>name<TAB>value", names and values base64-encoded so any
 * text survives. Sets are comma-separated base64 items. Unknown types are skipped when reading.
 */
object SettingsCodec {
    private val enc = Base64.getEncoder()
    private val dec = Base64.getDecoder()

    private fun b64(s: String) = enc.encodeToString(s.toByteArray())
    private fun unb64(s: String) = String(dec.decode(s))

    fun encode(values: Map<String, Any>): String = values.entries.sortedBy { it.key }.mapNotNull { (name, value) ->
        val (type, raw) = when (value) {
            is Boolean -> "b" to value.toString()
            is String -> "s" to b64(value)
            is Int -> "i" to value.toString()
            is Long -> "l" to value.toString()
            is Float -> "f" to value.toString()
            is Double -> "d" to value.toString()
            is Set<*> -> "set" to value.filterIsInstance<String>().sorted().joinToString(",") { b64(it) }
            else -> return@mapNotNull null
        }
        "$type\t${b64(name)}\t$raw"
    }.joinToString("\n")

    fun decode(text: String): Map<String, Any> = text.lines().filter { it.isNotBlank() }.mapNotNull { line ->
        val parts = line.split('\t')
        if (parts.size != 3) return@mapNotNull null
        val (type, name, raw) = parts
        val value: Any = runCatching {
            when (type) {
                "b" -> raw.toBooleanStrict()
                "s" -> unb64(raw)
                "i" -> raw.toInt()
                "l" -> raw.toLong()
                "f" -> raw.toFloat()
                "d" -> raw.toDouble()
                "set" -> if (raw.isEmpty()) emptySet<String>() else raw.split(',').map { unb64(it) }.toSet()
                else -> null
            }
        }.getOrNull() ?: return@mapNotNull null
        runCatching { unb64(name) }.getOrNull()?.let { it to value }
    }.toMap()
}

/** Why a backup file can't be restored. */
enum class RestoreProblem { NOT_A_BACKUP, NEWER_APP }

object BackupRules {
    /** A backup from a newer app could have tables this one doesn't know, so it's refused rather than half-read. */
    fun check(manifest: BackupManifest?, appDbVersion: Int): RestoreProblem? = when {
        manifest == null || manifest.format < 1 -> RestoreProblem.NOT_A_BACKUP
        manifest.format > BackupManifest.FORMAT || manifest.dbVersion > appDbVersion -> RestoreProblem.NEWER_APP
        else -> null
    }
}
