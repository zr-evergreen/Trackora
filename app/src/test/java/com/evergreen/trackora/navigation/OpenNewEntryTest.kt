package com.evergreen.trackora.navigation

import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavOptions
import androidx.navigation.Navigator
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test

/**
 * Tests for [openNewEntry], the add button's action.
 *
 * The button animates out instead of vanishing, so a second tap can land
 * while it is still on screen. That tap must find the form already open.
 */
class OpenNewEntryTest {

    private val todayRoute = "com.evergreen.trackora.navigation.TodayRoute"
    private val formRoute = "com.evergreen.trackora.navigation.AddEditWorkRoute?entryId={entryId}"

    private fun destination(route: String): NavDestination =
        mockk { every { this@mockk.route } returns route }

    @Test
    fun `opens a blank form from Today`() {
        val nav = mockk<NavController>(relaxed = true)
        every { nav.currentDestination } returns destination(todayRoute)

        nav.openNewEntry()

        verify(exactly = 1) { nav.navigate(AddEditWorkRoute(), null as NavOptions?, null as Navigator.Extras?) }
    }

    @Test
    fun `a second tap after the form has opened does not open another`() {
        val nav = mockk<NavController>(relaxed = true)
        // Navigating moves the current destination at once, before the
        // button has finished animating away.
        every { nav.currentDestination } returnsMany listOf(destination(todayRoute), destination(formRoute))

        nav.openNewEntry()
        nav.openNewEntry()

        verify(exactly = 1) { nav.navigate(AddEditWorkRoute(), null as NavOptions?, null as Navigator.Extras?) }
    }

    @Test
    fun `does nothing before the graph has a destination`() {
        val nav = mockk<NavController>(relaxed = true)
        every { nav.currentDestination } returns null

        nav.openNewEntry()

        verify(exactly = 0) { nav.navigate(any<AddEditWorkRoute>(), any<NavOptions>(), any<Navigator.Extras>()) }
    }
}
