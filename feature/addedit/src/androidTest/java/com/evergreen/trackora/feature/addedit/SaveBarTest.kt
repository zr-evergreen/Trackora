package com.evergreen.trackora.feature.addedit

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.evergreen.trackora.ui.theme.TrackoraTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests for [SaveBar], the entry form's pinned bottom bar.
 *
 * Save can be tapped from anywhere in the form, so a failed save has to be
 * reported in the bar itself; drawn at the end of the scrolling form it was
 * off screen and the tap looked as if it had done nothing.
 */
@RunWith(AndroidJUnit4::class)
class SaveBarTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val save = InstrumentationRegistry.getInstrumentation().targetContext
        .getString(R.string.action_save)

    @Test
    fun aSaveErrorIsShownInTheBarAboveSave() {
        composeRule.setContent {
            TrackoraTheme(darkTheme = false) {
                SaveBar(enabled = true, isSaving = false, onSave = {}, errorMessage = ERROR)
            }
        }

        val error = composeRule.onNodeWithText(ERROR).assertIsDisplayed().getBoundsInRoot()
        val button = composeRule.onNodeWithText(save).assertIsDisplayed().getBoundsInRoot()

        assertTrue("error ($error) should sit above Save ($button)", error.bottom <= button.top)
    }

    @Test
    fun aSaveErrorIsAnnouncedWhenItAppears() {
        var message by mutableStateOf<String?>(null)
        composeRule.setContent {
            TrackoraTheme(darkTheme = false) {
                SaveBar(enabled = true, isSaving = false, onSave = {}, errorMessage = message)
            }
        }
        composeRule.onNodeWithText(ERROR).assertDoesNotExist()

        message = ERROR

        composeRule.onNodeWithText(ERROR)
            .assertIsDisplayed()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
    }

    private companion object {
        const val ERROR = "Could not save the entry"
    }
}
