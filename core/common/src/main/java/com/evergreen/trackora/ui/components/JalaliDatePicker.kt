package com.evergreen.trackora.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.evergreen.trackora.util.JalaliCalendar
import com.evergreen.trackora.util.JalaliCalendar.JalaliDate

/**
 * Jalali Date Picker Dialog - displays as a modal dialog.
 * Shows Jalali (Persian Solar) calendar for date selection.
 */
@Composable
fun JalaliDatePickerDialog(
    initialDate: JalaliDate,
    onDateSelected: (JalaliDate) -> Unit,
    onDismiss: () -> Unit,
    usePersianNames: Boolean = true
) {
    var selectedYear by remember { mutableIntStateOf(initialDate.year) }
    var selectedMonth by remember { mutableIntStateOf(initialDate.month) }
    var selectedDay by remember { mutableIntStateOf(initialDate.day) }
    
    // Adjust day if it's invalid for the selected month
    val maxDays = JalaliCalendar.getDaysInJalaliMonth(selectedYear, selectedMonth)
    if (selectedDay > maxDays) {
        selectedDay = maxDays
    }
    
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
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.9f),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
            Text(
                text = if (usePersianNames) "انتخاب تاریخ" else "Select Date",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Year selector
            DateSelectorSection(
                label = if (usePersianNames) "سال" else "Year",
                value = selectedYear,
                onDecrease = { if (selectedYear > 1300) selectedYear-- },
                onIncrease = { if (selectedYear < 1500) selectedYear++ },
                onValueChange = { /* Year input handled by buttons */ }
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Month selector
            DateSelectorSection(
                label = if (usePersianNames) "ماه" else "Month",
                value = selectedMonth,
                displayValue = if (usePersianNames) {
                    JalaliCalendar.getJalaliMonthName(selectedMonth)
                } else {
                    JalaliCalendar.getJalaliMonthNameEn(selectedMonth)
                },
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
                },
                onValueChange = { /* Month input handled by buttons */ }
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Day selector
            val maxDaysInMonth = JalaliCalendar.getDaysInJalaliMonth(selectedYear, selectedMonth)
            DateSelectorSection(
                label = if (usePersianNames) "روز" else "Day",
                value = selectedDay,
                onDecrease = { if (selectedDay > 1) selectedDay-- },
                onIncrease = { if (selectedDay < maxDaysInMonth) selectedDay++ },
                onValueChange = { /* Day input handled by buttons */ }
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (usePersianNames) "لغو" else "Cancel")
                }
                Button(
                    onClick = {
                        onDateSelected(JalaliDate(selectedYear, selectedMonth, selectedDay))
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (usePersianNames) "تأیید" else "Confirm")
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
    displayValue: String = value.toString(),
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    onValueChange: (Int) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = onDecrease,
                modifier = Modifier.width(56.dp)
            ) {
                Text("−", style = MaterialTheme.typography.titleLarge)
            }
            
            Card(
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = displayValue,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            
            OutlinedButton(
                onClick = onIncrease,
                modifier = Modifier.width(56.dp)
            ) {
                Text("+", style = MaterialTheme.typography.titleLarge)
            }
        }
    }
}

