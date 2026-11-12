package com.evergreen.trackora.ui.recovery

import android.content.res.Configuration
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.evergreen.trackora.common.R
import com.evergreen.trackora.ui.theme.TrackoraTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.util.Locale

/** The recovery screen in each of its states, and its Persian text. */
@RunWith(AndroidJUnit4::class)
class RecoveryScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private fun text(id: Int) = context.getString(id)

    private var checks = 0
    private var restarts = 0
    private var state by mutableStateOf<RecoveryState>(RecoveryState.Paused)

    private fun show(recordedOn: LocalDate? = null) {
        composeRule.setContent {
            TrackoraTheme {
                RecoveryScreen(
                    state = state,
                    recordedOn = recordedOn,
                    onCheckAgain = { checks++ },
                    onRestart = { restarts++ },
                )
            }
        }
    }

    private fun assertShown(id: Int) = composeRule.onNodeWithText(text(id)).performScrollTo().assertIsDisplayed()
    private fun assertNotShown(id: Int) = composeRule.onNodeWithText(text(id)).assertDoesNotExist()

    @Test
    fun atFirstItSaysWhatHappenedAndOffersACheck() {
        show()

        composeRule.onNodeWithTag(RecoveryScreenTag).assertIsDisplayed()
        assertShown(R.string.recovery_title)
        assertShown(R.string.recovery_body_paused)
        assertShown(R.string.recovery_body_kept)
        assertShown(R.string.recovery_check_limits)
        composeRule.onNodeWithTag(RecoveryRestartButtonTag).assertDoesNotExist()

        composeRule.onNodeWithTag(RecoveryCheckButtonTag).performScrollTo().assertIsEnabled().performClick()
        assertEquals(1, checks)
        assertEquals(0, restarts)
    }

    @Test
    fun theDayTheDamageWasRecordedIsShownWhenKnown() {
        show(recordedOn = LocalDate.of(2026, 11, 3))
        composeRule.onNodeWithText(text(R.string.recovery_recorded_on).substringBefore("%1\$s").trim(), substring = true)
            .assertIsDisplayed()
    }

    @Test
    fun theButtonCannotBePressedAgainWhileChecking() {
        state = RecoveryState.Checking
        show()

        composeRule.onNodeWithTag(RecoveryCheckButtonTag).performScrollTo().assertIsNotEnabled()
        assertShown(R.string.recovery_checking)
        assertEquals(0, checks)
    }

    @Test
    fun eachFailureSaysWhyAndTheCheckCanBeTriedAgain() {
        show()
        mapOf(
            RecoveryState.Why.STILL_DAMAGED to R.string.recovery_failed_damaged,
            RecoveryState.Why.NOT_ENOUGH_SPACE to R.string.recovery_failed_space,
            RecoveryState.Why.FILES_CHANGED to R.string.recovery_failed_changed,
            RecoveryState.Why.NOT_COMPLETED to R.string.recovery_failed_other,
        ).forEach { (why, message) ->
            state = RecoveryState.NotPassed(why)
            composeRule.waitForIdle()
            assertShown(message)
            composeRule.onNodeWithTag(RecoveryRestartButtonTag).assertDoesNotExist()
            composeRule.onNodeWithTag(RecoveryCheckButtonTag).performScrollTo().assertIsEnabled()
        }
    }

    @Test
    fun afterAPassingCheckOnlyARestartIsOffered() {
        state = RecoveryState.Passed
        show()

        assertShown(R.string.recovery_passed_title)
        assertShown(R.string.recovery_passed_body)
        assertShown(R.string.recovery_restart_fallback)
        composeRule.onNodeWithTag(RecoveryCheckButtonTag).assertDoesNotExist()
        assertNotShown(R.string.recovery_check_again)

        composeRule.onNodeWithTag(RecoveryRestartButtonTag).performScrollTo().performClick()
        assertEquals(1, restarts)
        assertEquals(0, checks)
    }

    @Test
    fun aRestartThatCouldNotBeAskedForSaysHowToReopenTheApp() {
        state = RecoveryState.RestartNotStarted
        show()

        assertShown(R.string.recovery_passed_title)
        assertShown(R.string.recovery_restart_not_started)
        composeRule.onNodeWithTag(RecoveryRestartNotStartedTag).assertExists()
        // Nothing here claims the app restarted.
        assertNotShown(R.string.recovery_passed_body)
        composeRule.onNodeWithTag(RecoveryCheckButtonTag).assertDoesNotExist()
        // The restart can still be tried again.
        composeRule.onNodeWithTag(RecoveryRestartButtonTag).performScrollTo().performClick()
        assertEquals(1, restarts)
        assertEquals(0, checks)
    }

    @Test
    fun everyStringHasItsOwnPersianText() {
        val persian = context.createConfigurationContext(Configuration(context.resources.configuration).apply { setLocale(Locale("fa")) })
        val english = context.createConfigurationContext(Configuration(context.resources.configuration).apply { setLocale(Locale.ENGLISH) })
        val ids = listOf(
            R.string.recovery_title, R.string.recovery_body_paused, R.string.recovery_body_kept, R.string.recovery_recorded_on,
            R.string.recovery_check_again, R.string.recovery_checking, R.string.recovery_check_limits,
            R.string.recovery_passed_title, R.string.recovery_passed_body, R.string.recovery_restart, R.string.recovery_restart_fallback, R.string.recovery_restart_not_started,
            R.string.recovery_failed_damaged, R.string.recovery_failed_space, R.string.recovery_failed_changed, R.string.recovery_failed_other,
        )
        ids.forEach { id ->
            val name = context.resources.getResourceEntryName(id)
            assertNotEquals(name, english.getString(id), persian.getString(id))
        }
    }
}
