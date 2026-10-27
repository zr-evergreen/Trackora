package com.evergreen.trackora.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.evergreen.trackora.common.R
import com.evergreen.trackora.ui.text.isPersianLocale
import com.evergreen.trackora.ui.text.localizedNumberUngrouped
import com.evergreen.trackora.ui.theme.TrackoraIcons
import com.evergreen.trackora.ui.theme.TrackoraMotion
import com.evergreen.trackora.ui.theme.TrackoraSpacing
import com.evergreen.trackora.util.JalaliCalendar
import com.evergreen.trackora.util.JalaliCalendar.JalaliDate

/**
 * Jalali Date Picker Dialog - displays as a modal dialog.
 * Shows Jalali (Persian Solar) calendar for date selection.
 *
 * Styled as a Material dialog to match the Gregorian picker English users
 * get: the chosen date spelled out large at the top, so the result is
 * readable at a glance while the steppers below change it one unit at a time.
 */
@Composable
fun JalaliDatePickerDialog(
    initialDate: JalaliDate,
    onDateSelected: (JalaliDate) -> Unit,
    onDismiss: () -> Unit
) {
    // Month names follow the locale like every other string in the dialog.
    // This used to be a caller-supplied boolean, which meant the dialog could
    // be told to disagree with the language the user had actually chosen.
    val usePersianNames = isPersianLocale()

    var selectedYear by remember { mutableIntStateOf(initialDate.year) }
    var selectedMonth by remember { mutableIntStateOf(initialDate.month) }
    var selectedDay by remember { mutableIntStateOf(initialDate.day) }

    // Adjust day if it's invalid for the selected month
    val maxDays = JalaliCalendar.getDaysInJalaliMonth(selectedYear, selectedMonth)
    if (selectedDay > maxDays) {
        selectedDay = maxDays
    }

    val monthNameOf: (Int) -> String = { month ->
        if (usePersianNames) {
            JalaliCalendar.getJalaliMonthName(month)
        } else {
            JalaliCalendar.getJalaliMonthNameEn(month)
        }
    }
    val monthName = monthNameOf(selectedMonth)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f))
                // No ripple: the scrim is a way out, not a button.
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                ),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .widthIn(max = 400.dp),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 6.dp
            ) {
                Column(modifier = Modifier.padding(top = TrackoraSpacing.xxl)) {
                    Column(modifier = Modifier.padding(horizontal = TrackoraSpacing.xxl)) {
                        Text(
                            text = stringResource(R.string.date_picker_title),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(TrackoraSpacing.md))
                        Text(
                            text = "${localizedNumberUngrouped(selectedDay)} $monthName ${localizedNumberUngrouped(selectedYear)}",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            // Announced as it changes, so a screen-reader user
                            // hears the date each stepper press produces.
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                        )
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(top = TrackoraSpacing.lg),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )

                    Column(
                        modifier = Modifier.padding(
                            horizontal = TrackoraSpacing.xxl,
                            vertical = TrackoraSpacing.lg
                        ),
                        verticalArrangement = Arrangement.spacedBy(TrackoraSpacing.md)
                    ) {
                        // Year selector
                        DateSelectorSection(
                            label = stringResource(R.string.date_picker_year),
                            value = selectedYear,
                            onDecrease = { if (selectedYear > 1300) selectedYear-- },
                            onIncrease = { if (selectedYear < 1500) selectedYear++ }
                        )

                        // Month selector
                        DateSelectorSection(
                            label = stringResource(R.string.date_picker_month),
                            value = selectedMonth,
                            format = { monthNameOf(it) },
                            onDecrease = {
                                if (selectedMonth > 1) {
                                    selectedMonth--
                                    val maxDays = JalaliCalendar.getDaysInJalaliMonth(selectedYear, selectedMonth)
                                    if (selectedDay > maxDays) selectedDay = maxDays
                                }
                            },
                            onIncrease = {
                                if (selectedMonth < 12) {
                                    selectedMonth++
                                    val maxDays = JalaliCalendar.getDaysInJalaliMonth(selectedYear, selectedMonth)
                                    if (selectedDay > maxDays) selectedDay = maxDays
                                }
                            }
                        )

                        // Day selector
                        val maxDaysInMonth = JalaliCalendar.getDaysInJalaliMonth(selectedYear, selectedMonth)
                        DateSelectorSection(
                            label = stringResource(R.string.date_picker_day),
                            value = selectedDay,
                            onDecrease = { if (selectedDay > 1) selectedDay-- },
                            onIncrease = { if (selectedDay < maxDaysInMonth) selectedDay++ }
                        )
                    }

                    // Buttons, end-aligned as in every Material dialog.
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = TrackoraSpacing.lg, end = TrackoraSpacing.lg, bottom = TrackoraSpacing.lg),
                        horizontalArrangement = Arrangement.spacedBy(TrackoraSpacing.sm, Alignment.End),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = onDismiss) {
                            Text(stringResource(R.string.date_picker_cancel))
                        }
                        TextButton(
                            onClick = {
                                onDateSelected(JalaliDate(selectedYear, selectedMonth, selectedDay))
                            }
                        ) {
                            Text(
                                text = stringResource(R.string.date_picker_confirm),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DateSelectorSection(
    label: String,
    value: Int,
    // Years and days are bare numbers, so they need the locale's digits. The
    // month row passes its own names through instead.
    format: @Composable (Int) -> String = { localizedNumberUngrouped(it) },
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
) {
    val decreaseDescription = stringResource(R.string.date_picker_decrease, label)
    val increaseDescription = stringResource(R.string.date_picker_increase, label)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(TrackoraSpacing.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FilledTonalIconButton(onClick = onDecrease) {
            Icon(TrackoraIcons.Decrease, contentDescription = decreaseDescription)
        }

        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            // Rolls up when the value grows and down when it shrinks, so each
            // press visibly moves the value the way the button says.
            AnimatedContent(
                targetState = value,
                transitionSpec = {
                    val direction = if (targetState > initialState) 1 else -1
                    (slideInVertically(tween(TrackoraMotion.SHORT)) { it / 2 * direction } + fadeIn(tween(TrackoraMotion.SHORT)))
                        .togetherWith(slideOutVertically(tween(TrackoraMotion.SHORT)) { -it / 2 * direction } + fadeOut(tween(TrackoraMotion.SHORT)))
                },
                label = "dateUnit"
            ) { shown ->
                Text(
                    text = format(shown),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
            }
        }

        FilledTonalIconButton(onClick = onIncrease) {
            Icon(TrackoraIcons.Increase, contentDescription = increaseDescription)
        }
    }
}
