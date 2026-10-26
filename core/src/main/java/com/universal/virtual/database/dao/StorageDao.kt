package com.universal.virtual.database.dao

import androidx.room.*
import com.universal.virtual.database.entities.StorageRecordEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StorageDao {
    @Query("SELECT * FROM storage_records")
    fun getAllRecords(): Flow<List<StorageRecordEntity>>

    @Query("SELECT * FROM storage_records WHERE userId = :userId AND packageName = :packageName AND instanceId = :instanceId LIMIT 1")
    suspend fun getRecord(userId: Int, packageName: String, instanceId: Int): StorageRecordEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(record: StorageRecordEntity)
}