package com.hiroshi.moneyapp.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BackupFormatTest {

    @Test
    fun `settings of every type survive the round trip`() {
        val values = mapOf<String, Any>(
            "feature_auto_capture" to true,
            "theme" to "DARK",
            "home_layout" to "REVIEW,-IMPORT_STATEMENT",
            "last_backup_at" to 1_762_300_000_000L,
            "count" to 3,
            "capture_apps" to setOf("id.bank\tMy Bank\t4", "com.wallet\tDompet, Saya\t"),
            "budget_alerts_sent" to emptySet<String>(),
            "weird name = with\ttabs\nand lines" to "value\nwith\nlines",
        )
        assertEquals(values, SettingsCodec.decode(SettingsCodec.encode(values)))
    }

    @Test
    fun `broken lines are skipped instead of failing the restore`() {
        val good = SettingsCodec.encode(mapOf("theme" to "DARK"))
        assertEquals(mapOf("theme" to "DARK"), SettingsCodec.decode("garbage\n$good\nzz\t!!\tx\nb\tdGhlbWU=\tmaybe"))
    }

    @Test
    fun `manifest round trip`() {
        val m = BackupManifest(BackupManifest.FORMAT, dbVersion = 3, createdAt = 42)
        assertEquals(m, BackupManifest.decode(m.encode()))
        assertNull(BackupManifest.decode("hello"))
    }

    @Test
    fun `older backups restore and newer ones are refused`() {
        assertNull(BackupRules.check(BackupManifest(1, dbVersion = 1, createdAt = 0), appDbVersion = 3))
        assertNull(BackupRules.check(BackupManifest(1, dbVersion = 3, createdAt = 0), appDbVersion = 3))
        assertEquals(RestoreProblem.NEWER_APP, BackupRules.check(BackupManifest(1, dbVersion = 4, createdAt = 0), appDbVersion = 3))
        assertEquals(RestoreProblem.NEWER_APP, BackupRules.check(BackupManifest(2, dbVersion = 3, createdAt = 0), appDbVersion = 3))
        assertEquals(RestoreProblem.NOT_A_BACKUP, BackupRules.check(null, appDbVersion = 3))
    }
}
