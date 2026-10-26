package com.universal.virtual.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "virtual_processes")
data class VirtualProcessEntity(
    @PrimaryKey
    val virtualProcessId: Int,
    val hostPid: Int,
    val userId: Int,
    val packageName: String,
    val instanceId: Int,
    val processName: String,
    val state: String = "RUNNING",
    val memoryUsageMb: Long = 0,
    val cpuUsagePercent: Float = 0f,
    val startedAt: Long = System.currentTimeMillis()
)