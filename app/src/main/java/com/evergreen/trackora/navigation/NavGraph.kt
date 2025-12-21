package com.evergreen.trackora.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.toRoute
import com.evergreen.trackora.feature.addedit.AddEditWorkScreen
import com.evergreen.trackora.feature.allwork.allWorkNavigation
import com.evergreen.trackora.feature.reports.reportsNavigation
import com.evergreen.trackora.feature.today.todayNavigation
import com.evergreen.trackora.ui.settings.SettingsScreen
import com.evergreen.trackora.R

/**
 * Main navigation graph for the app with Bottom Navigation.
 */
@OptIn(ExperimentalMaterial3Api::class)
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
    
    androidx.compose.material3.Scaffold(
        bottomBar = {
            if (showBottomNav) {
                BottomNavigationBar(navController = navController)
            }
        },
        floatingActionButton = {
            if (showFab) {
                FloatingActionButton(
                    onClick = { navController.navigate(AddEditWorkRoute()) },
                    containerColor = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                    contentColor = androidx.compose.material3.MaterialTheme.colorScheme.onPrimary
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = stringResource(id = R.string.content_add_work_entry)
                    )
                }
            }
        }
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = startDestination
        ) {
            // Feature navigation graphs
            todayNavigation(
                contentPadding = paddingValues
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
            
            // Add/Edit Work (modal screen)
            composable<AddEditWorkRoute> { backStackEntry ->
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

@Composable
private fun BottomNavigationBar(
    navController: NavHostController
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    
    NavigationBar(
        containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp
    ) {
        NavigationBarItem(
            icon = { 
                Icon(
                    painterResource(id = R.drawable.ic_today), 
                    contentDescription = stringResource(id = R.string.nav_today)
                ) 
            },
            label = { 
                Text(
                    text = stringResource(id = R.string.nav_today),
                    style = androidx.compose.material3.MaterialTheme.typography.labelMedium
                ) 
            },
            selected = currentDestination?.hierarchy?.any { 
                it.route?.contains("TodayRoute") == true 
            } == true,
            onClick = {
                navController.navigate(TodayRoute) {
                    popUpTo(navController.graph.findStartDestination().id) {
                        saveState = true
                    }
                    launchSingleTop = true
                    restoreState = true
                }
            },
            colors = androidx.compose.material3.NavigationBarItemDefaults.colors(
                selectedIconColor = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                selectedTextColor = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                indicatorColor = androidx.compose.material3.MaterialTheme.colorScheme.primaryContainer,
                unselectedIconColor = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                unselectedTextColor = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
            )
        )
        NavigationBarItem(
            icon = { 
                Icon(
                    painterResource(id = R.drawable.ic_all_works), 
                    contentDescription = stringResource(id = R.string.nav_all_work)
                ) 
            },
            label = { 
                Text(
                    text = stringResource(id = R.string.nav_all_work),
                    style = androidx.compose.material3.MaterialTheme.typography.labelMedium
                ) 
            },
            selected = currentDestination?.hierarchy?.any { 
                it.route?.contains("AllWorkRoute") == true 
            } == true,
            onClick = {
                navController.navigate(AllWorkRoute) {
                    popUpTo(navController.graph.findStartDestination().id) {
                        saveState = true
                    }
                    launchSingleTop = true
                    restoreState = true
                }
            },
            colors = androidx.compose.material3.NavigationBarItemDefaults.colors(
                selectedIconColor = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                selectedTextColor = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                indicatorColor = androidx.compose.material3.MaterialTheme.colorScheme.primaryContainer,
                unselectedIconColor = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                unselectedTextColor = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
            )
        )
        NavigationBarItem(
            icon = { 
                Icon(
                    painterResource(id = R.drawable.ic_reports), 
                    contentDescription = stringResource(id = R.string.nav_reports)
                ) 
            },
            label = { 
                Text(
                    text = stringResource(id = R.string.nav_reports),
                    style = androidx.compose.material3.MaterialTheme.typography.labelMedium
                ) 
            },
            selected = currentDestination?.hierarchy?.any { 
                it.route?.contains("ReportsRoute") == true 
            } == true,
            onClick = {
                navController.navigate(ReportsRoute) {
                    popUpTo(navController.graph.findStartDestination().id) {
                        saveState = true
                    }
                    launchSingleTop = true
                    restoreState = true
                }
            },
            colors = androidx.compose.material3.NavigationBarItemDefaults.colors(
                selectedIconColor = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                selectedTextColor = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                indicatorColor = androidx.compose.material3.MaterialTheme.colorScheme.primaryContainer,
                unselectedIconColor = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                unselectedTextColor = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
            )
        )
        NavigationBarItem(
            icon = { 
                Icon(
                    painterResource(id = R.drawable.ic_settings), 
                    contentDescription = stringResource(id = R.string.nav_settings)
                ) 
            },
            label = { 
                Text(
                    text = stringResource(id = R.string.nav_settings),
                    style = androidx.compose.material3.MaterialTheme.typography.labelMedium
                ) 
            },
            selected = currentDestination?.hierarchy?.any { 
                it.route?.contains("SettingsRoute") == true 
            } == true,
            onClick = {
                navController.navigate(SettingsRoute) {
                    popUpTo(navController.graph.findStartDestination().id) {
                        saveState = true
                    }
                    launchSingleTop = true
                    restoreState = true
                }
            },
            colors = androidx.compose.material3.NavigationBarItemDefaults.colors(
                selectedIconColor = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                selectedTextColor = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                indicatorColor = androidx.compose.material3.MaterialTheme.colorScheme.primaryContainer,
                unselectedIconColor = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                unselectedTextColor = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
            )
        )
    }
}

