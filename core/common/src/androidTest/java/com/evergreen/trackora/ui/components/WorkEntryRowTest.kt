package com.evergreen.trackora.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.evergreen.trackora.common.R
import com.evergreen.trackora.domain.model.Status
import com.evergreen.trackora.domain.model.WorkEntry
import com.evergreen.trackora.ui.theme.TrackoraTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/**
 * Tests for [WorkEntryRow], the card every work list is drawn with.
 *
 * The card used to state its status twice — a badge with the current status
 * and an action button labelled with the next one — and to align each title
 * by its own script, so mixed lists had no common edge.
 */
@RunWith(AndroidJUnit4::class)
class WorkEntryRowTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val inProgress = context.getString(R.string.status_in_progress)
    private val completed = context.getString(R.string.status_completed)
    private val delivered = context.getString(R.string.status_delivered)
    private val markDone = context.getString(R.string.action_mark_complete)
    private val markDelivered = context.getString(R.string.action_mark_delivered)

    @Test
    fun theStatusIsStatedOnceAndTheActionSaysWhatItDoes() {
        setRow(entry(Status.IN_PROGRESS), onAdvance = {})

        composeRule.onAllNodesWithText(inProgress, useUnmergedTree = true).assertCountEquals(1)
        // The old action was labelled with the next status's name.
        composeRule.onAllNodesWithText(completed, useUnmergedTree = true).assertCountEquals(0)
        composeRule.onNodeWithText(markDone, useUnmergedTree = true).assertIsDisplayed()
        listOf(inProgress, completed, delivered).forEach { status ->
            assertNotEquals(status, markDone)
            assertNotEquals(status, markDelivered)
        }
    }

    @Test
    fun theActionIsAnnouncedWithItsEntryAndAdvancesIt() {
        var advanced = 0
        setRow(entry(Status.COMPLETED), onAdvance = { advanced++ })

        composeRule
            .onNodeWithContentDescription(context.getString(R.string.action_for_entry, markDelivered, TITLE))
            .assertHasClickAction()
            .performClick()

        assertEquals(1, advanced)
    }

    @Test
    fun deliveredWorkOffersNoAction() {
        setRow(entry(Status.DELIVERED), onAdvance = {})

        composeRule.onNodeWithText(delivered, useUnmergedTree = true).assertIsDisplayed()
        composeRule.onAllNodesWithText(markDone, useUnmergedTree = true).assertCountEquals(0)
        composeRule.onAllNodesWithText(markDelivered, useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun cardsAreTheSameHeightWithAndWithoutAnAction() {
        composeRule.setContent {
            TrackoraTheme(darkTheme = false) {
                Column {
                    WorkEntryRow(
                        entry = entry(Status.IN_PROGRESS),
                        modifier = Modifier.testTag("withAction"),
                        onAdvanceStatus = {}
                    )
                    WorkEntryRow(
                        entry = entry(Status.DELIVERED),
                        modifier = Modifier.testTag("withoutAction"),
                        onAdvanceStatus = {}
                    )
                }
            }
        }

        val withAction = composeRule.onNodeWithTag("withAction").getBoundsInRoot()
        val withoutAction = composeRule.onNodeWithTag("withoutAction").getBoundsInRoot()

        assertEquals(
            (withoutAction.bottom - withoutAction.top).value,
            (withAction.bottom - withAction.top).value,
            0.5f
        )
    }

    @Test
    fun aLatinTitleLinesUpWithTheStartOfAPersianLayout() {
        setRow(entry(Status.DELIVERED), onAdvance = null, direction = LayoutDirection.Rtl)

        val line = titleLayout()

        // Against the right edge, where the Persian titles around it start.
        assertEquals(line.size.width.toFloat(), line.getLineRight(0), 1f)
        assertTrue(line.getLineLeft(0) > 0f)
    }

    @Test
    fun aPersianTitleLinesUpWithTheStartOfAnEnglishLayout() {
        setRow(entry(Status.DELIVERED, title = PERSIAN_TITLE), onAdvance = null, direction = LayoutDirection.Ltr)

        val line = titleLayout(PERSIAN_TITLE)

        assertEquals(0f, line.getLineLeft(0), 1f)
        assertTrue(line.getLineRight(0) < line.size.width)
    }

    private fun setRow(
        entry: WorkEntry,
        onAdvance: (() -> Unit)?,
        direction: LayoutDirection = LayoutDirection.Ltr,
    ) {
        composeRule.setContent {
            TrackoraTheme(darkTheme = false) {
                CompositionLocalProvider(LocalLayoutDirection provides direction) {
                    WorkEntryRow(entry = entry, onClick = {}, onAdvanceStatus = onAdvance)
                }
            }
        }
    }

    private fun titleLayout(title: String = TITLE): TextLayoutResult {
        val node = composeRule.onNodeWithText(title, useUnmergedTree = true).fetchSemanticsNode()
        val results = mutableListOf<TextLayoutResult>()
        node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
        return results.single()
    }

    private fun entry(status: Status, title: String = TITLE) = WorkEntry(
        id = 1,
        title = title,
        quantity = 3,
        status = status,
        date = LocalDate.of(2026, 10, 27)
    )

    private companion object {
        const val TITLE = "Hem trousers"
        const val PERSIAN_TITLE = "کت‌شلوار"
    }
}
