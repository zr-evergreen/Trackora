package com.evergreen.trackora.domain.usecase

import com.evergreen.trackora.domain.model.WorkEntry
import com.evergreen.trackora.domain.repository.WorkEntryRepository
import javax.inject.Inject

/**
 * Use case to insert a new work entry.
 */
class InsertWorkEntryUseCase @Inject constructor(
    private val repository: WorkEntryRepository
) {
    suspend operator fun invoke(entry: WorkEntry): Long {
        return repository.insertEntry(entry)
    }
}

