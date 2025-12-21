package com.evergreen.trackora.util

/**
 * Application-wide constants.
 * Centralized location for magic numbers and string constants.
 */
object AppConstants {
    
    // Field length limits
    const val TITLE_MAX_LENGTH = 100
    const val DESCRIPTION_MAX_LENGTH = 500
    const val QUANTITY_MIN_VALUE = 1
    const val QUANTITY_MAX_VALUE = 999_999
    
    // Error messages
    object Errors {
        const val TITLE_REQUIRED = "Title is required"
        const val TITLE_TOO_LONG = "Title must be 100 characters or less"
        const val DESCRIPTION_TOO_LONG = "Description must be 500 characters or less"
        const val QUANTITY_INVALID = "Please enter a valid number"
        const val QUANTITY_TOO_SMALL = "Quantity must be greater than 0"
        const val QUANTITY_TOO_LARGE = "Quantity is too large"
        const val ENTRY_NOT_FOUND = "Entry not found"
        const val FAILED_TO_LOAD = "Failed to load entry"
        const val FAILED_TO_SAVE = "Unable to save entry"
        const val FAILED_TO_LOAD_ENTRIES = "Failed to load entries"
        const val FAILED_TO_ADD = "Failed to add entry"
        const val FAILED_TO_UPDATE = "Failed to update entry"
        const val FAILED_TO_UPDATE_STATUS = "Failed to update status"
        const val TITLE_CANNOT_BE_EMPTY = "Title cannot be empty"
    }
}

