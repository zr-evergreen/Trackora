package com.evergreen.trackora.domain.usecase

import com.evergreen.trackora.domain.model.WorkEntry
import com.evergreen.trackora.domain.repository.WorkEntryRepository
import javax.inject.Inject

/**
 * Use case to delete a work entry.
 */
class DeleteWorkEntryUseCase @Inject constructor(
    private val repository: WorkEntryRepository
) {
    suspend operator fun invoke(entry: WorkEntry) {
        repository.deleteEntry(entry)
    }
}

