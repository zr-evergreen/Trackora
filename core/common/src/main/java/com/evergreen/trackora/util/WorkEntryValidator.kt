package com.evergreen.trackora.util

/**
 * Validates work entry fields according to business rules.
 * Follows Single Responsibility Principle - only handles validation logic.
 */
object WorkEntryValidator {

    /**
     * Validates a work entry title.
     */
    fun validateTitle(title: String, allowEmptyWhileTyping: Boolean = false): ValidationResult {
        return when {
            title.isBlank() && !allowEmptyWhileTyping ->
                ValidationResult.Invalid(AppConstants.Errors.TITLE_REQUIRED)

            title.length > AppConstants.TITLE_MAX_LENGTH ->
                ValidationResult.Invalid(AppConstants.Errors.TITLE_TOO_LONG)

            else -> ValidationResult.Valid
        }
    }

    /**
     * Validates a work entry description.
     */
    fun validateDescription(description: String): ValidationResult {
        return when {
            description.length > AppConstants.DESCRIPTION_MAX_LENGTH ->
                ValidationResult.Invalid(AppConstants.Errors.DESCRIPTION_TOO_LONG)

            else -> ValidationResult.Valid
        }
    }

    /**
     * Validates a quantity input string.
     */
    fun validateQuantity(quantityInput: String): ValidationResult {
        if (quantityInput.isBlank()) {
            return ValidationResult.Valid // Quantity is optional
        }

        val quantity = quantityInput.toIntOrNull()
        return when {
            quantity == null ->
                ValidationResult.Invalid(AppConstants.Errors.QUANTITY_INVALID)

            quantity < AppConstants.QUANTITY_MIN_VALUE ->
                ValidationResult.Invalid(AppConstants.Errors.QUANTITY_TOO_SMALL)

            quantity > AppConstants.QUANTITY_MAX_VALUE ->
                ValidationResult.Invalid(AppConstants.Errors.QUANTITY_TOO_LARGE)

            else -> ValidationResult.Valid
        }
    }

    /**
     * Validates all fields of a work entry for saving.
     */
    fun validateForSave(
        title: String,
        quantityInput: String
    ): Pair<ValidationResult, ValidationResult> {
        return validateTitle(title) to validateQuantity(quantityInput)
    }

    /**
     * Sanitizes title input (trims leading whitespace, limits length).
     */
    fun sanitizeTitle(input: String): String {
        return input.trimStart().take(AppConstants.TITLE_MAX_LENGTH)
    }

    /**
     * Sanitizes description input (trims leading whitespace, limits length).
     */
    fun sanitizeDescription(input: String): String {
        return input.trimStart().take(AppConstants.DESCRIPTION_MAX_LENGTH)
    }

    /**
     * Sanitizes quantity input (removes non-digits).
     */
    fun sanitizeQuantity(input: String): String {
        return input.filter { it.isDigit() }
    }
}

