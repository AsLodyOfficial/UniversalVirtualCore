package com.universal.virtual.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "crash_records")
data class CrashRecordEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val userId: Int,
    val packageName: String,
    val instanceId: Int,
    val virtualProcessId: Int,
    val exceptionClass: String,
    val message: String,
    val stackTrace: String
)