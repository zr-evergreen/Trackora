package com.evergreen.trackora.feature.allwork

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.evergreen.trackora.domain.model.Status
import com.evergreen.trackora.ui.components.cardBorder
import com.evergreen.trackora.ui.text.localizedNumber
import com.evergreen.trackora.ui.theme.TrackoraIcons
import com.evergreen.trackora.ui.theme.TrackoraMotion
import com.evergreen.trackora.ui.theme.cardContainer
import com.evergreen.trackora.ui.theme.container
import com.evergreen.trackora.ui.theme.content
import com.evergreen.trackora.ui.theme.statusAccent
import com.evergreen.trackora.ui.theme.statusColors

/**
 * The status filter, drawn as an overview of the list: one cell for
 * everything and one per status, all four always in view, each leading with
 * how many entries it holds within the current search.
 *
 * It replaced a scrolling row of chips, whose fourth chip — Delivered — sat
 * half off a phone's screen, and whose counts were small grey afterthoughts
 * behind the labels. Here the counts lead, in each status's own colour, so
 * the row also serves as the key to the coloured marks on the cards beneath
 * it. The chosen cell fills with its status's container colour; "All" fills
 * neutral, because showing everything is the absence of a filter, not one
 * more status.
 *
 * The cells share the width equally and their labels wrap rather than
 * truncate, so Persian labels and large text stay whole, and every cell keeps
 * a full touch target. Each is one radio button to a screen reader:
 * "In Progress, 3 entries, selected".
 */
@Composable
internal fun StatusOverview(
    selected: Status?,
    allCount: Int,
    counts: Map<Status, Int>,
    onSelect: (Status?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.cardContainer,
        border = cardBorder()
    ) {
        Row(
            modifier = Modifier
                .height(IntrinsicSize.Min)
                .padding(CELL_INSET)
                .selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(CELL_INSET)
        ) {
            OverviewCell(
                label = stringResource(id = R.string.filter_all),
                count = allCount,
                icon = null,
                accent = MaterialTheme.colorScheme.onSurface,
                fill = MaterialTheme.colorScheme.surfaceContainerHighest,
                onFill = MaterialTheme.colorScheme.onSurface,
                selected = selected == null,
                onClick = { onSelect(null) },
                modifier = Modifier.weight(1f)
            )
            listOf(
                Status.IN_PROGRESS to R.string.filter_in_progress,
                Status.COMPLETED to R.string.filter_completed,
                Status.DELIVERED to R.string.filter_delivered
            ).forEach { (status, label) ->
                OverviewCell(
                    label = stringResource(id = label),
                    count = counts[status] ?: 0,
                    icon = TrackoraIcons.forStatus(status),
                    accent = statusAccent(status),
                    fill = MaterialTheme.statusColors.container(status),
                    onFill = MaterialTheme.statusColors.content(status),
                    selected = selected == status,
                    onClick = { onSelect(status) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun OverviewCell(
    label: String,
    count: Int,
    icon: ImageVector?,
    accent: Color,
    fill: Color,
    onFill: Color,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val quiet = MaterialTheme.colorScheme.onSurfaceVariant
    val background by animateColorAsState(
        targetValue = if (selected) fill else Color.Transparent,
        animationSpec = tween(TrackoraMotion.SHORT),
        label = "overviewFill"
    )
    // An empty status keeps its place but steps back, so the eye goes to
    // where the work is.
    val figure = when {
        selected -> onFill
        count == 0 -> quiet
        else -> accent
    }
    val description = stringResource(
        R.string.all_work_filter_description,
        label,
        pluralStringResource(R.plurals.all_work_count, count, count)
    )

    Column(
        modifier = modifier
            .fillMaxHeight()
            .heightIn(min = 48.dp)
            .clip(CELL_SHAPE)
            .background(background)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            // The count and label read as one phrase, not two stops.
            .clearAndSetSemantics { contentDescription = description }
            .padding(horizontal = 4.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = figure,
                    modifier = Modifier.size(15.dp)
                )
            }
            Text(
                text = localizedNumber(count),
                // Figures need none of the room Persian letters do above and
                // below the line, so the count sits tighter than its style.
                style = MaterialTheme.typography.titleMedium.copy(lineHeight = 22.sp),
                fontWeight = FontWeight.Bold,
                color = figure
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = if (selected) onFill else quiet,
            textAlign = TextAlign.Center,
            maxLines = 2,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

private val CELL_INSET = 4.dp
private val CELL_SHAPE = RoundedCornerShape(12.dp)
