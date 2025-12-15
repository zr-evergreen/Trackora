package com.evergreen.trackora.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Today
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
    
    // Show bottom nav only on main screens (not on AddEditWork)
    val showBottomNav = currentDestination?.route?.let { route ->
        route != AddEditWorkRoute().toString()
    } ?: true
    
    androidx.compose.material3.Scaffold(
        topBar = {
            if (showBottomNav) {
                TopAppBar(
                    title = { Text("Trackora") },
                    actions = {
                        IconButton(onClick = onSettingsClick) {
                            Icon(
                                imageVector = Icons.Default.Menu,
                                contentDescription = "Settings"
                            )
                        }
                    }
                )
            }
        },
        bottomBar = {
            if (showBottomNav) {
                BottomNavigationBar(navController = navController)
            }
        }
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(paddingValues)
        ) {
            // Feature navigation graphs
            todayNavigation(
                onAddEntryClick = {
                    navController.navigate(AddEditWorkRoute())
                }
            )
            
            allWorkNavigation()
            
            reportsNavigation()
            
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
    
    NavigationBar {
        NavigationBarItem(
            icon = { Icon(Icons.Default.Today, contentDescription = "Today") },
            label = { Text("Today") },
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
            }
        )
        NavigationBarItem(
            icon = { Icon(Icons.Default.Assignment, contentDescription = "All Work") },
            label = { Text("All Work") },
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
            }
        )
        NavigationBarItem(
            icon = { Icon(Icons.Default.BarChart, contentDescription = "Reports") },
            label = { Text("Reports") },
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
            }
        )
    }
}

