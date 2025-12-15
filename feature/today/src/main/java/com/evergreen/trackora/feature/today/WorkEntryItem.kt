package com.evergreen.trackora.feature.today

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.evergreen.trackora.domain.model.Status
import com.evergreen.trackora.domain.model.WorkEntry
import com.evergreen.trackora.ui.theme.TrackoraTheme
import java.time.LocalDate

/**
 * Composable for displaying a single work entry item.
 */
@Composable
fun WorkEntryItem(
    entry: WorkEntry,
    onStatusClick: (Status) -> Unit,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    showStatusSelector: Boolean = true
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .then(
                if (onClick != null) Modifier.clickable { onClick() } else Modifier
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = entry.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                entry.description?.let { description ->
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StatusChip(status = entry.status)
                    
                    if (entry.quantity != null) {
                        Text(
                            text = "Qty: ${entry.quantity}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            
            if (showStatusSelector) {
                StatusSelector(
                    currentStatus = entry.status,
                    onStatusSelected = onStatusClick
                )
            }
        }
    }
}

@Composable
private fun StatusChip(
    status: Status,
    modifier: Modifier = Modifier
) {
    val (text, color) = when (status) {
        Status.IN_PROGRESS -> "In Progress" to Color(0xFFFF9800)
        Status.COMPLETED -> "Completed" to Color(0xFF4CAF50)
        Status.DELIVERED -> "Delivered" to Color(0xFF2196F3)
    }
    
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.2f)
        )
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun StatusSelector(
    currentStatus: Status,
    onStatusSelected: (Status) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Status.values().forEach { status ->
            val isSelected = status == currentStatus
            AssistChip(
                onClick = { onStatusSelected(status) },
                label = {
                    Text(
                        text = when (status) {
                            Status.IN_PROGRESS -> "IP"
                            Status.COMPLETED -> "C"
                            Status.DELIVERED -> "D"
                        },
                        style = MaterialTheme.typography.labelSmall
                    )
                },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = if (isSelected) {
                        when (status) {
                            Status.IN_PROGRESS -> Color(0xFFFF9800).copy(alpha = 0.2f)
                            Status.COMPLETED -> Color(0xFF4CAF50).copy(alpha = 0.2f)
                            Status.DELIVERED -> Color(0xFF2196F3).copy(alpha = 0.2f)
                        }
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    }
                ),
                border = if (isSelected) {
                    BorderStroke(
                        width = 2.dp,
                        color = when (status) {
                            Status.IN_PROGRESS -> Color(0xFFFF9800)
                            Status.COMPLETED -> Color(0xFF4CAF50)
                            Status.DELIVERED -> Color(0xFF2196F3)
                        }
                    )
                } else null
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun WorkEntryItemPreview() {
    TrackoraTheme {
        WorkEntryItem(
            entry = WorkEntry(
                id = 1,
                title = "Sample Work Entry",
                description = "This is a sample description for the work entry",
                quantity = 5,
                status = Status.IN_PROGRESS,
                date = LocalDate.now()
            ),
            onStatusClick = {}
        )
    }
}

