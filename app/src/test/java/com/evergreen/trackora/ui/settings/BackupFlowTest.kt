package com.evergreen.trackora.ui.settings

import com.evergreen.trackora.backup.BackupApp
import com.evergreen.trackora.backup.BackupCheck
import com.evergreen.trackora.backup.BackupLanguage
import com.evergreen.trackora.backup.BackupReader
import com.evergreen.trackora.backup.BackupSettings
import com.evergreen.trackora.backup.BackupTheme
import com.evergreen.trackora.data.local.database.StoredEntry
import com.evergreen.trackora.export.BackupExport
import com.evergreen.trackora.export.BackupFailure
import com.evergreen.trackora.export.BackupSummary
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors

/** The settings screen's backup flow: build, choose where, save, and every way out of it. */
@OptIn(ExperimentalCoroutinesApi::class)
class BackupFlowTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val main = Executors.newSingleThreadExecutor()
    private lateinit var work: File
    private var refused = false
    private var duringRead: () -> Unit = {}

    @Before
    fun setUp() {
        Dispatchers.setMain(main.asCoroutineDispatcher())
        work = File(temp.root, "backup-export")
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        main.shutdownNow()
    }

    private fun viewModel(): SettingsViewModel {
        val export = BackupExport(
            workFolder = work,
            photoFolder = { null },
            isRefused = { refused },
            readSettings = { BackupSettings("", "", "", true, BackupTheme.SYSTEM, BackupLanguage.SYSTEM) },
            readEntries = { onRow ->
                onRow(StoredEntry(1, "Entry", null, 2, "COMPLETED", "2026-11-15", null, null, null, null))
                duringRead()
                1
            },
            app = BackupApp("com.evergreen.trackora", "1", 1),
            databaseVersion = 2,
            now = { Instant.parse("2026-11-15T09:30:00Z") },
            zone = { ZoneOffset.UTC },
        )
        return SettingsViewModel(mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true), export)
    }

    private class MemoryDestination(private val name: String? = "Chosen name.zip") : BackupDestination {
        val bytes = ByteArrayOutputStream()
        override fun open(): OutputStream = bytes
        override fun reopen(): InputStream = bytes.toByteArray().inputStream()
        override fun displayName(): String? = name
    }

    private fun SettingsViewModel.awaitState(predicate: (BackupUiState) -> Boolean): BackupUiState = runBlocking {
        withTimeout(10_000) { backupState.first(predicate) }
    }

    private fun SettingsViewModel.nextEvent(): SettingsEvent = runBlocking { withTimeout(10_000) { eventFlow.first() } }

    private fun folders(): List<String> = work.list()?.toList().orEmpty()

    @Test
    fun aBackupIsBuiltThenSavedWhereTheUserChose() {
        val vm = viewModel()
        vm.startBackup()
        assertEquals(SettingsEvent.ChooseBackupDestination("Trackora-backup-2026-11-15-0930.zip"), vm.nextEvent())
        assertEquals(BackupUiState.ChoosingDestination, vm.backupState.value)

        val destination = MemoryDestination()
        vm.onBackupDestination(destination)
        val saved = vm.awaitState { it is BackupUiState.Saved } as BackupUiState.Saved
        assertEquals("Chosen name.zip", saved.fileName)
        assertEquals(BackupSummary(1, 0, 0), saved.summary)
        val copy = File(temp.root, "copy.zip").apply { writeBytes(destination.bytes.toByteArray()) }
        assertTrue(BackupReader.verify(copy) is BackupCheck.Valid)
        assertEquals("the private copy is gone once saved", emptyList<String>(), folders())

        vm.dismissBackup()
        assertEquals(BackupUiState.Idle, vm.backupState.value)
    }

    @Test
    fun withoutADisplayNameTheSuggestedNameIsShown() {
        val vm = viewModel()
        vm.startBackup()
        vm.nextEvent()
        vm.onBackupDestination(MemoryDestination(name = null))
        val saved = vm.awaitState { it is BackupUiState.Saved } as BackupUiState.Saved
        assertEquals("Trackora-backup-2026-11-15-0930.zip", saved.fileName)
    }

    @Test
    fun dismissingTheSaveDialogSavesNothingAndKeepsNothing() {
        val vm = viewModel()
        vm.startBackup()
        vm.nextEvent()
        vm.onBackupDestination(null)
        assertEquals(SettingsEvent.BackupNotSaved, vm.nextEvent())
        assertEquals(BackupUiState.Idle, vm.backupState.value)
        assertEquals(emptyList<String>(), folders())
    }

    @Test
    fun cancellingWhileBuildingKeepsNothing() {
        val inside = CountDownLatch(1)
        val release = CountDownLatch(1)
        duringRead = {
            inside.countDown()
            release.await()
        }
        val vm = viewModel()
        vm.startBackup()
        inside.await()
        main.submit { vm.cancelBackup() }.get()
        release.countDown()
        assertEquals(SettingsEvent.BackupCancelled, vm.nextEvent())
        assertEquals(BackupUiState.Idle, vm.backupState.value)
        runBlocking { withTimeout(10_000) { while (folders().isNotEmpty()) kotlinx.coroutines.delay(20) } }
        assertEquals(emptyList<String>(), folders())
    }

    @Test
    fun aRefusedDatabaseIsReportedAndNothingIsBuilt() {
        refused = true
        val vm = viewModel()
        vm.startBackup()
        assertEquals(BackupUiState.Refused, vm.awaitState { it == BackupUiState.Refused })
        assertEquals(emptyList<String>(), folders())
    }

    @Test
    fun aFailedSaveIsReportedAndThePrivateCopyIsDeleted() {
        val vm = viewModel()
        vm.startBackup()
        vm.nextEvent()
        val failing = object : BackupDestination {
            override fun open(): OutputStream? = null
            override fun reopen(): InputStream? = null
            override fun displayName(): String? = null
        }
        vm.onBackupDestination(failing)
        assertEquals(BackupUiState.Failed(BackupFailure.SAVE), vm.awaitState { it is BackupUiState.Failed })
        assertEquals(emptyList<String>(), folders())
    }

    @Test
    fun aSecondTapWhileABackupIsUnderwayIsIgnored() {
        val release = CountDownLatch(1)
        duringRead = { release.await() }
        val vm = viewModel()
        vm.startBackup()
        vm.startBackup()
        release.countDown()
        assertTrue(vm.nextEvent() is SettingsEvent.ChooseBackupDestination)
        vm.onBackupDestination(null)
        vm.nextEvent()
        assertEquals(emptyList<String>(), folders())
    }
}
