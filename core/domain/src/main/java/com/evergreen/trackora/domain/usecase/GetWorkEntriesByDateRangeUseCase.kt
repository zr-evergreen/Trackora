package com.evergreen.trackora.domain.usecase

import com.evergreen.trackora.domain.model.WorkEntry
import com.evergreen.trackora.domain.repository.WorkEntryRepository
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import javax.inject.Inject

/**
 * Use case to get work entries within a date range.
 */
class GetWorkEntriesByDateRangeUseCase @Inject constructor(
    private val repository: WorkEntryRepository
) {
    operator fun invoke(startDate: LocalDate, endDate: LocalDate): Flow<List<WorkEntry>> {
        return repository.getEntriesByDateRange(startDate, endDate)
    }
}

