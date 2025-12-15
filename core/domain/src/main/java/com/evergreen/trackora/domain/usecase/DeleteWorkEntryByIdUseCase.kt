package com.evergreen.trackora.domain.usecase

import com.evergreen.trackora.domain.repository.WorkEntryRepository
import javax.inject.Inject

/**
 * Use case to delete a work entry by ID.
 */
class DeleteWorkEntryByIdUseCase @Inject constructor(
    private val repository: WorkEntryRepository
) {
    suspend operator fun invoke(id: Long) {
        repository.deleteEntryById(id)
    }
}

