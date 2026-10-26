package com.universal.virtual.database.dao

import androidx.room.*
import com.universal.virtual.database.entities.DiagnosticEventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DiagnosticDao {
    @Query("SELECT * FROM diagnostic_events ORDER BY timestamp DESC LIMIT 500")
    fun getAllEvents(): Flow<List<DiagnosticEventEntity>>

    @Query("SELECT * FROM diagnostic_events WHERE subsystem = :subsystem ORDER BY timestamp DESC LIMIT 200")
    fun getEventsBySubsystem(subsystem: String): Flow<List<DiagnosticEventEntity>>

    @Insert
    suspend fun insertEvent(event: DiagnosticEventEntity)

    @Query("DELETE FROM diagnostic_events")
    suspend fun clearEvents()
}