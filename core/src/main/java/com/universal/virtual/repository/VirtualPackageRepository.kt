package com.universal.virtual.repository

import com.universal.virtual.api.interfaces.VirtualPackageMetadata
import com.universal.virtual.common.identity.VirtualPackageId
import com.universal.virtual.common.result.VirtualErrorCode
import com.universal.virtual.common.result.VirtualResult
import com.universal.virtual.database.UniversalVirtualDatabase
import com.universal.virtual.database.entities.*
import com.universal.virtual.parser.*
import java.io.File

class VirtualPackageRepository(
    private val db: UniversalVirtualDatabase
) {

    suspend fun installOrUpdatePackage(
        parsedApk: ParsedApk,
        storedApkFile: File
    ): VirtualResult<VirtualPackageMetadata> {
        return try {
            val pkgEntity = VirtualPackageEntity(
                packageName = parsedApk.packageName,
                label = parsedApk.application.label,
                versionName = parsedApk.versionName,
                versionCode = parsedApk.versionCode,
                minSdk = parsedApk.minSdk,
                targetSdk = parsedApk.targetSdk,
                apkPath = storedApkFile.absolutePath,
                primaryAbi = parsedApk.primaryAbi,
                isEnabled = true,
                installedAt = System.currentTimeMillis()
            )

            val activities = parsedApk.activities.map {
                VirtualActivityEntity(
                    packageName = parsedApk.packageName,
                    name = it.name,
                    label = it.label,
                    launchMode = it.launchMode,
                    exported = it.exported,
                    enabled = it.enabled,
                    permission = it.permission,
                    theme = it.theme,
                    screenOrientation = it.screenOrientation,
                    configChanges = it.configChanges,
                    intentActions = it.intentActions.joinToString(",")
                )
            }

            val services = parsedApk.services.map {
                VirtualServiceEntity(
                    packageName = parsedApk.packageName,
                    name = it.name,
                    permission = it.permission,
                    exported = it.exported,
                    enabled = it.enabled,
                    foregroundServiceType = it.foregroundServiceType,
                    isolatedProcess = it.isolatedProcess
                )
            }

            val providers = parsedApk.providers.map {
                VirtualProviderEntity(
                    packageName = parsedApk.packageName,
                    name = it.name,
                    authority = it.authority,
                    exported = it.exported,
                    enabled = it.enabled,
                    grantUriPermissions = it.grantUriPermissions,
                    readPermission = it.readPermission,
                    writePermission = it.writePermission,
                    multiprocess = it.multiprocess
                )
            }

            val receivers = parsedApk.receivers.map {
                VirtualReceiverEntity(
                    packageName = parsedApk.packageName,
                    name = it.name,
                    permission = it.permission,
                    exported = it.exported,
                    enabled = it.enabled,
                    intentActions = it.intentActions.joinToString(",")
                )
            }

            val permissions = parsedApk.permissions.map {
                VirtualPermissionEntity(
                    packageName = parsedApk.packageName,
                    name = it.name,
                    protectionLevel = it.protectionLevel
                )
            }

            val features = parsedApk.features.map {
                VirtualFeatureEntity(
                    packageName = parsedApk.packageName,
                    name = it.name,
                    required = it.required
                )
            }

            val nativeLibs = parsedApk.nativeLibraries.map {
                VirtualNativeLibraryEntity(
                    packageName = parsedApk.packageName,
                    abi = it.abi,
                    libraryName = it.libraryName,
                    fileSizeBytes = it.fileSizeBytes
                )
            }

            db.packageDao().installOrUpdatePackageAtomic(
                pkg = pkgEntity,
                activities = activities,
                services = services,
                providers = providers,
                receivers = receivers,
                permissions = permissions,
                features = features,
                nativeLibs = nativeLibs
            )

            val metadata = VirtualPackageMetadata(
                packageId = VirtualPackageId.of(parsedApk.packageName),
                versionName = parsedApk.versionName,
                versionCode = parsedApk.versionCode,
                minSdk = parsedApk.minSdk,
                targetSdk = parsedApk.targetSdk,
                apkFile = storedApkFile,
                isEnabled = true
            )

            VirtualResult.success(metadata)
        } catch (e: Exception) {
            VirtualResult.failure(
                code = VirtualErrorCode.INTERNAL_ERROR,
                message = "Database transaction failed for package " + parsedApk.packageName + ": " + e.message,
                cause = e
            )
        }
    }

    suspend fun uninstallPackage(packageId: VirtualPackageId): VirtualResult<Unit> {
        return try {
            val existing = db.packageDao().getPackage(packageId.value)
            if (existing == null) {
                return VirtualResult.failure(
                    code = VirtualErrorCode.PACKAGE_NOT_FOUND,
                    message = "Package not found: " + packageId.value
                )
            }
            db.packageDao().deletePackage(packageId.value)
            VirtualResult.success(Unit)
        } catch (e: Exception) {
            VirtualResult.failure(
                code = VirtualErrorCode.INTERNAL_ERROR,
                message = "Failed to uninstall package " + packageId.value + ": " + e.message,
                cause = e
            )
        }
    }

    suspend fun getPackage(packageId: VirtualPackageId): VirtualResult<VirtualPackageMetadata> {
        return try {
            val entity = db.packageDao().getPackage(packageId.value)
                ?: return VirtualResult.failure(
                    code = VirtualErrorCode.PACKAGE_NOT_FOUND,
                    message = "Package not found: " + packageId.value
                )

            val metadata = VirtualPackageMetadata(
                packageId = VirtualPackageId.of(entity.packageName),
                versionName = entity.versionName,
                versionCode = entity.versionCode,
                minSdk = entity.minSdk,
                targetSdk = entity.targetSdk,
                apkFile = File(entity.apkPath),
                isEnabled = entity.isEnabled
            )
            VirtualResult.success(metadata)
        } catch (e: Exception) {
            VirtualResult.failure(
                code = VirtualErrorCode.INTERNAL_ERROR,
                message = "Error querying package " + packageId.value + ": " + e.message,
                cause = e
            )
        }
    }

    suspend fun getPackageDetails(packageId: VirtualPackageId): VirtualResult<ParsedApk> {
        return try {
            val details = db.packageDao().getPackageWithDetails(packageId.value)
                ?: return VirtualResult.failure(
                    code = VirtualErrorCode.PACKAGE_NOT_FOUND,
                    message = "Package details not found: " + packageId.value
                )

            val pkg = details.packageEntity
            val apkFile = File(pkg.apkPath)

            val parsedApk = ParsedApk(
                packageName = pkg.packageName,
                versionName = pkg.versionName,
                versionCode = pkg.versionCode,
                minSdk = pkg.minSdk,
                targetSdk = pkg.targetSdk,
                originalApkFile = apkFile,
                originalApkSize = if (apkFile.exists()) apkFile.length() else 0L,
                application = ParsedApplication(
                    label = pkg.label,
                    isEnabled = pkg.isEnabled
                ),
                activities = details.activities.map {
                    ParsedActivity(
                        name = it.name,
                        label = it.label,
                        launchMode = it.launchMode,
                        exported = it.exported,
                        enabled = it.enabled,
                        permission = it.permission,
                        theme = it.theme,
                        screenOrientation = it.screenOrientation,
                        configChanges = it.configChanges,
                        intentActions = if (it.intentActions.isBlank()) emptyList() else it.intentActions.split(",")
                    )
                },
                services = details.services.map {
                    ParsedService(
                        name = it.name,
                        permission = it.permission,
                        exported = it.exported,
                        enabled = it.enabled,
                        foregroundServiceType = it.foregroundServiceType,
                        isolatedProcess = it.isolatedProcess
                    )
                },
                providers = details.providers.map {
                    ParsedProvider(
                        name = it.name,
                        authority = it.authority,
                        exported = it.exported,
                        enabled = it.enabled,
                        grantUriPermissions = it.grantUriPermissions,
                        readPermission = it.readPermission,
                        writePermission = it.writePermission,
                        multiprocess = it.multiprocess
                    )
                },
                receivers = details.receivers.map {
                    ParsedReceiver(
                        name = it.name,
                        permission = it.permission,
                        exported = it.exported,
                        enabled = it.enabled,
                        intentActions = if (it.intentActions.isBlank()) emptyList() else it.intentActions.split(",")
                    )
                },
                permissions = details.permissions.map {
                    ParsedPermission(name = it.name, protectionLevel = it.protectionLevel)
                },
                features = details.features.map {
                    ParsedFeature(name = it.name, required = it.required)
                },
                nativeLibraries = details.nativeLibraries.map {
                    ParsedNativeLibrary(
                        abi = it.abi,
                        libraryName = it.libraryName,
                        fileSizeBytes = it.fileSizeBytes
                    )
                }
            )

            VirtualResult.success(parsedApk)
        } catch (e: Exception) {
            VirtualResult.failure(
                code = VirtualErrorCode.INTERNAL_ERROR,
                message = "Failed to get package details for " + packageId.value + ": " + e.message,
                cause = e
            )
        }
    }

    suspend fun listPackages(): VirtualResult<List<VirtualPackageMetadata>> {
        return try {
            val entities = db.packageDao().getAllPackagesList()
            val list = entities.map { entity ->
                VirtualPackageMetadata(
                    packageId = VirtualPackageId.of(entity.packageName),
                    versionName = entity.versionName,
                    versionCode = entity.versionCode,
                    minSdk = entity.minSdk,
                    targetSdk = entity.targetSdk,
                    apkFile = File(entity.apkPath),
                    isEnabled = entity.isEnabled
                )
            }
            VirtualResult.success(list)
        } catch (e: Exception) {
            VirtualResult.failure(
                code = VirtualErrorCode.INTERNAL_ERROR,
                message = "Failed to list virtual packages: " + e.message,
                cause = e
            )
        }
    }

    suspend fun isPackageInstalled(packageId: VirtualPackageId): Boolean {
        return try {
            db.packageDao().getPackage(packageId.value) != null
        } catch (_: Exception) {
            false
        }
    }

    suspend fun resolveActivity(packageId: VirtualPackageId, activityName: String): VirtualResult<ParsedActivity> {
        return try {
            val entity = db.packageDao().getActivity(packageId.value, activityName)
                ?: return VirtualResult.failure(
                    code = VirtualErrorCode.COMPONENT_NOT_FOUND,
                    message = "Activity not found: " + activityName + " in " + packageId.value
                )
            VirtualResult.success(
                ParsedActivity(
                    name = entity.name,
                    label = entity.label,
                    launchMode = entity.launchMode,
                    exported = entity.exported,
                    enabled = entity.enabled,
                    permission = entity.permission,
                    theme = entity.theme,
                    screenOrientation = entity.screenOrientation,
                    configChanges = entity.configChanges,
                    intentActions = if (entity.intentActions.isBlank()) emptyList() else entity.intentActions.split(",")
                )
            )
        } catch (e: Exception) {
            VirtualResult.failure(
                code = VirtualErrorCode.INTERNAL_ERROR,
                message = "Error resolving activity: " + e.message,
                cause = e
            )
        }
    }

    suspend fun resolveService(packageId: VirtualPackageId, serviceName: String): VirtualResult<ParsedService> {
        return try {
            val entity = db.packageDao().getService(packageId.value, serviceName)
                ?: return VirtualResult.failure(
                    code = VirtualErrorCode.COMPONENT_NOT_FOUND,
                    message = "Service not found: " + serviceName + " in " + packageId.value
                )
            VirtualResult.success(
                ParsedService(
                    name = entity.name,
                    permission = entity.permission,
                    exported = entity.exported,
                    enabled = entity.enabled,
                    foregroundServiceType = entity.foregroundServiceType,
                    isolatedProcess = entity.isolatedProcess
                )
            )
        } catch (e: Exception) {
            VirtualResult.failure(
                code = VirtualErrorCode.INTERNAL_ERROR,
                message = "Error resolving service: " + e.message,
                cause = e
            )
        }
    }

    suspend fun resolveProvider(authority: String): VirtualResult<ParsedProvider> {
        return try {
            val entity = db.packageDao().getProviderByAuthority(authority)
                ?: return VirtualResult.failure(
                    code = VirtualErrorCode.COMPONENT_NOT_FOUND,
                    message = "Provider not found for authority: " + authority
                )
            VirtualResult.success(
                ParsedProvider(
                    name = entity.name,
                    authority = entity.authority,
                    exported = entity.exported,
                    enabled = entity.enabled,
                    grantUriPermissions = entity.grantUriPermissions,
                    readPermission = entity.readPermission,
                    writePermission = entity.writePermission,
                    multiprocess = entity.multiprocess
                )
            )
        } catch (e: Exception) {
            VirtualResult.failure(
                code = VirtualErrorCode.INTERNAL_ERROR,
                message = "Error resolving provider: " + e.message,
                cause = e
            )
        }
    }
}