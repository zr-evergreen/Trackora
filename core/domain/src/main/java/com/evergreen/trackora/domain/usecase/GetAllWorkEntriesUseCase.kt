package com.evergreen.trackora.domain.usecase

import com.evergreen.trackora.domain.model.WorkEntry
import com.evergreen.trackora.domain.repository.WorkEntryRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Use case to get all work entries.
 */
class GetAllWorkEntriesUseCase @Inject constructor(
    private val repository: WorkEntryRepository
) {
    operator fun invoke(): Flow<List<WorkEntry>> {
        return repository.getAllEntries()
    }
}

