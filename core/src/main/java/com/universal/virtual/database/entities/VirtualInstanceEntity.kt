package com.universal.virtual.database.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "virtual_instances",
    primaryKeys = ["userId", "packageName", "instanceId"],
    foreignKeys = [
        ForeignKey(
            entity = VirtualUserEntity::class,
            parentColumns = ["userId"],
            childColumns = ["userId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = VirtualPackageEntity::class,
            parentColumns = ["packageName"],
            childColumns = ["packageName"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["userId"]),
        Index(value = ["packageName"])
    ]
)
data class VirtualInstanceEntity(
    val userId: Int,
    val packageName: String,
    val instanceId: Int,
    val displayName: String,
    val state: String = "STOPPED",
    val storagePath: String,
    val storageUsageBytes: Long = 0,
    val lastLaunchedAt: Long = 0,
    val createdAt: Long = System.currentTimeMillis()
)