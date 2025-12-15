package com.evergreen.trackora.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.evergreen.trackora.data.local.converter.Converters
import com.evergreen.trackora.domain.model.Status
import java.time.LocalDate

/**
 * Room entity representing a work entry in the database.
 * 
 * A WorkEntry represents one unit of work done on a specific date.
 * Each entry has a title, optional quantity, and a status.
 */
@Entity(tableName = "work_entries")
@TypeConverters(Converters::class)
data class WorkEntry(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    val title: String,
    
    val description: String? = null,
    
    val quantity: Int? = null,
    
    val status: Status,
    
    val date: LocalDate,
    
    val customField1: String? = null,
    
    val customField2: String? = null,
    
    val customField3: String? = null
)

