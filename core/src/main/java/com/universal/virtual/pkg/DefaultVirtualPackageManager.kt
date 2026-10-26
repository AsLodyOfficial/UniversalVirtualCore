package com.universal.virtual.pkg

import com.universal.virtual.api.interfaces.IVirtualPackageManager
import com.universal.virtual.api.interfaces.VirtualPackageMetadata
import com.universal.virtual.common.events.VirtualEvent
import com.universal.virtual.common.events.VirtualEventBus
import com.universal.virtual.common.events.VirtualEventType
import com.universal.virtual.common.identity.VirtualIdentity
import com.universal.virtual.common.identity.VirtualPackageId
import com.universal.virtual.common.identity.VirtualUserId
import com.universal.virtual.common.result.VirtualErrorCode
import com.universal.virtual.common.result.VirtualResult
import com.universal.virtual.parser.ApkParser
import com.universal.virtual.parser.ParsedActivity
import com.universal.virtual.parser.ParsedApk
import com.universal.virtual.parser.ParsedProvider
import com.universal.virtual.parser.ParsedService
import com.universal.virtual.repository.VirtualPackageRepository
import kotlinx.coroutines.runBlocking
import java.io.File

class DefaultVirtualPackageManager(
    private val storageManager: PackageStorageManager,
    private val repository: VirtualPackageRepository,
    private val apkParser: ApkParser = ApkParser(),
    private val eventBus: VirtualEventBus? = null
) : IVirtualPackageManager {

    override fun installPackage(apkPath: String): VirtualResult<VirtualPackageMetadata> = runBlocking {
        val apkFile = File(apkPath)
        if (!apkFile.exists() || !apkFile.isFile) {
            val err = VirtualResult.failure(
                code = VirtualErrorCode.NOT_FOUND,
                message = "APK file not found: $apkPath"
            )
            eventBus?.publishSync(
                VirtualEvent(
                    type = VirtualEventType.PACKAGE_INSTALL_FAILED,
                    description = "Failed to find APK file: $apkPath"
                )
            )
            return@runBlocking err
        }

        // 1. Parse APK Manifest & Metadata
        val parseResult = apkParser.parse(apkFile)
        if (parseResult.isFailure) {
            val err = parseResult.errorOrNull()!!
            eventBus?.publishSync(
                VirtualEvent(
                    type = VirtualEventType.PACKAGE_INSTALL_FAILED,
                    description = "APK parse error: ${err.message}"
                )
            )
            return@runBlocking VirtualResult.failure(err)
        }

        val parsedApk = parseResult.getOrNull()!!
        val packageId = VirtualPackageId.of(parsedApk.packageName)
        val isUpdate = repository.isPackageInstalled(packageId)

        // 2. Stage APK into private isolated storage
        val stageResult = storageManager.stageApk(apkFile, packageId)
        if (stageResult.isFailure) {
            val err = stageResult.errorOrNull()!!
            eventBus?.publishSync(
                VirtualEvent(
                    type = VirtualEventType.PACKAGE_INSTALL_FAILED,
                    identity = VirtualIdentity(VirtualUserId.SYSTEM, packageId),
                    description = "Storage staging failed: ${err.message}"
                )
            )
            return@runBlocking VirtualResult.failure(err)
        }

        val stagedFile = stageResult.getOrNull()!!

        // 3. Commit Staged APK
        val commitResult = storageManager.commitStagedApk(stagedFile, packageId)
        if (commitResult.isFailure) {
            storageManager.rollback(packageId)
            val err = commitResult.errorOrNull()!!
            eventBus?.publishSync(
                VirtualEvent(
                    type = VirtualEventType.PACKAGE_INSTALL_FAILED,
                    identity = VirtualIdentity(VirtualUserId.SYSTEM, packageId),
                    description = "Storage commit failed: ${err.message}"
                )
            )
            return@runBlocking VirtualResult.failure(err)
        }

        val committedApk = commitResult.getOrNull()!!

        // 4. Extract Native Libraries if available
        if (parsedApk.hasNativeLibraries) {
            storageManager.extractNativeLibraries(packageId, committedApk)
        }

        // 5. Persist to Room Virtual Package Database
        val dbResult = repository.installOrUpdatePackage(parsedApk, committedApk)
        if (dbResult.isFailure) {
            storageManager.rollback(packageId)
            val err = dbResult.errorOrNull()!!
            eventBus?.publishSync(
                VirtualEvent(
                    type = VirtualEventType.PACKAGE_INSTALL_FAILED,
                    identity = VirtualIdentity(VirtualUserId.SYSTEM, packageId),
                    description = "Database persistence failed: ${err.message}"
                )
            )
            return@runBlocking VirtualResult.failure(err)
        }

        val metadata = dbResult.getOrNull()!!

        // 6. Emit lifecycle event
        val eventType = if (isUpdate) VirtualEventType.PACKAGE_UPDATED else VirtualEventType.PACKAGE_INSTALLED
        eventBus?.publishSync(
            VirtualEvent(
                type = eventType,
                identity = VirtualIdentity(VirtualUserId.SYSTEM, packageId),
                description = if (isUpdate) "Package ${packageId.value} updated." else "Package ${packageId.value} installed."
            )
        )

        VirtualResult.success(metadata)
    }

    override fun uninstallPackage(packageId: VirtualPackageId): VirtualResult<Unit> = runBlocking {
        if (!repository.isPackageInstalled(packageId)) {
            return@runBlocking VirtualResult.failure(
                code = VirtualErrorCode.PACKAGE_NOT_FOUND,
                message = "Package not installed: ${packageId.value}"
            )
        }

        val dbResult = repository.uninstallPackage(packageId)
        if (dbResult.isFailure) {
            return@runBlocking dbResult
        }

        storageManager.deletePackageStorage(packageId)

        eventBus?.publishSync(
            VirtualEvent(
                type = VirtualEventType.PACKAGE_UNINSTALLED,
                identity = VirtualIdentity(VirtualUserId.SYSTEM, packageId),
                description = "Package ${packageId.value} uninstalled."
            )
        )

        VirtualResult.success(Unit)
    }

    override fun getPackage(packageId: VirtualPackageId): VirtualResult<VirtualPackageMetadata> = runBlocking {
        repository.getPackage(packageId)
    }

    override fun listPackages(): VirtualResult<List<VirtualPackageMetadata>> = runBlocking {
        repository.listPackages()
    }

    override fun isPackageInstalled(packageId: VirtualPackageId): Boolean = runBlocking {
        repository.isPackageInstalled(packageId)
    }

    fun getPackageDetails(packageId: VirtualPackageId): VirtualResult<ParsedApk> = runBlocking {
        repository.getPackageDetails(packageId)
    }

    fun resolveActivity(packageId: VirtualPackageId, activityName: String): VirtualResult<ParsedActivity> = runBlocking {
        repository.resolveActivity(packageId, activityName)
    }

    fun resolveService(packageId: VirtualPackageId, serviceName: String): VirtualResult<ParsedService> = runBlocking {
        repository.resolveService(packageId, serviceName)
    }

    fun resolveProvider(authority: String): VirtualResult<ParsedProvider> = runBlocking {
        repository.resolveProvider(authority)
    }
}