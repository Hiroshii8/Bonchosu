package com.hiroshi.bonchosu.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.hiroshi.bonchosu.AppContainer
import com.hiroshi.bonchosu.R
import com.hiroshi.bonchosu.backup.RestoreException
import com.hiroshi.bonchosu.backup.RestoreProblem
import com.hiroshi.bonchosu.core.Periods
import com.hiroshi.bonchosu.ui.components.ScreenScaffold
import com.hiroshi.bonchosu.ui.components.SectionCard
import com.hiroshi.bonchosu.ui.components.appContainer
import com.hiroshi.bonchosu.ui.components.formatDate
import com.hiroshi.bonchosu.ui.theme.Brand
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

data class BackupUi(
    val working: Boolean = false,
    val saved: Boolean = false,
    val failed: Int? = null,
    /** A checked backup is waiting for "Restore and restart". */
    val readyToRestore: Boolean = false,
)

class BackupViewModel(private val c: AppContainer) : ViewModel() {
    val ui = MutableStateFlow(BackupUi())
    val lastBackupAt = c.settings.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun backup(target: Uri) = run {
        c.backup.backup(target)
        ui.update { it.copy(saved = true) }
    }

    fun pickRestore(source: Uri) = run {
        c.backup.stageRestore(source)
        ui.update { it.copy(readyToRestore = true) }
    }

    fun restartToRestore() = c.backup.restartApp()

    /** Cancelling only throws the staged copy away on the next real restore; nothing has been replaced yet. */
    fun cancelRestore() = ui.update { it.copy(readyToRestore = false) }

    private fun run(block: suspend () -> Unit) {
        ui.value = BackupUi(working = true)
        viewModelScope.launch {
            try {
                block()
                ui.update { it.copy(working = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: RestoreException) {
                ui.value = BackupUi(failed = if (e.problem == RestoreProblem.NEWER_APP) R.string.backup_error_newer else R.string.backup_error_invalid)
            } catch (_: Exception) {
                ui.value = BackupUi(failed = R.string.backup_error_generic)
            }
        }
    }
}

@Composable
fun BackupScreen(nav: NavController) {
    val c = appContainer()
    val vm: BackupViewModel = viewModel { BackupViewModel(c) }
    val s by vm.ui.collectAsStateWithLifecycle()
    val settings by vm.lastBackupAt.collectAsStateWithLifecycle()
    val today = LocalDate.now()
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri -> uri?.let(vm::backup) }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(vm::pickRestore) }

    if (s.readyToRestore) {
        AlertDialog(
            onDismissRequest = vm::cancelRestore,
            title = { Text(stringResource(R.string.backup_restore_confirm_title)) },
            text = { Text(stringResource(R.string.backup_restore_confirm_body)) },
            confirmButton = { TextButton(onClick = vm::restartToRestore) { Text(stringResource(R.string.backup_restore_confirm)) } },
            dismissButton = { TextButton(onClick = vm::cancelRestore) { Text(stringResource(R.string.cancel)) } },
        )
    }

    ScreenScaffold(title = stringResource(R.string.backup_title), onBack = { nav.popBackStack() }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(stringResource(R.string.backup_intro), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

            SectionCard {
                Text(stringResource(R.string.backup_save_title), style = MaterialTheme.typography.titleMedium)
                val last = settings?.lastBackupAt
                Text(
                    if (last == null) stringResource(R.string.backup_never) else stringResource(R.string.backup_last, formatDate(Periods.toDate(last))),
                    fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    onClick = { save.launch("Bonchosu-backup-$today.zip") },
                    enabled = !s.working, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().height(52.dp),
                ) { Text(stringResource(R.string.backup_save)) }
                Text(stringResource(R.string.backup_drive_hint), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            SectionCard {
                Text(stringResource(R.string.backup_restore_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.backup_restore_hint), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(
                    onClick = { open.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")) },
                    enabled = !s.working, modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.backup_restore)) }
            }

            if (s.working) CircularProgressIndicator()
            if (s.saved) Notice(stringResource(R.string.backup_saved), Brand.AccentSoft, Brand.Accent)
            s.failed?.let { Notice(stringResource(it), MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer) }
            Text(stringResource(R.string.backup_privacy), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Notice(text: String, background: androidx.compose.ui.graphics.Color, ink: androidx.compose.ui.graphics.Color) {
    Surface(color = background, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Text(text, color = ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(12.dp))
    }
}
