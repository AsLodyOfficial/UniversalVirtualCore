package com.universal.virtual.database.entities

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(
    tableName = "virtual_activities",
    primaryKeys = ["packageName", "name"],
    foreignKeys = [
        ForeignKey(
            entity = VirtualPackageEntity::class,
            parentColumns = ["packageName"],
            childColumns = ["packageName"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["packageName"])]
)
data class VirtualActivityEntity(
    val packageName: String,
    val name: String,
    val label: String? = null,
    val launchMode: Int = 0,
    val exported: Boolean = false,
    val enabled: Boolean = true,
    val permission: String? = null,
    val theme: String? = null,
    val screenOrientation: Int = -1,
    val configChanges: Int = 0,
    val intentActions: String = ""
)

@Entity(
    tableName = "virtual_services",
    primaryKeys = ["packageName", "name"],
    foreignKeys = [
        ForeignKey(
            entity = VirtualPackageEntity::class,
            parentColumns = ["packageName"],
            childColumns = ["packageName"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["packageName"])]
)
data class VirtualServiceEntity(
    val packageName: String,
    val name: String,
    val permission: String? = null,
    val exported: Boolean = false,
    val enabled: Boolean = true,
    val foregroundServiceType: Int = 0,
    val isolatedProcess: Boolean = false
)

@Entity(
    tableName = "virtual_providers",
    primaryKeys = ["packageName", "name"],
    foreignKeys = [
        ForeignKey(
            entity = VirtualPackageEntity::class,
            parentColumns = ["packageName"],
            childColumns = ["packageName"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["packageName"]), Index(value = ["authority"])]
)
data class VirtualProviderEntity(
    val packageName: String,
    val name: String,
    val authority: String,
    val exported: Boolean = false,
    val enabled: Boolean = true,
    val grantUriPermissions: Boolean = false,
    val readPermission: String? = null,
    val writePermission: String? = null,
    val multiprocess: Boolean = false
)

@Entity(
    tableName = "virtual_receivers",
    primaryKeys = ["packageName", "name"],
    foreignKeys = [
        ForeignKey(
            entity = VirtualPackageEntity::class,
            parentColumns = ["packageName"],
            childColumns = ["packageName"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["packageName"])]
)
data class VirtualReceiverEntity(
    val packageName: String,
    val name: String,
    val permission: String? = null,
    val exported: Boolean = false,
    val enabled: Boolean = true,
    val intentActions: String = ""
)

@Entity(
    tableName = "virtual_permissions",
    primaryKeys = ["packageName", "name"],
    foreignKeys = [
        ForeignKey(
            entity = VirtualPackageEntity::class,
            parentColumns = ["packageName"],
            childColumns = ["packageName"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["packageName"])]
)
data class VirtualPermissionEntity(
    val packageName: String,
    val name: String,
    val protectionLevel: Int = 0
)

@Entity(
    tableName = "virtual_features",
    primaryKeys = ["packageName", "name"],
    foreignKeys = [
        ForeignKey(
            entity = VirtualPackageEntity::class,
            parentColumns = ["packageName"],
            childColumns = ["packageName"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["packageName"])]
)
data class VirtualFeatureEntity(
    val packageName: String,
    val name: String,
    val required: Boolean = false
)

@Entity(
    tableName = "virtual_native_libraries",
    primaryKeys = ["packageName", "abi", "libraryName"],
    foreignKeys = [
        ForeignKey(
            entity = VirtualPackageEntity::class,
            parentColumns = ["packageName"],
            childColumns = ["packageName"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["packageName"])]
)
data class VirtualNativeLibraryEntity(
    val packageName: String,
    val abi: String,
    val libraryName: String,
    val fileSizeBytes: Long = 0L,
    val extractedPath: String? = null
)

data class VirtualPackageWithDetails(
    @Embedded val packageEntity: VirtualPackageEntity,
    @Relation(
        parentColumn = "packageName",
        entityColumn = "packageName"
    )
    val activities: List<VirtualActivityEntity> = emptyList(),
    @Relation(
        parentColumn = "packageName",
        entityColumn = "packageName"
    )
    val services: List<VirtualServiceEntity> = emptyList(),
    @Relation(
        parentColumn = "packageName",
        entityColumn = "packageName"
    )
    val providers: List<VirtualProviderEntity> = emptyList(),
    @Relation(
        parentColumn = "packageName",
        entityColumn = "packageName"
    )
    val receivers: List<VirtualReceiverEntity> = emptyList(),
    @Relation(
        parentColumn = "packageName",
        entityColumn = "packageName"
    )
    val permissions: List<VirtualPermissionEntity> = emptyList(),
    @Relation(
        parentColumn = "packageName",
        entityColumn = "packageName"
    )
    val features: List<VirtualFeatureEntity> = emptyList(),
    @Relation(
        parentColumn = "packageName",
        entityColumn = "packageName"
    )
    val nativeLibraries: List<VirtualNativeLibraryEntity> = emptyList()
)