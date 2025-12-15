package com.evergreen.trackora.data.local.converter

import androidx.room.TypeConverter
import com.evergreen.trackora.domain.model.Status
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Type converters for Room database to handle custom types.
 */
class Converters {
    
    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    
    @TypeConverter
    fun fromLocalDate(date: LocalDate?): String? {
        return date?.format(dateFormatter)
    }
    
    @TypeConverter
    fun toLocalDate(dateString: String?): LocalDate? {
        return dateString?.let { LocalDate.parse(it, dateFormatter) }
    }
    
    @TypeConverter
    fun fromStatus(status: Status?): String? {
        return status?.name
    }
    
    @TypeConverter
    fun toStatus(statusString: String?): Status? {
        return statusString?.let { Status.valueOf(it) }
    }
}

