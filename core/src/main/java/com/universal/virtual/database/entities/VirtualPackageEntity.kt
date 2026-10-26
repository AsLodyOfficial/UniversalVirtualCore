package com.universal.virtual.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "virtual_packages")
data class VirtualPackageEntity(
    @PrimaryKey
    val packageName: String,
    val label: String,
    val versionName: String,
    val versionCode: Long,
    val minSdk: Int,
    val targetSdk: Int,
    val apkPath: String,
    val primaryAbi: String = "arm64-v8a",
    val isEnabled: Boolean = true,
    val installedAt: Long = System.currentTimeMillis()
)