package com.universal.virtual.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "diagnostic_events")
data class DiagnosticEventEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val subsystem: String,
    val level: String,
    val message: String,
    val isWarning: Boolean = false
)