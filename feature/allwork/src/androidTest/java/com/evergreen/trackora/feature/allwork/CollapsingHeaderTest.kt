package com.evergreen.trackora.feature.allwork

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.evergreen.trackora.ui.theme.TrackoraTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests for All Work's collapsing header and the list it gives way to.
 *
 * Both failures this guards against were invisible until a finger was on
 * the list: the header's collapse limit was never set, so it either swallowed
 * every scroll and pinned the list to its first card, or never moved at all.
 */
@RunWith(AndroidJUnit4::class)
class CollapsingHeaderTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var collapse: HeaderCollapse
    private lateinit var list: LazyListState

    private fun showList() {
        composeRule.setContent {
            TrackoraTheme(darkTheme = false) {
                collapse = remember { HeaderCollapse() }
                list = rememberLazyListState()
                Column(modifier = Modifier.fillMaxSize().nestedScroll(collapse.connection)) {
                    CollapsingHeader(state = collapse) {
                        Text(text = HEADER, modifier = Modifier.height(120.dp))
                    }
                    LazyColumn(state = list, modifier = Modifier.fillMaxSize().testTag(LIST)) {
                        items(count = 60) { row -> Text(text = "Row $row", modifier = Modifier.height(56.dp)) }
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun theHeaderKnowsHowFarItCanGiveWay() {
        showList()

        assertTrue("limit was ${collapse.limit}", collapse.limit > 0f)
    }

    @Test
    fun aSwipeUpHidesTheHeaderAndStillScrollsTheList() {
        showList()

        composeRule.onNodeWithTag(LIST).performTouchInput { swipeUp() }
        composeRule.waitForIdle()

        assertEquals(1f, collapse.fraction, 0.01f)
        assertTrue("the list did not scroll", list.firstVisibleItemIndex > 0)
        composeRule.onNodeWithText(HEADER).assertIsNotDisplayed()
    }

    @Test
    fun theHeaderReturnsOnlyOnceTheListIsBackAtItsTop() {
        showList()
        composeRule.onNodeWithTag(LIST).performTouchInput {
            swipeUp()
            swipeUp()
        }
        composeRule.waitForIdle()

        // A short pull back while deep in the list scrolls the list, not the header.
        composeRule.onNodeWithTag(LIST).performTouchInput {
            swipeDown(startY = centerY, endY = centerY + 150f)
        }
        composeRule.waitForIdle()
        assertEquals(1f, collapse.fraction, 0.01f)

        composeRule.onNodeWithTag(LIST).performTouchInput { repeat(8) { swipeDown() } }
        composeRule.waitForIdle()
        assertEquals(0, list.firstVisibleItemIndex)
        assertEquals(0f, collapse.fraction, 0.01f)
        composeRule.onNodeWithText(HEADER).assertIsDisplayed()
    }

    private companion object {
        const val HEADER = "Header"
        const val LIST = "list"
    }
}
