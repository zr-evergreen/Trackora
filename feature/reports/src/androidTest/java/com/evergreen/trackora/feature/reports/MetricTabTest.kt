package com.evergreen.trackora.feature.reports

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.evergreen.trackora.ui.theme.TrackoraTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests for the figure tabs over the Reports chart: one is always chosen,
 * choosing the other moves the selection, and a screen reader hears each as
 * a single tab carrying the whole comparison rather than as its pieces.
 */
@RunWith(AndroidJUnit4::class)
class MetricTabTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun showTabs() {
        composeRule.setContent {
            TrackoraTheme(darkTheme = false) {
                var chosen by remember { mutableStateOf(JOBS) }
                Row(modifier = Modifier.selectableGroup()) {
                    MetricTab(
                        label = "Jobs finished",
                        value = 6,
                        change = 5,
                        description = JOBS,
                        selected = chosen == JOBS,
                        onClick = { chosen = JOBS },
                        modifier = Modifier.weight(1f)
                    )
                    MetricTab(
                        label = "Items",
                        value = 14,
                        change = null,
                        description = ITEMS,
                        selected = chosen == ITEMS,
                        onClick = { chosen = ITEMS },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }

    @Test
    fun eachTabIsOneStopCarryingTheWholeComparison() {
        showTabs()

        composeRule.onNodeWithContentDescription(JOBS)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))
            .assertIsSelected()
        // The label, number and movement are not read again as separate stops.
        composeRule.onAllNodesWithText("Jobs finished").assertCountEquals(0)
        composeRule.onAllNodesWithText("6").assertCountEquals(0)
    }

    @Test
    fun choosingTheOtherTabMovesTheSelection() {
        showTabs()

        composeRule.onNodeWithContentDescription(ITEMS).assertIsNotSelected().performClick()

        composeRule.onNodeWithContentDescription(ITEMS).assertIsSelected()
        composeRule.onNodeWithContentDescription(JOBS).assertIsNotSelected()
    }

    private companion object {
        const val JOBS = "Jobs finished, 6. 5 more than the 7 days before"
        const val ITEMS = "Items, 14"
    }
}
