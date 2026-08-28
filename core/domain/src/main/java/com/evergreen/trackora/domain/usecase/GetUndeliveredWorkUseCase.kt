package com.evergreen.trackora.domain.usecase

import com.evergreen.trackora.domain.model.Status
import com.evergreen.trackora.domain.model.WorkEntry
import com.evergreen.trackora.domain.repository.WorkEntryRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Work that is finished but has not reached the customer.
 *
 * This is the app's central idea expressed as a query. COMPLETED means the
 * work is done; DELIVERED means it has been handed over. The gap between them
 * is where a self-employed worker loses money — a finished job sitting on a
 * shelf is unpaid — so it is the one thing the Today screen leads with.
 *
 * Deliberately not limited to today. A job completed last week and still not
 * collected is more urgent than anything finished this morning, and the old
 * Today screen could not show it at all because it only ever queried today.
 */
class GetUndeliveredWorkUseCase @Inject constructor(
    private val repository: WorkEntryRepository
) {
    operator fun invoke(): Flow<List<WorkEntry>> =
        repository.observeEntriesByStatus(Status.COMPLETED)
}
