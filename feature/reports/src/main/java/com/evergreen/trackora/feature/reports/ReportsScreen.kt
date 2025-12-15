package com.evergreen.trackora.feature.reports

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Reports screen for viewing statistics.
 * TODO: Implement reports functionality
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen() {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(text = "Reports")
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            Text(
                text = "Reports Screen",
                modifier = Modifier.padding(16.dp)
            )
            // TODO: Implement reports and statistics
        }
    }
}

