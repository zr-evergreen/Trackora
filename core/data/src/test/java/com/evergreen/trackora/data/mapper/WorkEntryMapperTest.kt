package com.evergreen.trackora.data.mapper

import com.evergreen.trackora.domain.model.Status
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import com.evergreen.trackora.data.local.entity.WorkEntry as WorkEntryEntity
import com.evergreen.trackora.domain.model.WorkEntry as WorkEntryDomain

/**
 * Tests for the entity <-> domain mapper.
 *
 * The mapper is hand-written field-by-field, which is exactly the kind of code
 * that silently rots: adding a field to both models and forgetting one line
 * here compiles cleanly and loses the user's data on the way to or from the
 * database. The round-trip tests below fail the moment that happens, and the
 * nullable-field test covers the six optional columns where "lost" looks
 * identical to "never set".
 */
class WorkEntryMapperTest {

    private val fullyPopulatedDomain = WorkEntryDomain(
        id = 7L,
        title = "Hem trousers",
        description = "Charcoal, 4cm turn-up",
        quantity = 3,
        status = Status.COMPLETED,
        date = LocalDate.of(2026, 3, 21),
        customField1 = "Order 118",
        customField2 = "Ms. Ahmadi",
        customField3 = "Paid",
        photoUri = "content://media/external/images/media/42"
    )

    private val fullyPopulatedEntity = WorkEntryEntity(
        id = 7L,
        title = "Hem trousers",
        description = "Charcoal, 4cm turn-up",
        quantity = 3,
        status = Status.COMPLETED,
        date = LocalDate.of(2026, 3, 21),
        customField1 = "Order 118",
        customField2 = "Ms. Ahmadi",
        customField3 = "Paid",
        photoUri = "content://media/external/images/media/42"
    )

    // --- Round trips -------------------------------------------------------

    @Test
    fun `domain survives a round trip through the entity unchanged`() {
        val roundTripped = WorkEntryMapper.toDomain(
            WorkEntryMapper.toEntity(fullyPopulatedDomain)
        )

        assertEquals(fullyPopulatedDomain, roundTripped)
    }

    @Test
    fun `entity survives a round trip through the domain model unchanged`() {
        val roundTripped = WorkEntryMapper.toEntity(
            WorkEntryMapper.toDomain(fullyPopulatedEntity)
        )

        assertEquals(fullyPopulatedEntity, roundTripped)
    }

    // --- Field by field ----------------------------------------------------

    @Test
    fun `toDomain copies every field across`() {
        val domain = WorkEntryMapper.toDomain(fullyPopulatedEntity)

        assertEquals(7L, domain.id)
        assertEquals("Hem trousers", domain.title)
        assertEquals("Charcoal, 4cm turn-up", domain.description)
        assertEquals(3, domain.quantity)
        assertEquals(Status.COMPLETED, domain.status)
        assertEquals(LocalDate.of(2026, 3, 21), domain.date)
        assertEquals("Order 118", domain.customField1)
        assertEquals("Ms. Ahmadi", domain.customField2)
        assertEquals("Paid", domain.customField3)
        assertEquals("content://media/external/images/media/42", domain.photoUri)
    }

    @Test
    fun `toEntity copies every field across`() {
        val entity = WorkEntryMapper.toEntity(fullyPopulatedDomain)

        assertEquals(7L, entity.id)
        assertEquals("Hem trousers", entity.title)
        assertEquals("Charcoal, 4cm turn-up", entity.description)
        assertEquals(3, entity.quantity)
        assertEquals(Status.COMPLETED, entity.status)
        assertEquals(LocalDate.of(2026, 3, 21), entity.date)
        assertEquals("Order 118", entity.customField1)
        assertEquals("Ms. Ahmadi", entity.customField2)
        assertEquals("Paid", entity.customField3)
        assertEquals("content://media/external/images/media/42", entity.photoUri)
    }

    // --- Nullable fields ---------------------------------------------------

    @Test
    fun `null optional fields stay null in both directions`() {
        val minimalDomain = WorkEntryDomain(
            title = "Cut fabric",
            status = Status.IN_PROGRESS,
            date = LocalDate.of(2026, 1, 5)
        )

        val entity = WorkEntryMapper.toEntity(minimalDomain)
        assertNull(entity.description)
        assertNull(entity.quantity)
        assertNull(entity.customField1)
        assertNull(entity.customField2)
        assertNull(entity.customField3)
        assertNull(entity.photoUri)

        val backToDomain = WorkEntryMapper.toDomain(entity)
        assertNull(backToDomain.description)
        assertNull(backToDomain.quantity)
        assertNull(backToDomain.customField1)
        assertNull(backToDomain.customField2)
        assertNull(backToDomain.customField3)
        assertNull(backToDomain.photoUri)

        assertEquals(minimalDomain, backToDomain)
    }

    @Test
    fun `a partially filled entry keeps the set fields and the null ones apart`() {
        val partial = WorkEntryDomain(
            title = "Press seams",
            description = null,
            quantity = 10,
            status = Status.DELIVERED,
            date = LocalDate.of(2026, 2, 14),
            customField1 = "Order 42",
            customField2 = null,
            customField3 = "",
            photoUri = null
        )

        val roundTripped = WorkEntryMapper.toDomain(WorkEntryMapper.toEntity(partial))

        assertEquals(partial, roundTripped)
        assertNull(roundTripped.description)
        assertEquals(10, roundTripped.quantity)
        assertEquals("Order 42", roundTripped.customField1)
        assertNull(roundTripped.customField2)
        // An empty string is a distinct value from null and must not be collapsed.
        assertEquals("", roundTripped.customField3)
    }

    @Test
    fun `default id of zero is preserved so Room can autogenerate it`() {
        val unsaved = WorkEntryDomain(
            title = "New job",
            status = Status.IN_PROGRESS,
            date = LocalDate.of(2026, 5, 1)
        )

        // Room treats id == 0 as "assign me one". Mapping must not substitute
        // a placeholder here or every insert would collide on primary key.
        assertEquals(0L, WorkEntryMapper.toEntity(unsaved).id)
    }

    // --- Enum and list mapping ---------------------------------------------

    @Test
    fun `every status value maps in both directions`() {
        Status.entries.forEach { status ->
            val domain = WorkEntryDomain(
                title = "Entry",
                status = status,
                date = LocalDate.of(2026, 4, 1)
            )

            assertEquals(
                "status $status",
                status,
                WorkEntryMapper.toDomain(WorkEntryMapper.toEntity(domain)).status
            )
        }
    }

    @Test
    fun `toDomainList preserves order and size`() {
        val entities = listOf(
            fullyPopulatedEntity.copy(id = 1L, title = "First"),
            fullyPopulatedEntity.copy(id = 2L, title = "Second"),
            fullyPopulatedEntity.copy(id = 3L, title = "Third")
        )

        val domains = WorkEntryMapper.toDomainList(entities)

        assertEquals(3, domains.size)
        assertEquals(listOf("First", "Second", "Third"), domains.map { it.title })
        assertEquals(listOf(1L, 2L, 3L), domains.map { it.id })
    }

    @Test
    fun `toDomainList maps an empty list to an empty list`() {
        assertTrue(WorkEntryMapper.toDomainList(emptyList()).isEmpty())
    }
}
