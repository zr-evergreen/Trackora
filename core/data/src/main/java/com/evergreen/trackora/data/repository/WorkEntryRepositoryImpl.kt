package com.evergreen.trackora.data.repository

import com.evergreen.trackora.data.local.dao.WorkEntryDao
import com.evergreen.trackora.data.mapper.WorkEntryMapper
import com.evergreen.trackora.domain.model.WorkEntry
import com.evergreen.trackora.domain.repository.WorkEntryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject

/**
 * Implementation of WorkEntryRepository using Room database.
 */
class WorkEntryRepositoryImpl @Inject constructor(
    private val workEntryDao: WorkEntryDao
) : WorkEntryRepository {
    
    override fun getAllEntries(): Flow<List<WorkEntry>> {
        return workEntryDao.getAllEntries().map { entities ->
            WorkEntryMapper.toDomainList(entities)
        }
    }
    
    override fun getEntriesByDate(date: LocalDate): Flow<List<WorkEntry>> {
        return workEntryDao.getEntriesByDate(date).map { entities ->
            WorkEntryMapper.toDomainList(entities)
        }
    }
    
    override fun getEntriesByDateRange(
        startDate: LocalDate,
        endDate: LocalDate
    ): Flow<List<WorkEntry>> {
        return workEntryDao.getEntriesByDateRange(startDate, endDate).map { entities ->
            WorkEntryMapper.toDomainList(entities)
        }
    }
    
    override suspend fun getEntryById(id: Long): WorkEntry? {
        return workEntryDao.getEntryById(id)?.let { entity ->
            WorkEntryMapper.toDomain(entity)
        }
    }
    
    override suspend fun insertEntry(entry: WorkEntry): Long {
        return workEntryDao.insertEntry(WorkEntryMapper.toEntity(entry))
    }
    
    override suspend fun updateEntry(entry: WorkEntry) {
        workEntryDao.updateEntry(WorkEntryMapper.toEntity(entry))
    }
    
    override suspend fun deleteEntry(entry: WorkEntry) {
        workEntryDao.deleteEntry(WorkEntryMapper.toEntity(entry))
    }
    
    override suspend fun deleteEntryById(id: Long) {
        workEntryDao.deleteEntryById(id)
    }
    
    override fun getEntryCountByDate(date: LocalDate): Flow<Int> {
        return workEntryDao.getEntryCountByDate(date)
    }
    
    override fun getEntryCountByDateRange(
        startDate: LocalDate,
        endDate: LocalDate
    ): Flow<Int> {
        return workEntryDao.getEntryCountByDateRange(startDate, endDate)
    }
}

