package com.evergreen.trackora.feature.reports

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.evergreen.trackora.common.R as CommonR
import com.evergreen.trackora.ui.components.IconTile
import com.evergreen.trackora.ui.components.ScreenHeader
import com.evergreen.trackora.ui.components.TrackoraEmptyState
import com.evergreen.trackora.ui.components.TrackoraErrorState
import com.evergreen.trackora.ui.components.TrackoraLoadingState
import com.evergreen.trackora.ui.components.cardBorder
import com.evergreen.trackora.ui.text.localizedNumber
import com.evergreen.trackora.ui.theme.TrackoraIcons
import com.evergreen.trackora.ui.theme.TrackoraMotion
import com.evergreen.trackora.ui.theme.TrackoraSpacing
import com.evergreen.trackora.ui.theme.cardContainer
import com.evergreen.trackora.ui.theme.statusColors

/**
 * Reports screen for viewing numeric summaries.
 *
 * The window is picked with a segmented control rather than chips: exactly one
 * is always selected, which is what segmented buttons say and filter chips do
 * not. The subtitle names the window in plain words — "Last 7 days" — because
 * "Weekly" leaves open whether that means this calendar week.
 */

private enum class ReportsRange {
    DAILY, WEEKLY, MONTHLY
}

@Composable
fun ReportsScreen(
    contentPadding: PaddingValues,
    viewModel: ReportsViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedRange by rememberSaveable { mutableStateOf(ReportsRange.DAILY) }
    val ready = !uiState.isLoading && uiState.errorMessage == null

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            // Scrolls so the figures stay reachable at large text sizes.
            .verticalScroll(rememberScrollState())
    ) {
        ScreenHeader(
            title = stringResource(id = R.string.reports_title),
            subtitle = if (ready) {
                stringResource(
                    id = when (selectedRange) {
                        ReportsRange.DAILY -> R.string.reports_period_daily
                        ReportsRange.WEEKLY -> R.string.reports_period_weekly
                        ReportsRange.MONTHLY -> R.string.reports_period_monthly
                    }
                )
            } else {
                null
            }
        )

        if (uiState.isLoading) {
            TrackoraLoadingState()
        } else if (uiState.errorMessage != null) {
            TrackoraErrorState(
                title = stringResource(id = CommonR.string.state_error_title),
                message = stringResource(id = CommonR.string.state_error_body),
                retryLabel = stringResource(id = CommonR.string.state_retry),
                onRetry = viewModel::retry
            )
        } else {
            RangeSelector(
                selectedRange = selectedRange,
                onRangeSelected = { selectedRange = it },
                modifier = Modifier.padding(horizontal = TrackoraSpacing.screen)
            )

            Spacer(modifier = Modifier.height(TrackoraSpacing.lg))

            val currentSummary = when (selectedRange) {
                ReportsRange.DAILY -> uiState.daily
                ReportsRange.WEEKLY -> uiState.weekly
                ReportsRange.MONTHLY -> uiState.monthly
            }

            if (!currentSummary.hasActivity && uiState.undeliveredCount == 0) {
                // A grid of zeros looks like a broken screen. Say what is
                // actually true: nothing was recorded in this window.
                TrackoraEmptyState(
                    title = stringResource(id = R.string.reports_empty_title),
                    body = stringResource(id = R.string.reports_empty_body),
                    icon = TrackoraIcons.ReportsOutlined
                )
            } else {
                ReportsFigures(
                    summary = currentSummary,
                    undeliveredCount = uiState.undeliveredCount,
                    modifier = Modifier.padding(horizontal = TrackoraSpacing.screen)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RangeSelector(
    selectedRange: ReportsRange,
    onRangeSelected: (ReportsRange) -> Unit,
    modifier: Modifier = Modifier,
) {
    val ranges = listOf(
        ReportsRange.DAILY to R.string.reports_daily,
        ReportsRange.WEEKLY to R.string.reports_weekly,
        ReportsRange.MONTHLY to R.string.reports_monthly
    )
    SingleChoiceSegmentedButtonRow(modifier = modifier.fillMaxWidth()) {
        ranges.forEachIndexed { index, (range, label) ->
            SegmentedButton(
                selected = selectedRange == range,
                onClick = { onRangeSelected(range) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = ranges.size),
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    activeContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    activeBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    inactiveContainerColor = MaterialTheme.colorScheme.cardContainer,
                    inactiveBorderColor = MaterialTheme.colorScheme.outlineVariant
                ),
                label = { Text(text = stringResource(id = label), maxLines = 1) }
            )
        }
    }
}

/**
 * The figures for one window.
 *
 * ### What this replaces
 *
 * The previous card printed Completed, then Delivered, then Completed again
 * and Total quantity — the same number twice in one card, in two different
 * type sizes. It also had no empty state, so a user with no data saw a grid of
 * zeros rather than an explanation.
 *
 * The figures now answer the questions a self-employed worker actually asks:
 * how much did I finish, how many units was that, how does it compare with the
 * period before, and — the one that costs money — how much is still sitting
 * undelivered.
 */
@Composable
private fun ReportsFigures(
    summary: ReportSummary,
    undeliveredCount: Int,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            // Intrinsic min height so a two-line delta on one tile does not
            // leave the other visibly short beside it.
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(TrackoraSpacing.md)
        ) {
            StatTile(
                icon = TrackoraIcons.Completed,
                iconContainer = MaterialTheme.statusColors.completedContainer,
                iconContent = MaterialTheme.statusColors.onCompletedContainer,
                label = stringResource(id = R.string.reports_completed),
                value = summary.completed,
                delta = summary.changeVsPrevious,
                modifier = Modifier.weight(1f)
            )
            StatTile(
                icon = TrackoraIcons.Quantity,
                iconContainer = MaterialTheme.colorScheme.primaryContainer,
                iconContent = MaterialTheme.colorScheme.onPrimaryContainer,
                label = stringResource(id = R.string.reports_quantity),
                value = summary.totalQuantity,
                modifier = Modifier.weight(1f)
            )
        }

        // Present-tense, not scoped to the selected window, so it is separated
        // from the two figures above rather than sitting in the same row.
        if (undeliveredCount > 0) {
            Spacer(modifier = Modifier.height(TrackoraSpacing.md))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = TrackoraSpacing.lg, vertical = 14.dp)
                        .semantics(mergeDescendants = true) {},
                    horizontalArrangement = Arrangement.spacedBy(TrackoraSpacing.md),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconTile(
                        icon = TrackoraIcons.Waiting,
                        container = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.1f),
                        content = MaterialTheme.colorScheme.onTertiaryContainer,
                        size = 36.dp
                    )
                    Text(
                        text = stringResource(id = R.string.reports_awaiting_delivery),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    AnimatedNumber(
                        value = undeliveredCount,
                        style = MaterialTheme.typography.titleLarge
                    )
                }
            }
        }
    }
}

/**
 * One figure, with an optional change against the previous period.
 *
 * The delta is plain text rather than a coloured arrow: for a pieceworker a
 * quiet month is information, not a failure, and painting it red would be the
 * app passing judgement on their week.
 */
@Composable
private fun StatTile(
    icon: ImageVector,
    iconContainer: Color,
    iconContent: Color,
    label: String,
    value: Int,
    modifier: Modifier = Modifier,
    delta: Int? = null,
) {
    Surface(
        modifier = modifier.fillMaxHeight(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.cardContainer,
        border = cardBorder()
    ) {
        Column(
            modifier = Modifier
                .padding(TrackoraSpacing.lg)
                .semantics(mergeDescendants = true) {}
        ) {
            IconTile(icon = icon, container = iconContainer, content = iconContent, size = 36.dp)
            Spacer(modifier = Modifier.height(TrackoraSpacing.md))
            AnimatedNumber(
                value = value,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (delta != null && delta != 0) {
                Text(
                    text = stringResource(
                        id = if (delta > 0) R.string.reports_delta_up else R.string.reports_delta_down,
                        localizedNumber(kotlin.math.abs(delta))
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = TrackoraSpacing.sm)
                )
            }
        }
    }
}

/**
 * A figure that rolls to its new value when the window changes, rising when
 * it grows and falling when it shrinks, so switching from Daily to Monthly
 * shows the numbers change rather than just swapping them.
 */
@Composable
private fun AnimatedNumber(
    value: Int,
    style: TextStyle,
    color: Color = Color.Unspecified,
) {
    AnimatedContent(
        targetState = value,
        transitionSpec = {
            val direction = if (targetState > initialState) 1 else -1
            val enter = slideInVertically(tween(TrackoraMotion.MEDIUM, easing = TrackoraMotion.EmphasizedDecelerate)) {
                it / 2 * direction
            } + fadeIn(tween(TrackoraMotion.MEDIUM))
            val exit = slideOutVertically(tween(TrackoraMotion.SHORT, easing = TrackoraMotion.EmphasizedAccelerate)) {
                -it / 2 * direction
            } + fadeOut(tween(TrackoraMotion.SHORT))
            (enter togetherWith exit).using(SizeTransform(clip = false))
        },
        label = "figure"
    ) { number ->
        Text(
            text = localizedNumber(number),
            style = style,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}
