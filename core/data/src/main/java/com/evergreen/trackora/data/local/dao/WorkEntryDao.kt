package com.evergreen.trackora.data.local.dao

import androidx.room.*
import com.evergreen.trackora.data.local.entity.WorkEntry
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * Data Access Object for WorkEntry operations.
 */
@Dao
interface WorkEntryDao {
    
    @Query("SELECT * FROM work_entries ORDER BY date DESC, id DESC")
    fun getAllEntries(): Flow<List<WorkEntry>>
    
    @Query("SELECT * FROM work_entries WHERE date = :date ORDER BY id DESC")
    fun getEntriesByDate(date: LocalDate): Flow<List<WorkEntry>>
    
    @Query("SELECT * FROM work_entries WHERE date >= :startDate AND date <= :endDate ORDER BY date DESC, id DESC")
    fun getEntriesByDateRange(startDate: LocalDate, endDate: LocalDate): Flow<List<WorkEntry>>
    
    @Query("SELECT * FROM work_entries WHERE id = :id")
    suspend fun getEntryById(id: Long): WorkEntry?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntry(entry: WorkEntry): Long
    
    @Update
    suspend fun updateEntry(entry: WorkEntry)
    
    @Delete
    suspend fun deleteEntry(entry: WorkEntry)
    
    @Query("DELETE FROM work_entries WHERE id = :id")
    suspend fun deleteEntryById(id: Long)
    
    @Query("SELECT COUNT(*) FROM work_entries WHERE date = :date")
    fun getEntryCountByDate(date: LocalDate): Flow<Int>
    
    @Query("SELECT COUNT(*) FROM work_entries WHERE date >= :startDate AND date <= :endDate")
    fun getEntryCountByDateRange(startDate: LocalDate, endDate: LocalDate): Flow<Int>
    
    /**
     * Observes entries in one status, oldest first.
     *
     * Ascending by date on purpose: this drives the undelivered list, where the
     * job that has been waiting longest is the one most worth chasing.
     */
    @Query("SELECT * FROM work_entries WHERE status = :status ORDER BY date ASC, id ASC")
    fun observeEntriesByStatus(status: String): Flow<List<WorkEntry>>

    @Query("SELECT * FROM work_entries WHERE status = :status")
    suspend fun getEntriesByStatus(status: String): List<WorkEntry>
}

