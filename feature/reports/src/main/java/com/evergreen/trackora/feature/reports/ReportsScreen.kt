package com.evergreen.trackora.feature.reports

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.evergreen.trackora.common.R as CommonR
import com.evergreen.trackora.domain.model.Status
import com.evergreen.trackora.ui.components.IconTile
import com.evergreen.trackora.ui.components.ScreenHeader
import com.evergreen.trackora.ui.components.TrackoraEmptyState
import com.evergreen.trackora.ui.components.TrackoraErrorState
import com.evergreen.trackora.ui.components.TrackoraLoadingState
import com.evergreen.trackora.ui.components.cardBorder
import com.evergreen.trackora.ui.text.localizedDate
import com.evergreen.trackora.ui.text.localizedDateRange
import com.evergreen.trackora.ui.text.localizedDayOfMonth
import com.evergreen.trackora.ui.text.localizedNumber
import com.evergreen.trackora.ui.text.localizedRelativeDate
import com.evergreen.trackora.ui.text.localizedWeekdayShort
import com.evergreen.trackora.ui.theme.TrackoraIcons
import com.evergreen.trackora.ui.theme.TrackoraMotion
import com.evergreen.trackora.ui.theme.TrackoraSpacing
import com.evergreen.trackora.ui.theme.cardContainer
import com.evergreen.trackora.ui.theme.statusAccent
import com.evergreen.trackora.util.PersianDigits
import java.time.LocalDate
import java.util.Locale
import kotlin.math.abs

/**
 * Reports, told as a short story about the chosen period rather than as a
 * grid of tiles:
 *
 *  1. **How much** — jobs finished and items made, large, each with one plain
 *     sentence on how it compares with the period before.
 *  2. **When** — the same figures day by day, so a busy Tuesday and a quiet
 *     Thursday are visible instead of averaged away. Tap a bar to read it.
 *  3. **Where it stands** — of everything recorded in the period, how much is
 *     still in progress, finished and waiting, or delivered.
 *  4. **What to do next** — work waiting to be delivered, across all dates,
 *     and a way straight to it.
 *
 * Every figure comes from the same entries the totals are counted from, so
 * the bars always add up to the headline and nothing here is estimated.
 */

private enum class ReportsRange { DAILY, WEEKLY, MONTHLY }

private enum class ChartMeasure { JOBS, ITEMS }

@Composable
fun ReportsScreen(
    contentPadding: PaddingValues,
    viewModel: ReportsViewModel,
    /** Opens the work waiting to be delivered, in All Work. */
    onOpenWaiting: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsState()
    var range by rememberSaveable { mutableStateOf(ReportsRange.WEEKLY) }
    var measure by rememberSaveable { mutableStateOf(ChartMeasure.JOBS) }
    val ready = !uiState.isLoading && uiState.errorMessage == null
    val today = uiState.today

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .verticalScroll(rememberScrollState())
            .padding(bottom = TrackoraSpacing.xxl)
    ) {
        ScreenHeader(
            title = stringResource(id = R.string.reports_title),
            subtitle = if (ready) periodSubtitle(range, today) else null
        )

        if (uiState.isLoading) {
            TrackoraLoadingState()
            return@Column
        }
        if (uiState.errorMessage != null) {
            TrackoraErrorState(
                title = stringResource(id = CommonR.string.state_error_title),
                message = stringResource(id = CommonR.string.state_error_body),
                retryLabel = stringResource(id = CommonR.string.state_retry),
                onRetry = viewModel::retry
            )
            return@Column
        }

        RangeSelector(
            selected = range,
            onSelected = { range = it },
            modifier = Modifier.padding(horizontal = TrackoraSpacing.screen)
        )

        val summary = when (range) {
            ReportsRange.DAILY -> uiState.daily
            ReportsRange.WEEKLY -> uiState.weekly
            ReportsRange.MONTHLY -> uiState.monthly
        }

        if (!summary.hasActivity && uiState.undeliveredCount == 0) {
            // A grid of zeros looks like a broken screen. Say what is
            // actually true: nothing was recorded in this window.
            TrackoraEmptyState(
                title = stringResource(id = R.string.reports_empty_title),
                body = stringResource(id = R.string.reports_empty_body),
                icon = TrackoraIcons.ReportsOutlined
            )
            return@Column
        }

        if (summary.hasActivity) {
            Spacer(modifier = Modifier.height(TrackoraSpacing.xxl))
            Headline(
                summary = summary,
                range = range,
                modifier = Modifier.padding(horizontal = TrackoraSpacing.screen)
            )

            Spacer(modifier = Modifier.height(TrackoraSpacing.xxl))
            TrendCard(
                summary = summary,
                range = range,
                measure = measure,
                onMeasureChange = { measure = it },
                today = today,
                modifier = Modifier.padding(horizontal = TrackoraSpacing.screen)
            )
        } else {
            // Nothing in this period, but work still waits. Two giant zeros
            // and an empty chart would say the same thing worse — and a
            // Persian zero is a dot, which at display size looks like a fault.
            TrackoraEmptyState(
                title = stringResource(id = R.string.reports_empty_title),
                body = stringResource(id = R.string.reports_empty_body),
                icon = TrackoraIcons.ReportsOutlined
            )
        }

        if (summary.recorded > 0) {
            Spacer(modifier = Modifier.height(TrackoraSpacing.md))
            SplitCard(
                summary = summary,
                modifier = Modifier.padding(horizontal = TrackoraSpacing.screen)
            )
        }

        if (uiState.undeliveredCount > 0) {
            Spacer(modifier = Modifier.height(TrackoraSpacing.md))
            WaitingCard(
                count = uiState.undeliveredCount,
                oldest = uiState.oldestWaiting,
                today = today,
                onOpen = onOpenWaiting,
                modifier = Modifier.padding(horizontal = TrackoraSpacing.screen)
            )
        }
    }
}

/** "Last 7 days · Oct 21 – 27, 2026": what the period is, then exactly which days. */
@Composable
private fun periodSubtitle(range: ReportsRange, today: LocalDate): String = when (range) {
    ReportsRange.DAILY -> localizedDate(today, includeWeekday = true)
    ReportsRange.WEEKLY -> stringResource(
        R.string.reports_period_with_dates,
        stringResource(R.string.reports_period_weekly),
        localizedDateRange(today.minusDays(6), today)
    )
    ReportsRange.MONTHLY -> stringResource(
        R.string.reports_period_with_dates,
        stringResource(R.string.reports_period_monthly),
        localizedDateRange(today.minusDays(29), today)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RangeSelector(
    selected: ReportsRange,
    onSelected: (ReportsRange) -> Unit,
    modifier: Modifier = Modifier,
) {
    val ranges = listOf(
        ReportsRange.DAILY to R.string.reports_range_today,
        ReportsRange.WEEKLY to R.string.reports_range_week,
        ReportsRange.MONTHLY to R.string.reports_range_month
    )
    SingleChoiceSegmentedButtonRow(modifier = modifier.fillMaxWidth()) {
        ranges.forEachIndexed { index, (range, label) ->
            SegmentedButton(
                selected = selected == range,
                onClick = { onSelected(range) },
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
 * The two figures the period is about, set as type rather than boxed into
 * tiles: jobs finished first and largest, items made beside it. Under each,
 * how it compares with the period before — in words, and without colour,
 * because a quiet week is information to a pieceworker, not a failure.
 */
@Composable
private fun Headline(
    summary: ReportSummary,
    range: ReportsRange,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(TrackoraSpacing.xl)
    ) {
        Figure(
            value = summary.completed,
            label = pluralStringResource(R.plurals.reports_jobs_finished, summary.completed),
            comparison = comparison(range, summary.changeVsPrevious),
            style = MaterialTheme.typography.displayMedium,
            modifier = Modifier.weight(1.1f)
        )
        VerticalDivider(
            modifier = Modifier
                .fillMaxHeight()
                .padding(vertical = TrackoraSpacing.xs),
            color = MaterialTheme.colorScheme.outlineVariant
        )
        Figure(
            value = summary.totalQuantity,
            label = pluralStringResource(R.plurals.reports_items_made, summary.totalQuantity),
            comparison = comparison(range, summary.quantityChangeVsPrevious),
            style = MaterialTheme.typography.displaySmall,
            modifier = Modifier.weight(1f)
        )
    }
}

private data class Comparison(val direction: Int, val text: String)

@Composable
private fun comparison(range: ReportsRange, delta: Int?): Comparison? {
    delta ?: return null
    val amount = localizedNumber(abs(delta))
    val text = when {
        delta == 0 && range == ReportsRange.DAILY -> stringResource(R.string.reports_same_as_yesterday)
        delta == 0 -> stringResource(R.string.reports_same_as_before)
        delta > 0 -> stringResource(
            when (range) {
                ReportsRange.DAILY -> R.string.reports_more_than_yesterday
                ReportsRange.WEEKLY -> R.string.reports_more_than_last_week
                ReportsRange.MONTHLY -> R.string.reports_more_than_last_month
            },
            amount
        )
        else -> stringResource(
            when (range) {
                ReportsRange.DAILY -> R.string.reports_fewer_than_yesterday
                ReportsRange.WEEKLY -> R.string.reports_fewer_than_last_week
                ReportsRange.MONTHLY -> R.string.reports_fewer_than_last_month
            },
            amount
        )
    }
    return Comparison(direction = delta.compareTo(0), text = text)
}

@Composable
private fun Figure(
    value: Int,
    label: String,
    comparison: Comparison?,
    style: TextStyle,
    modifier: Modifier = Modifier,
) {
    // One stop for a screen reader: the number, what it counts, how it moved.
    Column(modifier = modifier.semantics(mergeDescendants = true) {}) {
        AnimatedNumber(value = value, style = style, color = MaterialTheme.colorScheme.onSurface)
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (comparison != null) {
            Row(
                modifier = Modifier.padding(top = TrackoraSpacing.sm),
                horizontalArrangement = Arrangement.spacedBy(TrackoraSpacing.xs)
            ) {
                Icon(
                    imageVector = when {
                        comparison.direction > 0 -> TrackoraIcons.TrendUp
                        comparison.direction < 0 -> TrackoraIcons.TrendDown
                        else -> TrackoraIcons.TrendFlat
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = comparison.text,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * The period day by day. For Today a single bar would say nothing, so it is
 * set against yesterday instead — the same comparison the headline makes,
 * drawn.
 */
@Composable
private fun TrendCard(
    summary: ReportSummary,
    range: ReportsRange,
    measure: ChartMeasure,
    onMeasureChange: (ChartMeasure) -> Unit,
    today: LocalDate,
    modifier: Modifier = Modifier,
) {
    var selected by remember(range) { mutableStateOf<Int?>(null) }
    val days = if (range == ReportsRange.DAILY) summary.previousDays + summary.days else summary.days
    val values = days.map { if (measure == ChartMeasure.JOBS) it.finished else it.units }
    val title = stringResource(
        when {
            range == ReportsRange.DAILY -> R.string.reports_chart_today_title
            measure == ChartMeasure.JOBS -> R.string.reports_chart_jobs_title
            else -> R.string.reports_chart_items_title
        }
    )
    val average = if (range == ReportsRange.DAILY || values.isEmpty()) null else values.sum().toFloat() / values.size
    val labels = days.mapIndexed { index, day ->
        when (range) {
            ReportsRange.DAILY ->
                stringResource(if (day.date == today) CommonR.string.date_today else CommonR.string.date_yesterday)
            ReportsRange.WEEKLY -> localizedWeekdayShort(day.date)
            // A label every seventh day, counted back from today so today is always labelled.
            ReportsRange.MONTHLY -> if ((days.lastIndex - index) % 7 == 0) localizedDayOfMonth(day.date) else null
        }
    }
    val valueText: @Composable (Int) -> String = { value ->
        if (measure == ChartMeasure.JOBS) {
            pluralStringResource(R.plurals.reports_jobs_value, value, localizedNumber(value))
        } else {
            pluralStringResource(R.plurals.reports_items_value, value, localizedNumber(value))
        }
    }
    val description = chartDescription(title, days.map { it.date }, values, today, valueText)

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.cardContainer,
        border = cardBorder()
    ) {
        Column(modifier = Modifier.padding(TrackoraSpacing.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                MeasureToggle(selected = measure, onSelected = onMeasureChange)
            }

            // What the chart is showing right now: the tapped day, or the
            // average it should be read against.
            val readout = selected?.let { index ->
                stringResource(
                    R.string.reports_chart_selection,
                    localizedRelativeDate(days[index].date, today),
                    valueText(values[index])
                )
            } ?: average?.let { stringResource(R.string.reports_chart_average, formatAverage(it)) }
            ?: stringResource(R.string.reports_chart_hint)
            AnimatedContent(
                targetState = readout,
                transitionSpec = { fadeIn(tween(TrackoraMotion.SHORT)) togetherWith fadeOut(tween(TrackoraMotion.SHORT)) },
                label = "chartReadout"
            ) { text ->
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (selected != null) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (selected != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(TrackoraSpacing.lg))
            DailyBarChart(
                values = values,
                labels = labels,
                emphasisIndex = days.indexOfFirst { it.date == today }.takeIf { it >= 0 },
                selectedIndex = selected,
                onSelect = { selected = it },
                average = average,
                showValues = values.size <= 7,
                description = description
            )

            if (selected == null && average != null) {
                Text(
                    text = stringResource(R.string.reports_chart_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = TrackoraSpacing.md)
                )
            }
        }
    }
}

/**
 * Jobs or items: which measure the chart draws. Two compact pills, each
 * inside a full 48dp touch target that stays invisible, so the toggle sits
 * neatly beside the card's title.
 */
@Composable
private fun MeasureToggle(
    selected: ChartMeasure,
    onSelected: (ChartMeasure) -> Unit,
) {
    Row(
        modifier = Modifier.selectableGroup(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        listOf(
            ChartMeasure.JOBS to R.string.reports_chart_jobs,
            ChartMeasure.ITEMS to R.string.reports_chart_items
        ).forEach { (measure, label) ->
            val isSelected = selected == measure
            val fill by animateColorAsState(
                targetValue = if (isSelected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                },
                animationSpec = tween(TrackoraMotion.SHORT),
                label = "measureFill"
            )
            Box(
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .selectable(selected = isSelected, role = Role.RadioButton, onClick = { onSelected(measure) }),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(label),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier
                        .padding(horizontal = 2.dp)
                        .clip(CircleShape)
                        .background(fill)
                        .padding(horizontal = 12.dp, vertical = 5.dp)
                )
            }
        }
    }
}

/** What a screen reader hears in place of the bars: the total, and the busiest day. */
@Composable
private fun chartDescription(
    title: String,
    dates: List<LocalDate>,
    values: List<Int>,
    today: LocalDate,
    valueText: @Composable (Int) -> String,
): String {
    val busiest = values.indices.maxByOrNull { values[it] }
    return if (busiest == null || values[busiest] == 0) {
        stringResource(R.string.reports_chart_description_empty, title)
    } else {
        stringResource(
            R.string.reports_chart_description,
            title,
            valueText(values.sum()),
            localizedRelativeDate(dates[busiest], today),
            valueText(values[busiest])
        )
    }
}

/** An average to one decimal place, without a trailing ".0", in the locale's digits. */
@Composable
private fun formatAverage(value: Float): String {
    val rounded = Math.round(value * 10) / 10f
    val plain = if (rounded % 1f == 0f) rounded.toInt().toString() else String.format(Locale.US, "%.1f", rounded)
    val persian = LocalConfiguration.current.locales[0].language == "fa"
    // Persian writes the decimal point as U+066B.
    return if (persian) PersianDigits.toPersian(plain.replace('.', '٫')) else plain
}

/**
 * How the period's recorded work stands now: still in progress, finished and
 * waiting, or delivered. The same three colours as the cards in All Work.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SplitCard(
    summary: ReportSummary,
    modifier: Modifier = Modifier,
) {
    val parts = listOf(
        Status.IN_PROGRESS to summary.inProgress,
        Status.COMPLETED to summary.waiting,
        Status.DELIVERED to summary.delivered
    )
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.cardContainer,
        border = cardBorder()
    ) {
        Column(
            modifier = Modifier.padding(TrackoraSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(TrackoraSpacing.md)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.reports_split_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = pluralStringResource(
                        R.plurals.reports_split_recorded,
                        summary.recorded,
                        localizedNumber(summary.recorded)
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            SplitBar(parts = parts.map { (status, count) -> count to statusAccent(status) })
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(TrackoraSpacing.lg),
                verticalArrangement = Arrangement.spacedBy(TrackoraSpacing.xs)
            ) {
                parts.forEach { (status, count) ->
                    LegendItem(
                        icon = TrackoraIcons.forStatus(status),
                        color = statusAccent(status),
                        label = stringResource(statusName(status)),
                        count = count
                    )
                }
            }
        }
    }
}

@Composable
private fun LegendItem(
    icon: ImageVector,
    color: Color,
    label: String,
    count: Int,
) {
    Row(
        modifier = Modifier.semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(TrackoraSpacing.xs)
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = localizedNumber(count),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

private fun statusName(status: Status): Int = when (status) {
    Status.IN_PROGRESS -> CommonR.string.status_in_progress
    Status.COMPLETED -> CommonR.string.status_completed
    Status.DELIVERED -> CommonR.string.status_delivered
}

/**
 * Work finished and not yet delivered — about now, across all dates, so it
 * is the one figure here that does not move with the period. It is also the
 * one that asks for something to be done, so it opens that work in All Work.
 */
@Composable
private fun WaitingCard(
    count: Int,
    oldest: LocalDate?,
    today: LocalDate,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val openLabel = stringResource(R.string.reports_waiting_open)
    Surface(
        onClick = onOpen,
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                role = Role.Button
                onClick(label = openLabel, action = null)
            },
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer
    ) {
        Row(
            modifier = Modifier.padding(TrackoraSpacing.lg),
            horizontalArrangement = Arrangement.spacedBy(TrackoraSpacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconTile(
                icon = TrackoraIcons.Waiting,
                container = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.1f),
                content = MaterialTheme.colorScheme.onTertiaryContainer
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.reports_awaiting_delivery),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                if (oldest != null) {
                    Text(
                        text = stringResource(R.string.reports_waiting_oldest, localizedRelativeDate(oldest, today)),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            AnimatedNumber(value = count, style = MaterialTheme.typography.headlineSmall)
            Icon(imageVector = TrackoraIcons.Forward, contentDescription = null)
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
