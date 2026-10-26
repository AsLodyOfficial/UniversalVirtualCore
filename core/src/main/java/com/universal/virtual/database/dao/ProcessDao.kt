package com.universal.virtual.database.dao

import androidx.room.*
import com.universal.virtual.database.entities.VirtualProcessEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProcessDao {
    @Query("SELECT * FROM virtual_processes ORDER BY startedAt DESC")
    fun getAllProcesses(): Flow<List<VirtualProcessEntity>>

    @Query("SELECT * FROM virtual_processes WHERE virtualProcessId = :processId LIMIT 1")
    suspend fun getProcess(processId: Int): VirtualProcessEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProcess(process: VirtualProcessEntity)

    @Query("DELETE FROM virtual_processes WHERE virtualProcessId = :processId")
    suspend fun deleteProcess(processId: Int)

    @Query("DELETE FROM virtual_processes")
    suspend fun clearAll()

    @Query("SELECT COUNT(*) FROM virtual_processes WHERE state = 'RUNNING'")
    fun getRunningProcessCount(): Flow<Int>
}