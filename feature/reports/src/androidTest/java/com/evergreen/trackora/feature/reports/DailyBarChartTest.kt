package com.evergreen.trackora.feature.reports

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.evergreen.trackora.ui.theme.TrackoraTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests for the Reports bar chart: what a screen reader hears, and which day
 * a tap lands on — including in Persian, where time runs right to left and
 * the oldest day sits on the right.
 */
@RunWith(AndroidJUnit4::class)
class DailyBarChartTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var selected: Int? = null

    private fun showChart(direction: LayoutDirection = LayoutDirection.Ltr) {
        composeRule.setContent {
            TrackoraTheme(darkTheme = false) {
                CompositionLocalProvider(LocalLayoutDirection provides direction) {
                    var current by remember { mutableStateOf<Int?>(null) }
                    DailyBarChart(
                        values = listOf(1, 0, 3, 2),
                        labels = listOf("A", "B", "C", "D"),
                        emphasisIndex = 3,
                        selectedIndex = current,
                        onSelect = {
                            current = it
                            selected = it
                        },
                        average = 1.5f,
                        showValues = true,
                        description = DESCRIPTION
                    )
                }
            }
        }
    }

    @Test
    fun theChartIsDescribedInsteadOfDrawnForScreenReaders() {
        showChart()

        composeRule.onNodeWithContentDescription(DESCRIPTION).assertExists()
    }

    @Test
    fun aTapSelectsTheDayUnderIt() {
        showChart()

        composeRule.onNodeWithContentDescription(DESCRIPTION).performTouchInput {
            click(Offset(width * 0.125f, centerY))
        }

        assertEquals(0, selected)
    }

    @Test
    fun inRightToLeftTheOldestDayIsOnTheRight() {
        showChart(LayoutDirection.Rtl)

        composeRule.onNodeWithContentDescription(DESCRIPTION).performTouchInput {
            click(Offset(width * 0.125f, centerY))
        }
        assertEquals(3, selected)

        composeRule.onNodeWithContentDescription(DESCRIPTION).performTouchInput {
            click(Offset(width * 0.875f, centerY))
        }
        assertEquals(0, selected)
    }

    @Test
    fun tappingTheSelectedDayAgainClearsTheSelection() {
        showChart()

        composeRule.onNodeWithContentDescription(DESCRIPTION).performTouchInput {
            click(Offset(width * 0.625f, centerY))
        }
        assertEquals(2, selected)
        composeRule.onNodeWithContentDescription(DESCRIPTION).performTouchInput {
            click(Offset(width * 0.625f, centerY))
        }

        assertNull(selected)
    }

    private companion object {
        const val DESCRIPTION = "Finished, day by day. Total 6 jobs."
    }
}
