package com.evergreen.trackora.navigation

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusTarget
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.toRoute
import com.evergreen.trackora.R
import com.evergreen.trackora.domain.model.Status
import com.evergreen.trackora.feature.addedit.AddEditWorkScreen
import com.evergreen.trackora.feature.allwork.allWorkNavigation
import com.evergreen.trackora.feature.reports.reportsNavigation
import com.evergreen.trackora.feature.today.todayNavigation
import com.evergreen.trackora.ui.settings.SettingsScreen
import com.evergreen.trackora.ui.theme.TrackoraIcons
import com.evergreen.trackora.ui.theme.TrackoraMotion

/**
 * Main navigation graph for the app with Bottom Navigation.
 *
 * ### Motion
 *
 * Moving between tabs is a fade-through: the old screen fades out quickly and
 * the new one fades in with a slight lift, because tabs are peers and nothing
 * spatial connects them. The entry form rises from below and sinks back,
 * because it is a step into one entry and back out again. Both stay inside
 * Material's medium duration band so navigation never feels slower than the
 * tap that caused it.
 */
@Composable
fun NavGraph(
    navController: NavHostController,
    startDestination: Any = TodayRoute,
    onSettingsClick: () -> Unit = {}
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    // Show bottom nav on primary tabs, FAB only on Today. Hide both on Add/Edit.
    val currentRoute = currentDestination?.route.orEmpty()
    val isAddEdit = currentRoute.contains("AddEditWorkRoute")
    val showBottomNav = currentRoute.isEmpty() || (!isAddEdit)
    val showFab = currentRoute.contains("TodayRoute")

    // Today's snackbars go through the Scaffold rather than a host inside the
    // screen, so the Scaffold places them above the add button instead of
    // under it, where the button covered Undo.
    val todaySnackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        // Android 8.1 and below give a window initial focus even in touch
        // mode, and Compose passes it to the first focusable node — on Today,
        // the quick-add field. When a focused node later leaves with its
        // screen, focus is reassigned the same way and the next screen's first
        // text field opens the keyboard unasked. A focus target around
        // everything is the first focusable node instead, so stray focus
        // lands here. It carries no semantics, so screen readers ignore it.
        modifier = Modifier.focusTarget(),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(hostState = todaySnackbarHostState) },
        bottomBar = {
            if (showBottomNav) {
                BottomNavigationBar(navController = navController, currentDestination = currentDestination)
            }
        },
        floatingActionButton = {
            // Scales in on Today and out elsewhere instead of blinking, so it
            // reads as belonging to that screen.
            AnimatedVisibility(
                visible = showFab,
                enter = scaleIn(tween(TrackoraMotion.MEDIUM, easing = TrackoraMotion.EmphasizedDecelerate)) +
                    fadeIn(tween(TrackoraMotion.SHORT)),
                exit = scaleOut(tween(TrackoraMotion.SHORT, easing = TrackoraMotion.EmphasizedAccelerate)) +
                    fadeOut(tween(TrackoraMotion.SHORT))
            ) {
                FloatingActionButton(
                    onClick = { navController.openNewEntry() },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ) {
                    Icon(
                        imageVector = TrackoraIcons.Add,
                        contentDescription = stringResource(id = R.string.content_add_work_entry)
                    )
                }
            }
        }
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            enterTransition = { fadeThroughIn() },
            exitTransition = { fadeThroughOut() },
            popEnterTransition = { fadeThroughIn() },
            popExitTransition = { fadeThroughOut() }
        ) {
            // Feature navigation graphs
            todayNavigation(
                contentPadding = paddingValues,
                snackbarHostState = todaySnackbarHostState,
                // The band shows only the oldest few; the rest live in the
                // history, where the Completed filter and search already exist.
                onSeeAllUndelivered = {
                    // Once All Work has been visited from the bottom bar its
                    // state is saved, and restoring it brings back the
                    // arguments it was saved with — dropping the filter passed
                    // here. Discard that saved state and open it fresh instead.
                    navController.clearBackStack<AllWorkRoute>()
                    navController.navigate(AllWorkRoute(initialStatus = Status.COMPLETED.name)) {
                        launchSingleTop = true
                    }
                },
                // Rows on Today are now tappable. Previously the only way to
                // open an entry was via All Work, which users had no reason to
                // discover.
                onEntryClick = { entryId ->
                    navController.navigate(AddEditWorkRoute(entryId = entryId))
                }
            )

            allWorkNavigation(
                contentPadding = paddingValues,
                onEntryClick = { entryId ->
                    navController.navigate(AddEditWorkRoute(entryId = entryId))
                }
            )

            reportsNavigation(contentPadding = paddingValues)

            composable<SettingsRoute> {
                SettingsScreen(
                    contentPadding = paddingValues
                )
            }

            // Add/Edit Work: rises over the list it came from and sinks back.
            composable<AddEditWorkRoute>(
                enterTransition = { riseIn() },
                exitTransition = { fadeThroughOut() },
                popEnterTransition = { fadeThroughIn() },
                popExitTransition = { sinkOut() }
            ) { backStackEntry ->
                val route = backStackEntry.toRoute<AddEditWorkRoute>()
                AddEditWorkScreen(
                    entryId = route.entryId,
                    onNavigateBack = {
                        navController.popBackStack()
                    }
                )
            }
        }
    }
}

/**
 * Opens a blank entry form, but only from Today.
 *
 * The add button animates out rather than vanishing, so it is still tappable
 * for a moment after the first tap has navigated, and a quick double tap
 * stacked a second empty form on the first. Navigating moves the current
 * destination at once, so the second tap now finds the form already open and
 * does nothing.
 */
internal fun NavController.openNewEntry() {
    if (currentDestination?.route?.contains("TodayRoute") != true) return
    navigate(AddEditWorkRoute())
}

private fun AnimatedContentTransitionScope<NavBackStackEntry>.fadeThroughIn(): EnterTransition =
    fadeIn(tween(TrackoraMotion.MEDIUM, delayMillis = 60, easing = TrackoraMotion.EmphasizedDecelerate)) +
        scaleIn(tween(TrackoraMotion.MEDIUM, delayMillis = 60, easing = TrackoraMotion.EmphasizedDecelerate), initialScale = 0.97f)

private fun AnimatedContentTransitionScope<NavBackStackEntry>.fadeThroughOut(): ExitTransition =
    fadeOut(tween(90, easing = TrackoraMotion.EmphasizedAccelerate))

private fun AnimatedContentTransitionScope<NavBackStackEntry>.riseIn(): EnterTransition =
    slideInVertically(tween(TrackoraMotion.LONG, easing = TrackoraMotion.EmphasizedDecelerate)) { it / 10 } +
        fadeIn(tween(TrackoraMotion.MEDIUM))

private fun AnimatedContentTransitionScope<NavBackStackEntry>.sinkOut(): ExitTransition =
    slideOutVertically(tween(TrackoraMotion.MEDIUM, easing = TrackoraMotion.EmphasizedAccelerate)) { it / 10 } +
        fadeOut(tween(TrackoraMotion.SHORT))

/** A bottom-navigation destination. */
private data class TopLevelTab(
    val route: Any,
    /** Matched against the destination's route, which embeds the class name. */
    val routeName: String,
    @StringRes val label: Int,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
)

private val TopLevelTabs = listOf(
    TopLevelTab(TodayRoute, "TodayRoute", R.string.nav_today, TrackoraIcons.TodayOutlined, TrackoraIcons.Today),
    TopLevelTab(AllWorkRoute(), "AllWorkRoute", R.string.nav_all_work, TrackoraIcons.HistoryOutlined, TrackoraIcons.History),
    TopLevelTab(ReportsRoute, "ReportsRoute", R.string.nav_reports, TrackoraIcons.ReportsOutlined, TrackoraIcons.Reports),
    TopLevelTab(SettingsRoute, "SettingsRoute", R.string.nav_settings, TrackoraIcons.SettingsOutlined, TrackoraIcons.Settings),
)

@Composable
private fun BottomNavigationBar(
    navController: NavHostController,
    currentDestination: NavDestination?,
) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 0.dp
    ) {
        TopLevelTabs.forEach { tab ->
            val selected = currentDestination?.hierarchy?.any {
                it.route?.contains(tab.routeName) == true
            } == true
            val label = stringResource(id = tab.label)

            NavigationBarItem(
                selected = selected,
                onClick = {
                    navController.navigate(tab.route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                // Filled when selected, outlined when not: the Material
                // convention, and a second cue besides the indicator pill.
                icon = {
                    Icon(
                        imageVector = if (selected) tab.selectedIcon else tab.icon,
                        contentDescription = null
                    )
                },
                label = {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    selectedTextColor = MaterialTheme.colorScheme.onSurface,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
    }
}
