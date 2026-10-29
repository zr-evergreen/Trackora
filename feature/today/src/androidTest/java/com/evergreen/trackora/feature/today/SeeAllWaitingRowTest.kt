package com.evergreen.trackora.feature.today

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.evergreen.trackora.ui.theme.TrackoraTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests for [SeeAllWaitingRow]. It replaced a text button, and a screen reader
 * has to keep announcing it as one.
 */
@RunWith(AndroidJUnit4::class)
class SeeAllWaitingRowTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val label = InstrumentationRegistry.getInstrumentation().targetContext
        .getString(R.string.today_see_all_undelivered, 5)

    @Test
    fun isAnnouncedAsAButtonAndOpensTheList() {
        var opened = 0
        composeRule.setContent {
            TrackoraTheme(darkTheme = false) {
                SeeAllWaitingRow(count = 5, onClick = { opened++ }, shape = RoundedCornerShape(16.dp))
            }
        }

        composeRule.onNodeWithText(label)
            .assertHasClickAction()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .performClick()

        assertEquals(1, opened)
    }
}
