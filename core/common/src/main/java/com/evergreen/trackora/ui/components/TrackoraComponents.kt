package com.evergreen.trackora.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.evergreen.trackora.ui.text.localizedNumber
import com.evergreen.trackora.ui.theme.TrackoraSpacing
import com.evergreen.trackora.ui.theme.cardContainer

/**
 * The title block every top-level screen opens with: a bold name, and
 * optionally one quiet line beneath it saying what the screen is showing.
 */
@Composable
fun ScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = TrackoraSpacing.screen,
                end = TrackoraSpacing.screen,
                top = TrackoraSpacing.xl,
                bottom = TrackoraSpacing.md
            )
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics { heading() }
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * A heading for a group within a screen, with an optional icon and count.
 *
 * The count sits with the heading rather than in a summary card so the number
 * is attached to the thing it counts — it reads as "four waiting", not as a
 * free-floating statistic. [accent] tints the heading for the one group a
 * screen wants noticed; everything else stays in the quiet variant colour.
 */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    count: Int? = null,
    accent: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = TrackoraSpacing.screen, vertical = TrackoraSpacing.sm)
            .semantics(mergeDescendants = true) { heading() },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(TrackoraSpacing.sm)
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(20.dp)
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = accent,
            modifier = Modifier.weight(1f)
        )
        if (count != null) {
            CountPill(count = count, color = accent)
        }
    }
}

/** A number in a soft pill of its own colour. */
@Composable
fun CountPill(
    count: Int,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Text(
        text = localizedNumber(count),
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = color,
        modifier = modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 10.dp, vertical = 1.dp)
    )
}

/** An icon on a small tinted tile — the leading mark of a settings row, a stat or a state. */
@Composable
fun IconTile(
    icon: ImageVector,
    container: Color,
    content: Color,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    shape: Shape = MaterialTheme.shapes.small,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(container),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = content,
            modifier = Modifier.size(size * 0.5f)
        )
    }
}

/**
 * Shape for one row of a grouped list: the group's outer corners are fully
 * rounded and the joins between rows nearly square, so a run of rows reads
 * as one card cut into strips rather than a stack of separate cards.
 */
fun groupedItemShape(index: Int, count: Int): Shape {
    val outer = 16.dp
    val inner = 4.dp
    val top = if (index == 0) outer else inner
    val bottom = if (index == count - 1) outer else inner
    return RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom)
}

/**
 * The hairline edge every card carries. A white card on the warm page is
 * otherwise barely distinguishable from it; in dark mode the same line keeps
 * adjacent cards from merging.
 */
@Composable
fun cardBorder(): BorderStroke =
    BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))

/**
 * Colours for every text field in the app: a filled card-coloured field with
 * a soft outline that turns indigo on focus, so forms sit on the page the way
 * the list cards do.
 */
@Composable
fun trackoraTextFieldColors(): TextFieldColors {
    val fill = MaterialTheme.colorScheme.cardContainer
    return OutlinedTextFieldDefaults.colors(
        focusedBorderColor = MaterialTheme.colorScheme.primary,
        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
        focusedContainerColor = fill,
        unfocusedContainerColor = fill,
        errorContainerColor = fill,
        disabledContainerColor = fill,
    )
}
