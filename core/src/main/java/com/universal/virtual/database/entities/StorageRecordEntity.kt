package com.universal.virtual.database.entities

import androidx.room.Entity

@Entity(
    tableName = "storage_records",
    primaryKeys = ["userId", "packageName", "instanceId"]
)
data class StorageRecordEntity(
    val userId: Int,
    val packageName: String,
    val instanceId: Int,
    val dataBytes: Long = 0,
    val cacheBytes: Long = 0,
    val dbBytes: Long = 0,
    val filesBytes: Long = 0,
    val totalBytes: Long = 0,
    val lastCalculatedAt: Long = System.currentTimeMillis()
)