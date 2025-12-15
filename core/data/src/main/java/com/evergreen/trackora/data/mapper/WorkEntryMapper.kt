package com.evergreen.trackora.data.mapper

import com.evergreen.trackora.data.local.entity.WorkEntry as WorkEntryEntity
import com.evergreen.trackora.domain.model.WorkEntry as WorkEntryDomain

/**
 * Mapper to convert between data layer entities and domain models.
 */
object WorkEntryMapper {
    
    fun toDomain(entity: WorkEntryEntity): WorkEntryDomain {
        return WorkEntryDomain(
            id = entity.id,
            title = entity.title,
            description = entity.description,
            quantity = entity.quantity,
            status = entity.status,
            date = entity.date,
            customField1 = entity.customField1,
            customField2 = entity.customField2,
            customField3 = entity.customField3
        )
    }
    
    fun toEntity(domain: WorkEntryDomain): WorkEntryEntity {
        return WorkEntryEntity(
            id = domain.id,
            title = domain.title,
            description = domain.description,
            quantity = domain.quantity,
            status = domain.status,
            date = domain.date,
            customField1 = domain.customField1,
            customField2 = domain.customField2,
            customField3 = domain.customField3
        )
    }
    
    fun toDomainList(entities: List<WorkEntryEntity>): List<WorkEntryDomain> {
        return entities.map { toDomain(it) }
    }
}

