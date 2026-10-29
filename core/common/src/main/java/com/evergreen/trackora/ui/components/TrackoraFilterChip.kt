package com.evergreen.trackora.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.evergreen.trackora.ui.text.localizedNumber
import com.evergreen.trackora.ui.theme.TrackoraIcons

/**
 * The one filter chip in the app.
 *
 * Selection is shown three ways at once — fill, a tick, and the label colour —
 * so it never depends on telling two tints apart. By default the selected fill
 * is the app's indigo, because selection is emphasis and the app has one
 * emphasis colour. A chip that stands for a status passes [icon] and that
 * status's own colours instead, so choosing "Completed" looks like the
 * Completed badge it will produce.
 */
@Composable
fun TrackoraFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    selectedContainerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    selectedContentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    /**
     * How many entries the chip would show, printed after the label so a
     * filter says what is behind it before it is tapped. Null for none.
     */
    count: Int? = null,
) {
    val leading = when {
        selected -> TrackoraIcons.Save
        else -> icon
    }
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (count != null) {
                    Text(
                        text = localizedNumber(count),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = LocalContentColor.current.copy(alpha = 0.7f),
                        maxLines = 1
                    )
                }
            }
        },
        leadingIcon = leading?.let { vector ->
            {
                Icon(
                    imageVector = vector,
                    contentDescription = null,
                    modifier = Modifier.size(FilterChipDefaults.IconSize)
                )
            }
        },
        shape = MaterialTheme.shapes.small,
        colors = FilterChipDefaults.filterChipColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            selectedContainerColor = selectedContainerColor,
            selectedLabelColor = selectedContentColor,
            selectedLeadingIconColor = selectedContentColor
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = MaterialTheme.colorScheme.outlineVariant,
            selectedBorderColor = Color.Transparent
        ),
        modifier = modifier
    )
}

/**
 * A horizontally scrollable row of filter chips, inset to the screen edge.
 *
 * Scrollable because Persian labels are longer than their English equivalents
 * — «در حال انجام» against "In Progress" — and on a narrow screen a fixed row
 * either truncates the last chip or squeezes them all below a usable tap size.
 * The inset lives inside the scroll so chips slide to the screen edge rather
 * than being clipped short of it.
 */
@Composable
fun TrackoraFilterRow(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: @Composable () -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(contentPadding),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        content()
    }
}
