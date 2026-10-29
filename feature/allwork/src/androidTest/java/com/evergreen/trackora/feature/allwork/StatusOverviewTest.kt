package com.evergreen.trackora.feature.allwork

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.evergreen.trackora.domain.model.Status
import com.evergreen.trackora.ui.theme.TrackoraTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests for All Work's status overview: every filter in view and whole on a
 * narrow phone at large text, each one stop for a screen reader that names
 * its count, and the chosen one the one that is selected.
 */
@RunWith(AndroidJUnit4::class)
class StatusOverviewTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val resources = InstrumentationRegistry.getInstrumentation().targetContext.resources

    /** The filter the overview last asked for. */
    private var chosen: Status? = null

    private fun showOverview(
        selected: Status? = null,
        direction: LayoutDirection = LayoutDirection.Ltr,
        fontScale: Float = 1f,
    ) {
        composeRule.setContent {
            val density = LocalDensity.current
            TrackoraTheme(darkTheme = false) {
                CompositionLocalProvider(
                    LocalLayoutDirection provides direction,
                    LocalDensity provides Density(density.density, fontScale)
                ) {
                    var current by remember { mutableStateOf(selected) }
                    Box(modifier = Modifier.width(WIDTH)) {
                        StatusOverview(
                            selected = current,
                            allCount = 10,
                            counts = mapOf(Status.IN_PROGRESS to 3, Status.COMPLETED to 5, Status.DELIVERED to 2),
                            onSelect = {
                                current = it
                                chosen = it
                            }
                        )
                    }
                }
            }
        }
    }

    private fun cell(@StringRes label: Int, count: Int) = composeRule.onNodeWithContentDescription(
        resources.getString(
            R.string.all_work_filter_description,
            resources.getString(label),
            resources.getQuantityString(R.plurals.all_work_count, count, count)
        )
    )

    private val all get() = cell(R.string.filter_all, 10)
    private val inProgress get() = cell(R.string.filter_in_progress, 3)
    private val completed get() = cell(R.string.filter_completed, 5)
    private val delivered get() = cell(R.string.filter_delivered, 2)

    @Test
    fun everyFilterIsWhollyInViewOnANarrowPhoneAtLargeText() {
        showOverview(fontScale = 1.3f)

        composeRule.onAllNodes(isSelectable()).assertCountEquals(4)
        listOf(all, inProgress, completed, delivered).forEach { node ->
            val bounds = node.getUnclippedBoundsInRoot()
            assertTrue("starts off screen: $bounds", bounds.left >= 0.dp)
            assertTrue("runs off screen: $bounds", bounds.right <= WIDTH)
            node.assertHeightIsAtLeast(48.dp)
        }
    }

    @Test
    fun theChosenFilterIsTheOneSelected() {
        showOverview(selected = Status.COMPLETED)

        completed.assertIsSelected()
        all.assertIsNotSelected()
        inProgress.assertIsNotSelected()
        delivered.assertIsNotSelected()
    }

    @Test
    fun tappingAFilterChoosesIt() {
        showOverview()

        inProgress.performClick()
        assertEquals(Status.IN_PROGRESS, chosen)
        inProgress.assertIsSelected()
        all.assertIsNotSelected()

        all.performClick()
        assertNull(chosen)
        all.assertIsSelected()
    }

    @Test
    fun inPersianEverythingIsAtTheRightAndDeliveredAtTheLeft() {
        showOverview(direction = LayoutDirection.Rtl)

        val allLeft = all.getUnclippedBoundsInRoot().left
        val deliveredLeft = delivered.getUnclippedBoundsInRoot().left
        assertTrue("All at $allLeft, Delivered at $deliveredLeft", allLeft > deliveredLeft)
    }

    private companion object {
        val WIDTH = 320.dp
    }
}
