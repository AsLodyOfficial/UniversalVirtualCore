package com.universal.virtual.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "virtual_users")
data class VirtualUserEntity(
    @PrimaryKey
    val userId: Int,
    val displayName: String,
    val isEnabled: Boolean = true,
    val storageQuotaMb: Long = 4096,
    val createdAt: Long = System.currentTimeMillis()
)