package com.evergreen.trackora.data.repository

import com.evergreen.trackora.data.local.dao.WorkEntryDao
import com.evergreen.trackora.data.local.entity.WorkEntry as WorkEntryEntity
import com.evergreen.trackora.data.mapper.WorkEntryMapper
import com.evergreen.trackora.domain.model.Status
import com.evergreen.trackora.domain.model.WorkEntry
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

class WorkEntryRepositoryImplTest {

    private lateinit var mockDao: WorkEntryDao
    private lateinit var repository: WorkEntryRepositoryImpl

    private val sampleDomainEntry = WorkEntry(
        id = 1L,
        title = "Title",
        description = "Desc",
        quantity = 2,
        status = Status.IN_PROGRESS,
        date = LocalDate.of(2024, 12, 1),
        customField1 = "c1",
        customField2 = "c2",
        customField3 = "c3"
    )

    private val sampleEntityEntry = WorkEntryMapper.toEntity(sampleDomainEntry)

    @Before
    fun setup() {
        mockDao = mockk()
        repository = WorkEntryRepositoryImpl(mockDao)
    }

    @Test
    fun `getAllEntries maps entities to domain`() = runTest {
        val entityList = listOf(sampleEntityEntry)
        every { mockDao.getAllEntries() } returns flowOf(entityList)

        val result = repository.getAllEntries().first()

        assertEquals(listOf(sampleDomainEntry), result)
        verify { mockDao.getAllEntries() }
    }

    @Test
    fun `getEntriesByDate maps entities to domain`() = runTest {
        val date = LocalDate.of(2024, 12, 1)
        val entityList = listOf(sampleEntityEntry)
        every { mockDao.getEntriesByDate(date) } returns flowOf(entityList)

        val result = repository.getEntriesByDate(date).first()

        assertEquals(listOf(sampleDomainEntry), result)
        verify { mockDao.getEntriesByDate(date) }
    }

    @Test
    fun `getEntriesByDateRange maps entities to domain`() = runTest {
        val startDate = LocalDate.of(2024, 12, 1)
        val endDate = LocalDate.of(2024, 12, 31)
        val entityList = listOf(sampleEntityEntry)
        every { mockDao.getEntriesByDateRange(startDate, endDate) } returns flowOf(entityList)

        val result = repository.getEntriesByDateRange(startDate, endDate).first()

        assertEquals(listOf(sampleDomainEntry), result)
        verify { mockDao.getEntriesByDateRange(startDate, endDate) }
    }

    @Test
    fun `getEntryById returns mapped domain model`() = runTest {
        coEvery { mockDao.getEntryById(sampleDomainEntry.id) } returns sampleEntityEntry

        val result = repository.getEntryById(sampleDomainEntry.id)

        assertEquals(sampleDomainEntry, result)
        coVerify { mockDao.getEntryById(sampleDomainEntry.id) }
    }

    @Test
    fun `getEntryById returns null when dao has no entry`() = runTest {
        coEvery { mockDao.getEntryById(99L) } returns null

        val result = repository.getEntryById(99L)

        assertNull(result)
        coVerify { mockDao.getEntryById(99L) }
    }

    @Test
    fun `insertEntry delegates to dao with mapped entity`() = runTest {
        val expectedId = 1L
        coEvery { mockDao.insertEntry(any()) } returns expectedId

        val result = repository.insertEntry(sampleDomainEntry)

        assertEquals(expectedId, result)
        coVerify { mockDao.insertEntry(sampleEntityEntry) }
    }

    @Test
    fun `updateEntry delegates to dao with mapped entity`() = runTest {
        coEvery { mockDao.updateEntry(any()) } returns Unit

        repository.updateEntry(sampleDomainEntry)

        coVerify { mockDao.updateEntry(sampleEntityEntry) }
    }

    @Test
    fun `deleteEntry delegates to dao with mapped entity`() = runTest {
        coEvery { mockDao.deleteEntry(any()) } returns Unit

        repository.deleteEntry(sampleDomainEntry)

        coVerify { mockDao.deleteEntry(sampleEntityEntry) }
    }

    @Test
    fun `deleteEntryById delegates to dao`() = runTest {
        val entryId = 1L
        coEvery { mockDao.deleteEntryById(entryId) } returns Unit

        repository.deleteEntryById(entryId)

        coVerify { mockDao.deleteEntryById(entryId) }
    }

    @Test
    fun `getEntryCountByDate returns count from dao`() = runTest {
        val date = LocalDate.of(2024, 12, 1)
        val expectedCount = 5
        every { mockDao.getEntryCountByDate(date) } returns flowOf(expectedCount)

        val result = repository.getEntryCountByDate(date).first()

        assertEquals(expectedCount, result)
        verify { mockDao.getEntryCountByDate(date) }
    }

    @Test
    fun `getEntryCountByDateRange returns count from dao`() = runTest {
        val startDate = LocalDate.of(2024, 12, 1)
        val endDate = LocalDate.of(2024, 12, 31)
        val expectedCount = 10
        every { mockDao.getEntryCountByDateRange(startDate, endDate) } returns flowOf(expectedCount)

        val result = repository.getEntryCountByDateRange(startDate, endDate).first()

        assertEquals(expectedCount, result)
        verify { mockDao.getEntryCountByDateRange(startDate, endDate) }
    }
}
