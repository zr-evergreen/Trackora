package com.evergreen.trackora.ui.theme

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DynamicForm
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.LocalShipping
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Numbers
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PendingActions
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.ui.graphics.vector.ImageVector
import com.evergreen.trackora.domain.model.Status

/**
 * Every icon the app shows, named for what it means here.
 *
 * One family — Material Rounded — so the soft corners match the type and the
 * card radii, and no screen picks its own. Bottom navigation pairs each filled
 * icon with its outlined form, the Material convention for selected and
 * unselected. Anything that points a direction is AutoMirrored so it turns
 * round in Persian.
 *
 * The three statuses each have one glyph, used both on the status badge and
 * on the button that moves an entry into that status, so "Delivered" looks the
 * same as a label and as an action:
 *  - in progress: a clock — work in hand;
 *  - completed: a ticked circle — finished, waiting for its customer;
 *  - delivered: a delivery van — handed over.
 */
object TrackoraIcons {

    // Navigation: (selected, unselected)
    val Today: ImageVector = Icons.Rounded.Today
    val TodayOutlined: ImageVector = Icons.Outlined.Today
    val History: ImageVector = Icons.AutoMirrored.Rounded.ReceiptLong
    val HistoryOutlined: ImageVector = Icons.AutoMirrored.Outlined.ReceiptLong
    val Reports: ImageVector = Icons.Rounded.Insights
    val ReportsOutlined: ImageVector = Icons.Outlined.Insights
    val Settings: ImageVector = Icons.Rounded.Settings
    val SettingsOutlined: ImageVector = Icons.Outlined.Settings

    // Actions
    val Add: ImageVector = Icons.Rounded.Add
    val Back: ImageVector = Icons.AutoMirrored.Rounded.ArrowBack
    val Forward: ImageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight
    val Search: ImageVector = Icons.Rounded.Search
    val Clear: ImageVector = Icons.Rounded.Close
    val Delete: ImageVector = Icons.Rounded.DeleteOutline
    val Save: ImageVector = Icons.Rounded.Check
    val Retry: ImageVector = Icons.Rounded.Refresh
    val Increase: ImageVector = Icons.Rounded.Add
    val Decrease: ImageVector = Icons.Rounded.Remove
    val Camera: ImageVector = Icons.Rounded.PhotoCamera
    val Gallery: ImageVector = Icons.Rounded.PhotoLibrary
    val AddPhoto: ImageVector = Icons.Rounded.AddPhotoAlternate
    val Calendar: ImageVector = Icons.Rounded.CalendarMonth
    val Export: ImageVector = Icons.Rounded.IosShare

    // Sections and subjects
    val Waiting: ImageVector = Icons.Rounded.PendingActions
    val TodaysWork: ImageVector = Icons.Rounded.EventAvailable
    val Quantity: ImageVector = Icons.Rounded.Numbers
    val Write: ImageVector = Icons.Rounded.EditNote
    val NoResults: ImageVector = Icons.Rounded.SearchOff
    val Error: ImageVector = Icons.Rounded.ErrorOutline
    val Appearance: ImageVector = Icons.Rounded.Palette
    val WorkFields: ImageVector = Icons.Rounded.DynamicForm
    val Language: ImageVector = Icons.Rounded.Translate
    val Reminder: ImageVector = Icons.Rounded.NotificationsActive
    val About: ImageVector = Icons.Rounded.Info
    val ThemeSystem: ImageVector = Icons.Rounded.BrightnessAuto
    val ThemeLight: ImageVector = Icons.Rounded.LightMode
    val ThemeDark: ImageVector = Icons.Rounded.DarkMode

    // Statuses
    val InProgress: ImageVector = Icons.Rounded.Schedule
    val Completed: ImageVector = Icons.Rounded.TaskAlt
    val Delivered: ImageVector = Icons.Rounded.LocalShipping

    fun forStatus(status: Status): ImageVector = when (status) {
        Status.IN_PROGRESS -> InProgress
        Status.COMPLETED -> Completed
        Status.DELIVERED -> Delivered
    }
}
