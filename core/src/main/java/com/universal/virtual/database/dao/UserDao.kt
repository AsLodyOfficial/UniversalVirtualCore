package com.universal.virtual.database.dao

import androidx.room.*
import com.universal.virtual.database.entities.VirtualUserEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    @Query("SELECT * FROM virtual_users ORDER BY userId ASC")
    fun getAllUsers(): Flow<List<VirtualUserEntity>>

    @Query("SELECT * FROM virtual_users ORDER BY userId ASC")
    suspend fun getAllUsersList(): List<VirtualUserEntity>

    @Query("SELECT * FROM virtual_users WHERE userId = :userId LIMIT 1")
    suspend fun getUser(userId: Int): VirtualUserEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: VirtualUserEntity)

    @Query("DELETE FROM virtual_users WHERE userId = :userId")
    suspend fun deleteUser(userId: Int)

    @Query("SELECT COUNT(*) FROM virtual_users")
    fun getUserCount(): Flow<Int>
}