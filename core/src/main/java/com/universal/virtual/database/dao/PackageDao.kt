package com.universal.virtual.database.dao

import androidx.room.*
import com.universal.virtual.database.entities.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PackageDao {
    @Query("SELECT * FROM virtual_packages ORDER BY installedAt DESC")
    fun getAllPackages(): Flow<List<VirtualPackageEntity>>

    @Query("SELECT * FROM virtual_packages ORDER BY installedAt DESC")
    suspend fun getAllPackagesList(): List<VirtualPackageEntity>

    @Query("SELECT * FROM virtual_packages WHERE packageName = :packageName LIMIT 1")
    suspend fun getPackage(packageName: String): VirtualPackageEntity?

    @Transaction
    @Query("SELECT * FROM virtual_packages WHERE packageName = :packageName LIMIT 1")
    suspend fun getPackageWithDetails(packageName: String): VirtualPackageWithDetails?

    @Transaction
    @Query("SELECT * FROM virtual_packages ORDER BY installedAt DESC")
    suspend fun getAllPackagesWithDetails(): List<VirtualPackageWithDetails>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPackage(pkg: VirtualPackageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertActivities(activities: List<VirtualActivityEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertServices(services: List<VirtualServiceEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProviders(providers: List<VirtualProviderEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReceivers(receivers: List<VirtualReceiverEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPermissions(permissions: List<VirtualPermissionEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFeatures(features: List<VirtualFeatureEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNativeLibraries(libs: List<VirtualNativeLibraryEntity>)

    @Query("DELETE FROM virtual_packages WHERE packageName = :packageName")
    suspend fun deletePackage(packageName: String)

    @Query("DELETE FROM virtual_activities WHERE packageName = :packageName")
    suspend fun deleteActivities(packageName: String)

    @Query("DELETE FROM virtual_services WHERE packageName = :packageName")
    suspend fun deleteServices(packageName: String)

    @Query("DELETE FROM virtual_providers WHERE packageName = :packageName")
    suspend fun deleteProviders(packageName: String)

    @Query("DELETE FROM virtual_receivers WHERE packageName = :packageName")
    suspend fun deleteReceivers(packageName: String)

    @Query("DELETE FROM virtual_permissions WHERE packageName = :packageName")
    suspend fun deletePermissions(packageName: String)

    @Query("DELETE FROM virtual_features WHERE packageName = :packageName")
    suspend fun deleteFeatures(packageName: String)

    @Query("DELETE FROM virtual_native_libraries WHERE packageName = :packageName")
    suspend fun deleteNativeLibraries(packageName: String)

    @Query("SELECT COUNT(*) FROM virtual_packages")
    fun getPackageCount(): Flow<Int>

    @Query("SELECT * FROM virtual_activities WHERE packageName = :packageName AND name = :activityName LIMIT 1")
    suspend fun getActivity(packageName: String, activityName: String): VirtualActivityEntity?

    @Query("SELECT * FROM virtual_activities WHERE intentActions LIKE '%' || :action || '%'")
    suspend fun findActivitiesByAction(action: String): List<VirtualActivityEntity>

    @Query("SELECT * FROM virtual_services WHERE packageName = :packageName AND name = :serviceName LIMIT 1")
    suspend fun getService(packageName: String, serviceName: String): VirtualServiceEntity?

    @Query("SELECT * FROM virtual_providers WHERE authority = :authority LIMIT 1")
    suspend fun getProviderByAuthority(authority: String): VirtualProviderEntity?

    @Transaction
    suspend fun installOrUpdatePackageAtomic(
        pkg: VirtualPackageEntity,
        activities: List<VirtualActivityEntity>,
        services: List<VirtualServiceEntity>,
        providers: List<VirtualProviderEntity>,
        receivers: List<VirtualReceiverEntity>,
        permissions: List<VirtualPermissionEntity>,
        features: List<VirtualFeatureEntity>,
        nativeLibs: List<VirtualNativeLibraryEntity>
    ) {
        insertPackage(pkg)
        deleteActivities(pkg.packageName)
        deleteServices(pkg.packageName)
        deleteProviders(pkg.packageName)
        deleteReceivers(pkg.packageName)
        deletePermissions(pkg.packageName)
        deleteFeatures(pkg.packageName)
        deleteNativeLibraries(pkg.packageName)

        if (activities.isNotEmpty()) insertActivities(activities)
        if (services.isNotEmpty()) insertServices(services)
        if (providers.isNotEmpty()) insertProviders(providers)
        if (receivers.isNotEmpty()) insertReceivers(receivers)
        if (permissions.isNotEmpty()) insertPermissions(permissions)
        if (features.isNotEmpty()) insertFeatures(features)
        if (nativeLibs.isNotEmpty()) insertNativeLibraries(nativeLibs)
    }
}