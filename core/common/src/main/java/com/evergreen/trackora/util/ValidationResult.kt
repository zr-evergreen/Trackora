package com.evergreen.trackora.util

/**
 * Represents the result of a validation operation.
 * Follows the Result pattern for better error handling.
 */
sealed class ValidationResult {
    object Valid : ValidationResult()
    data class Invalid(val message: String) : ValidationResult()
    
    val isValid: Boolean
        get() = this is Valid
    
    val errorMessage: String?
        get() = (this as? Invalid)?.message
}

