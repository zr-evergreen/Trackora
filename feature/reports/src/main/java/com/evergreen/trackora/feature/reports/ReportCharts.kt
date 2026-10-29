package com.evergreen.trackora.feature.reports

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.evergreen.trackora.ui.text.localizedNumber
import com.evergreen.trackora.ui.theme.TrackoraMotion
import kotlin.math.max
import kotlin.math.min

/**
 * One bar per day, oldest first, for a reporting window.
 *
 * Drawn rather than taken from a chart library: the app needs exactly this
 * one chart, and owning it means it can follow the reading direction — in
 * Persian, time runs right to left, so the oldest day sits on the right —
 * use the locale's digits, and stay within the app's own colours.
 *
 * Quiet days are drawn as a short stub on the baseline rather than left out,
 * so a gap reads as "nothing that day" and never as a missing bar. The day
 * in [emphasisIndex] — today — is drawn solid and the rest softer; tapping a
 * bar selects it instead, and tapping it again clears the selection.
 *
 * Screen readers get [description] in place of the bars.
 *
 * @param labels an axis label per bar, or null for a bar left unlabelled.
 * @param average draws a dashed reference line at this value when not null.
 * @param showValues prints each bar's figure above it; only where bars are
 *   wide enough for the number to sit over its own bar.
 */
@Composable
internal fun DailyBarChart(
    values: List<Int>,
    labels: List<String?>,
    emphasisIndex: Int?,
    selectedIndex: Int?,
    onSelect: (Int?) -> Unit,
    average: Float?,
    showValues: Boolean,
    description: String,
    modifier: Modifier = Modifier,
) {
    val accent = MaterialTheme.colorScheme.primary
    val soft = accent.copy(alpha = 0.42f)
    val stub = MaterialTheme.colorScheme.outlineVariant
    val ink = MaterialTheme.colorScheme.onSurfaceVariant
    val valueStyle = MaterialTheme.typography.labelSmall.copy(color = ink, fontWeight = FontWeight.Medium)
    val selectedValueStyle = valueStyle.copy(color = accent, fontWeight = FontWeight.Bold)
    val valueTexts = values.map { localizedNumber(it) }
    val textMeasurer = rememberTextMeasurer()
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl

    // Bars grow from the baseline whenever what they show changes — a new
    // period or the other measure — so the switch reads as the data moving.
    val growth = remember(values) { Animatable(0f) }
    LaunchedEffect(values) {
        growth.animateTo(1f, tween(TrackoraMotion.LONG + 150, easing = TrackoraMotion.EmphasizedDecelerate))
    }

    Column(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (values.size <= 2) 140.dp else 156.dp)
                .semantics { contentDescription = description }
                .pointerInput(values.size, rtl, selectedIndex) {
                    detectTapGestures { position ->
                        val slot = (position.x / size.width * values.size).toInt().coerceIn(0, values.lastIndex)
                        val index = if (rtl) values.lastIndex - slot else slot
                        onSelect(if (index == selectedIndex) null else index)
                    }
                }
        ) {
            val count = values.size
            if (count == 0) return@Canvas
            val highest = max(values.maxOrNull() ?: 0, 1)
            val slotWidth = size.width / count
            val maxBar = when {
                count <= 2 -> 56.dp.toPx()
                count <= 7 -> 28.dp.toPx()
                else -> 10.dp.toPx()
            }
            val barWidth = min(slotWidth * 0.6f, maxBar)
            val headroom = if (showValues) 20.dp.toPx() else 6.dp.toPx()
            val plot = size.height - headroom
            val baseline = size.height
            val stubHeight = 3.dp.toPx()
            val radius = CornerRadius(min(barWidth / 2, 8.dp.toPx()))

            drawLine(
                color = stub,
                start = Offset(0f, baseline - 0.5f),
                end = Offset(size.width, baseline - 0.5f),
                strokeWidth = 1.dp.toPx()
            )

            if (average != null && average > 0f) {
                val y = baseline - plot * (average / highest) * growth.value
                drawLine(
                    color = ink.copy(alpha = 0.55f),
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()))
                )
            }

            values.forEachIndexed { index, value ->
                val slot = if (rtl) count - 1 - index else index
                val centre = slotWidth * slot + slotWidth / 2
                val selected = index == selectedIndex
                val emphasised = if (selectedIndex != null) selected else index == emphasisIndex

                if (value == 0) {
                    drawRoundRect(
                        color = if (selected) accent else stub,
                        topLeft = Offset(centre - barWidth / 2, baseline - stubHeight),
                        size = Size(barWidth, stubHeight),
                        cornerRadius = CornerRadius(stubHeight / 2)
                    )
                } else {
                    val height = max(plot * value / highest * growth.value, stubHeight)
                    drawRoundRect(
                        color = if (emphasised) accent else soft,
                        topLeft = Offset(centre - barWidth / 2, baseline - height),
                        size = Size(barWidth, height),
                        cornerRadius = radius
                    )
                    if (showValues || selected) {
                        val layout = textMeasurer.measure(
                            valueTexts[index],
                            if (selected || emphasised) selectedValueStyle else valueStyle
                        )
                        drawText(
                            textLayoutResult = layout,
                            topLeft = Offset(
                                centre - layout.size.width / 2f,
                                baseline - height - layout.size.height - 2.dp.toPx()
                            ),
                            alpha = growth.value
                        )
                    }
                }
            }
        }

        // Labels as text rather than drawn, so they take the app's fonts and
        // mirror with the layout. Each sits centred under its own bar; a
        // label may run wider than its slot because its neighbours are blank.
        Row(modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
            labels.forEachIndexed { index, label ->
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    if (label != null) {
                        val strong = index == (selectedIndex ?: emphasisIndex)
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (strong) FontWeight.Bold else FontWeight.Normal,
                            color = if (strong) accent else ink,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.wrapContentWidth(unbounded = true)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Proportions as one bar cut into coloured lengths, in reading order — the
 * first segment starts at the start edge, so in Persian at the right.
 *
 * Segments with nothing in them take no room; any segment with something
 * keeps a minimum width so a single delivered job among forty is still a
 * visible sliver rather than nothing.
 */
@Composable
internal fun SplitBar(
    parts: List<Pair<Int, Color>>,
    modifier: Modifier = Modifier,
) {
    val growth = remember(parts.map { it.first }) { Animatable(0f) }
    LaunchedEffect(parts.map { it.first }) {
        growth.animateTo(1f, tween(TrackoraMotion.LONG, easing = TrackoraMotion.EmphasizedDecelerate))
    }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val track = MaterialTheme.colorScheme.surfaceContainerHigh

    Canvas(modifier = modifier.fillMaxWidth().height(12.dp)) {
        val radius = CornerRadius(size.height / 2)
        drawRoundRect(color = track, size = size, cornerRadius = radius)

        val shown = parts.filter { it.first > 0 }
        val total = shown.sumOf { it.first }
        if (total == 0) return@Canvas
        val gap = 3.dp.toPx()
        val minimum = 6.dp.toPx()
        val available = (size.width - gap * (shown.size - 1)) * growth.value
        val raw = shown.map { available * it.first / total }
        // Lift any sliver to the minimum and take the difference from the rest.
        val lifted = raw.map { max(it, minimum) }
        val excess = lifted.sum() - available
        val shrinkable = lifted.filterIndexed { i, w -> w > minimum && raw[i] >= minimum }.sum()
        val widths = lifted.mapIndexed { i, w ->
            if (excess > 0 && shrinkable > 0 && raw[i] >= minimum) w - excess * (w / shrinkable) else w
        }

        var cursor = 0f
        shown.forEachIndexed { index, (_, color) ->
            val width = widths[index]
            val left = if (rtl) size.width - cursor - width else cursor
            drawRoundRect(
                color = color,
                topLeft = Offset(left, 0f),
                size = Size(width, size.height),
                cornerRadius = radius
            )
            cursor += width + gap
        }
    }
}
