package com.evergreen.trackora.feature.addedit

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
 * Screen for adding or editing work entries.
 * TODO: Implement the add/edit functionality
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditWorkScreen(
    entryId: Long? = null,
    onNavigateBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (entryId == null) "Add Work Entry" else "Edit Work Entry"
                    )
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
                text = "Add/Edit Work Entry Screen",
                modifier = Modifier.padding(16.dp)
            )
            // TODO: Implement form for adding/editing work entries
        }
    }
}

