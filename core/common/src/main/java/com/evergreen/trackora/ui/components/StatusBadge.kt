package com.evergreen.trackora.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.evergreen.trackora.common.R
import com.evergreen.trackora.domain.model.Status
import com.evergreen.trackora.ui.theme.statusColors

/**
 * The one way a work entry's status is drawn.
 *
 * Previously three different components rendered it — a chip on Today, a pill
 * on All Work, and a selector that also showed it — each with its own copy of
 * the same three `Color(0xFF…)` literals. Six hardcoded hexes across two
 * modules meant changing one status colour required six edits, and none of
 * them were checked against the dark scheme.
 *
 * Colours now come from [statusColors], so this badge and anything else that
 * shows status move together and follow the theme.
 */
@Composable
fun StatusBadge(
    status: Status,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.statusColors
    val (container, content) = when (status) {
        Status.IN_PROGRESS -> colors.inProgressContainer to colors.onInProgressContainer
        Status.COMPLETED -> colors.completedContainer to colors.onCompletedContainer
        Status.DELIVERED -> colors.deliveredContainer to colors.onDeliveredContainer
    }

    val label = when (status) {
        Status.IN_PROGRESS -> R.string.status_in_progress
        Status.COMPLETED -> R.string.status_completed
        Status.DELIVERED -> R.string.status_delivered
    }

    Text(
        text = stringResource(id = label),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Medium,
        color = content,
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .background(container)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    )
}
