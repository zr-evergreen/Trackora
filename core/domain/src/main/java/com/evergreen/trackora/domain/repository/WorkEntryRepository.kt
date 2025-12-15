package com.evergreen.trackora.domain.repository

import com.evergreen.trackora.domain.model.WorkEntry
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * Repository interface for work entries.
 * This defines the contract for data operations in the domain layer.
 */
interface WorkEntryRepository {
    
    /**
     * Get all work entries, ordered by date (newest first).
     */
    fun getAllEntries(): Flow<List<WorkEntry>>
    
    /**
     * Get work entries for a specific date.
     */
    fun getEntriesByDate(date: LocalDate): Flow<List<WorkEntry>>
    
    /**
     * Get work entries within a date range.
     */
    fun getEntriesByDateRange(startDate: LocalDate, endDate: LocalDate): Flow<List<WorkEntry>>
    
    /**
     * Get a single work entry by ID.
     */
    suspend fun getEntryById(id: Long): WorkEntry?
    
    /**
     * Insert a new work entry.
     * @return The ID of the inserted entry.
     */
    suspend fun insertEntry(entry: WorkEntry): Long
    
    /**
     * Update an existing work entry.
     */
    suspend fun updateEntry(entry: WorkEntry)
    
    /**
     * Delete a work entry.
     */
    suspend fun deleteEntry(entry: WorkEntry)
    
    /**
     * Delete a work entry by ID.
     */
    suspend fun deleteEntryById(id: Long)
    
    /**
     * Get the count of entries for a specific date.
     */
    fun getEntryCountByDate(date: LocalDate): Flow<Int>
    
    /**
     * Get the count of entries within a date range.
     */
    fun getEntryCountByDateRange(startDate: LocalDate, endDate: LocalDate): Flow<Int>
}

