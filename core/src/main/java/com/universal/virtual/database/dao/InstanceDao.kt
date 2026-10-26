package com.universal.virtual.database.dao

import androidx.room.*
import com.universal.virtual.database.entities.VirtualInstanceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface InstanceDao {
    @Query("SELECT * FROM virtual_instances ORDER BY lastLaunchedAt DESC")
    fun getAllInstances(): Flow<List<VirtualInstanceEntity>>

    @Query("SELECT * FROM virtual_instances ORDER BY lastLaunchedAt DESC")
    suspend fun getAllInstancesList(): List<VirtualInstanceEntity>

    @Query("SELECT * FROM virtual_instances WHERE userId = :userId")
    fun getInstancesForUser(userId: Int): Flow<List<VirtualInstanceEntity>>

    @Query("SELECT * FROM virtual_instances WHERE packageName = :packageName")
    fun getInstancesForPackage(packageName: String): Flow<List<VirtualInstanceEntity>>

    @Query("SELECT * FROM virtual_instances WHERE packageName = :packageName")
    suspend fun getInstancesForPackageSync(packageName: String): List<VirtualInstanceEntity>

    @Query("SELECT * FROM virtual_instances WHERE userId = :userId AND packageName = :packageName AND instanceId = :instanceId LIMIT 1")
    suspend fun getInstance(userId: Int, packageName: String, instanceId: Int): VirtualInstanceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInstance(instance: VirtualInstanceEntity)

    @Query("DELETE FROM virtual_instances WHERE userId = :userId AND packageName = :packageName AND instanceId = :instanceId")
    suspend fun deleteInstance(userId: Int, packageName: String, instanceId: Int)

    @Query("UPDATE virtual_instances SET state = :state, lastLaunchedAt = :timestamp WHERE userId = :userId AND packageName = :packageName AND instanceId = :instanceId")
    suspend fun updateState(userId: Int, packageName: String, instanceId: Int, state: String, timestamp: Long = System.currentTimeMillis())

    @Query("SELECT COUNT(*) FROM virtual_instances")
    fun getInstanceCount(): Flow<Int>
}