package com.evergreen.trackora.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Today
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
                    onClick = { navController.navigate(AddEditWorkRoute()) }
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add Work Entry"
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

