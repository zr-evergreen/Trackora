package com.evergreen.trackora.domain.usecase

import com.evergreen.trackora.domain.model.WorkEntry
import com.evergreen.trackora.domain.repository.WorkEntryRepository
import javax.inject.Inject

/**
 * Use case to fetch a single work entry by ID.
 */
class GetWorkEntryByIdUseCase @Inject constructor(
    private val repository: WorkEntryRepository
) {
    suspend operator fun invoke(id: Long): WorkEntry? {
        return repository.getEntryById(id)
    }
}


