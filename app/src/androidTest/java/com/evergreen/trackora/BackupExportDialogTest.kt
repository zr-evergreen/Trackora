package com.evergreen.trackora

import android.Manifest
import android.os.Build
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.evergreen.trackora.export.BackupFailure
import com.evergreen.trackora.export.BackupProgress
import com.evergreen.trackora.export.BackupSummary
import com.evergreen.trackora.ui.settings.BackupExportDialog
import com.evergreen.trackora.ui.settings.BackupUiState
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The backup dialog shows each state's text, and its buttons do what they
 * say. It is shown in the app's own activity, the way the other app tests
 * launch it, with the dialog as the activity's content.
 */
@RunWith(AndroidJUnit4::class)
class BackupExportDialogTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val resources = instrumentation.targetContext.resources

    @Before
    fun allowNotifications() {
        // Otherwise MainActivity asks for it on Android 13 and later; the dialog pauses
        // the activity, and the compose test rule only sees a resumed activity's content.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            instrumentation.uiAutomation.grantRuntimePermission(instrumentation.targetContext.packageName, Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun text(id: Int, vararg args: Any) = resources.getString(id, *args)

    private fun show(content: @Composable () -> Unit, check: () -> Unit) {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { it.setContent { content() } }
            check()
        }
    }

    @Test
    fun buildingShowsProgressAndCanBeCancelled() {
        var cancelled = 0
        show({ BackupExportDialog(BackupUiState.Building(BackupProgress.Photos(3, 12)), onCancel = { cancelled++ }, onDismiss = {}) }) {
            compose.onNodeWithText(text(R.string.backup_progress_photos, "3", "12")).assertIsDisplayed()
            compose.onNodeWithText(text(R.string.backup_cancel)).performClick()
            compose.waitForIdle()
            assertEquals(1, cancelled)
        }
    }

    @Test
    fun savingHasNoCancel() {
        show({ BackupExportDialog(BackupUiState.Saving, onCancel = {}, onDismiss = {}) }) {
            compose.onNodeWithText(text(R.string.backup_saving)).assertIsDisplayed()
            compose.onNodeWithText(text(R.string.backup_cancel)).assertDoesNotExist()
        }
    }

    @Test
    fun savedShowsTheFileNameAndWhatItHolds() {
        var dismissed = 0
        show({
            BackupExportDialog(BackupUiState.Saved("Trackora-backup-2026-11-15-0930.zip", BackupSummary(57, 12, 2)), onCancel = {}, onDismiss = { dismissed++ })
        }) {
            compose.onNodeWithText(text(R.string.backup_saved_file, "Trackora-backup-2026-11-15-0930.zip")).assertIsDisplayed()
            compose.onNodeWithText(text(R.string.backup_saved_counts, "57", "12")).assertIsDisplayed()
            compose.onNodeWithText(text(R.string.backup_saved_missing, "2")).assertIsDisplayed()
            compose.onNodeWithText(text(R.string.backup_ok)).performClick()
            compose.waitForIdle()
            assertEquals(1, dismissed)
        }
    }

    @Test
    fun everyFailureHasItsOwnMessage() {
        val messages = mapOf(
            BackupFailure.DATA to R.string.backup_failed_data,
            BackupFailure.PHOTO_CHANGED to R.string.backup_failed_photo_changed,
            BackupFailure.TOO_LARGE to R.string.backup_failed_too_large,
            BackupFailure.STORAGE to R.string.backup_failed_storage,
            BackupFailure.VERIFICATION to R.string.backup_failed_verification,
            BackupFailure.SAVE to R.string.backup_failed_save,
            BackupFailure.SAVE_VERIFICATION to R.string.backup_failed_save_verification,
        )
        assertEquals(BackupFailure.entries.toSet(), messages.keys)
        assertEquals("each failure has a message of its own", messages.size, messages.values.toSet().size)
        var state by mutableStateOf(BackupFailure.DATA)
        show({ BackupExportDialog(BackupUiState.Failed(state), onCancel = {}, onDismiss = {}) }) {
            for ((failure, message) in messages) {
                state = failure
                compose.onNodeWithText(text(message)).assertIsDisplayed()
            }
        }
    }

    @Test
    fun aRefusedDatabaseIsExplained() {
        show({ BackupExportDialog(BackupUiState.Refused, onCancel = {}, onDismiss = {}) }) {
            compose.onNodeWithText(text(R.string.backup_refused)).assertIsDisplayed()
        }
    }
}
