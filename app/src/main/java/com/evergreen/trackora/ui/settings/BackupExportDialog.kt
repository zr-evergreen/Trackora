package com.evergreen.trackora.ui.settings

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.window.DialogProperties
import com.evergreen.trackora.R
import com.evergreen.trackora.export.BackupFailure
import com.evergreen.trackora.export.BackupProgress
import com.evergreen.trackora.ui.text.localizedDigits
import com.evergreen.trackora.ui.theme.TrackoraSpacing
import java.io.FileNotFoundException
import java.io.InputStream
import java.io.OutputStream

const val BackupDialogTag = "backup_dialog"

/**
 * Shows a backup being made, saved, and how it ended. Nothing shows while
 * the system's save dialog is open. While a backup is being built it can be
 * cancelled; while it is being saved it can't, so a half-written file isn't
 * left behind by a tap.
 */
@Composable
fun BackupExportDialog(
    state: BackupUiState,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
) {
    when (state) {
        BackupUiState.Idle, BackupUiState.ChoosingDestination -> Unit

        is BackupUiState.Building -> ProgressDialog(
            title = stringResource(R.string.backup_working_title),
            message = progressText(state.progress),
            fraction = (state.progress as? BackupProgress.Photos)?.let { if (it.total == 0) 1f else it.done.toFloat() / it.total },
            onCancel = onCancel,
        )

        BackupUiState.Saving -> ProgressDialog(
            title = stringResource(R.string.backup_working_title),
            message = stringResource(R.string.backup_saving),
            fraction = null,
            onCancel = null,
        )

        is BackupUiState.Saved -> ResultDialog(
            title = stringResource(R.string.backup_saved_title),
            lines = listOfNotNull(
                stringResource(R.string.backup_saved_file, state.fileName),
                stringResource(
                    R.string.backup_saved_counts,
                    localizedDigits(state.summary.entries.toString()),
                    localizedDigits(state.summary.photos.toString()),
                ),
                state.summary.missingPhotos.takeIf { it > 0 }?.let {
                    stringResource(R.string.backup_saved_missing, localizedDigits(it.toString()))
                },
            ),
            onDismiss = onDismiss,
        )

        is BackupUiState.Failed -> ResultDialog(
            title = stringResource(R.string.backup_failed_title),
            lines = listOf(stringResource(failureText(state.reason))),
            onDismiss = onDismiss,
        )

        BackupUiState.Refused -> ResultDialog(
            title = stringResource(R.string.backup_refused_title),
            lines = listOf(stringResource(R.string.backup_refused)),
            onDismiss = onDismiss,
        )
    }
}

@Composable
private fun progressText(progress: BackupProgress?): String = when (progress) {
    null, BackupProgress.Settings -> stringResource(R.string.backup_progress_settings)
    is BackupProgress.Entries -> stringResource(R.string.backup_progress_entries, localizedDigits(progress.read.toString()))
    is BackupProgress.Photos -> stringResource(
        R.string.backup_progress_photos,
        localizedDigits(progress.done.toString()),
        localizedDigits(progress.total.toString()),
    )
    BackupProgress.Verifying -> stringResource(R.string.backup_progress_verifying)
}

private fun failureText(reason: BackupFailure): Int = when (reason) {
    BackupFailure.DATA -> R.string.backup_failed_data
    BackupFailure.PHOTO_CHANGED -> R.string.backup_failed_photo_changed
    BackupFailure.TOO_LARGE -> R.string.backup_failed_too_large
    BackupFailure.STORAGE -> R.string.backup_failed_storage
    BackupFailure.VERIFICATION -> R.string.backup_failed_verification
    BackupFailure.SAVE -> R.string.backup_failed_save
    BackupFailure.SAVE_VERIFICATION -> R.string.backup_failed_save_verification
}

@Composable
private fun ProgressDialog(title: String, message: String, fraction: Float?, onCancel: (() -> Unit)?) {
    AlertDialog(
        // Not dismissed by tapping outside or Back: only Cancel stops a backup.
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        modifier = Modifier.testTag(BackupDialogTag),
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(TrackoraSpacing.md)) {
                Text(message, style = MaterialTheme.typography.bodyMedium)
                if (fraction == null) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                } else {
                    LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = {
            if (onCancel != null) TextButton(onClick = onCancel) { Text(stringResource(R.string.backup_cancel)) }
        },
    )
}

@Composable
private fun ResultDialog(title: String, lines: List<String>, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag(BackupDialogTag),
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(TrackoraSpacing.sm)) {
                lines.forEach { line ->
                    // File names read left to right in either language.
                    Text(line, style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Content))
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.backup_ok)) } },
    )
}

/** A document the system's save dialog created, opened through the content resolver. */
internal class ContentDestination(private val resolver: ContentResolver, private val uri: Uri) : BackupDestination {

    /** Truncating, so a file replaced at the user's request keeps none of its old bytes; the read-back checks it. */
    override fun open(): OutputStream? = try {
        resolver.openOutputStream(uri, "wt")
    } catch (e: IllegalArgumentException) {
        resolver.openOutputStream(uri, "w")
    } catch (e: FileNotFoundException) {
        resolver.openOutputStream(uri, "w")
    }

    override fun reopen(): InputStream? = resolver.openInputStream(uri)

    override fun displayName(): String? =
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getString(0) else null
        }
}
