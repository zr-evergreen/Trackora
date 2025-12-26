package com.evergreen.trackora.domain.model

import java.time.LocalDate

/**
 * Domain model representing a work entry.
 * This is the clean domain representation, separate from the data layer entity.
 */
data class WorkEntry(
    val id: Long = 0,
    val title: String,
    val description: String? = null,
    val quantity: Int? = null,
    val status: Status,
    val date: LocalDate,
    val customField1: String? = null,
    val customField2: String? = null,
    val customField3: String? = null,
    val photoUri: String? = null,
)

