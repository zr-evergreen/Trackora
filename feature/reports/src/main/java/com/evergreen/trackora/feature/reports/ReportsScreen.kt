package com.evergreen.trackora.feature.reports

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Reports screen for viewing numeric summaries.
 */
@Composable
fun ReportsScreen(
    contentPadding: androidx.compose.foundation.layout.PaddingValues,
    viewModel: ReportsViewModel
) {
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            text = "Reports",
            style = MaterialTheme.typography.titleLarge
        )

        Spacer(modifier = Modifier.height(12.dp))

        if (uiState.isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        } else {
            ReportCard(uiState.daily)
            ReportCard(uiState.weekly)
            ReportCard(uiState.monthly)
        }
    }
}

@Composable
private fun ReportCard(summary: ReportSummary) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = summary.label,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "Completed: ${summary.completed}",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "Delivered: ${summary.delivered}",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "Total Quantity: ${summary.totalQuantity}",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

