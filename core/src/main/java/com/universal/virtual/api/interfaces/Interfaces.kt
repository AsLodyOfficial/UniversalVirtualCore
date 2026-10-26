package com.universal.virtual.api.interfaces

import com.universal.virtual.common.capability.VirtualCapabilities
import com.universal.virtual.common.identity.*
import com.universal.virtual.common.result.VirtualResult
import com.universal.virtual.diagnostics.DiagnosticSnapshot
import com.universal.virtual.diagnostics.SystemMetrics
import com.universal.virtual.security.SecurityPolicy
import com.universal.virtual.security.SecurityValidationResult
import java.io.File
import java.io.Serializable

data class VirtualPackageMetadata(
    val packageId: VirtualPackageId,
    val versionName: String,
    val versionCode: Long,
    val minSdk: Int,
    val targetSdk: Int,
    val apkFile: File,
    val isEnabled: Boolean = true
) : Serializable

interface IVirtualPackageManager {
    fun installPackage(apkPath: String): VirtualResult<VirtualPackageMetadata>
    fun uninstallPackage(packageId: VirtualPackageId): VirtualResult<Unit>
    fun getPackage(packageId: VirtualPackageId): VirtualResult<VirtualPackageMetadata>
    fun listPackages(): VirtualResult<List<VirtualPackageMetadata>>
    fun isPackageInstalled(packageId: VirtualPackageId): Boolean
}

data class VirtualUserMetadata(
    val userId: VirtualUserId,
    val displayName: String,
    val createdAtMs: Long = System.currentTimeMillis(),
    val isEnabled: Boolean = true
) : Serializable

interface IVirtualUserManager {
    fun createUser(userId: VirtualUserId, displayName: String): VirtualResult<VirtualUserMetadata>
    fun deleteUser(userId: VirtualUserId): VirtualResult<Unit>
    fun getUser(userId: VirtualUserId): VirtualResult<VirtualUserMetadata>
    fun listUsers(): VirtualResult<List<VirtualUserMetadata>>
    fun isUserExists(userId: VirtualUserId): Boolean
}

data class VirtualInstanceMetadata(
    val identity: VirtualIdentity,
    val displayName: String,
    val storageRoot: File,
    val createdAtMs: Long = System.currentTimeMillis()
) : Serializable

interface IVirtualInstanceManager {
    fun createInstance(identity: VirtualIdentity, displayName: String): VirtualResult<VirtualInstanceMetadata>
    fun deleteInstance(identity: VirtualIdentity): VirtualResult<Unit>
    fun getInstance(identity: VirtualIdentity): VirtualResult<VirtualInstanceMetadata>
    fun listInstances(userId: VirtualUserId? = null): VirtualResult<List<VirtualInstanceMetadata>>
}

interface IVirtualProcessManager {
    fun startProcess(identity: VirtualIdentity): VirtualResult<VirtualProcessId>
    fun stopProcess(processId: VirtualProcessId): VirtualResult<Unit>
    fun killProcess(processId: VirtualProcessId): VirtualResult<Unit>
    fun listProcesses(): VirtualResult<List<VirtualProcessId>>
}

interface IVirtualActivityManager {
    fun startActivity(identity: VirtualIdentity, componentId: VirtualComponentId, extras: Map<String, Any> = emptyMap()): VirtualResult<String>
    fun finishActivity(activityToken: String): VirtualResult<Unit>
}

interface IVirtualServiceManager {
    fun startService(identity: VirtualIdentity, componentId: VirtualComponentId): VirtualResult<Unit>
    fun stopService(identity: VirtualIdentity, componentId: VirtualComponentId): VirtualResult<Unit>
}

interface IVirtualProviderManager {
    fun registerProvider(identity: VirtualIdentity, authority: String, componentId: VirtualComponentId): VirtualResult<Unit>
    fun unregisterProvider(authority: String): VirtualResult<Unit>
}

interface IVirtualBroadcastManager {
    fun sendBroadcast(identity: VirtualIdentity, action: String, extras: Map<String, Any> = emptyMap()): VirtualResult<Int>
}

interface IVirtualStorageManager {
    fun getPackageStorage(packageId: VirtualPackageId): File
    fun getInstanceStorage(identity: VirtualIdentity): File
    fun validatePath(identity: VirtualIdentity, targetPath: File): Boolean
}

interface IVirtualResourceManager {
    fun loadString(packageId: VirtualPackageId, resId: Int): VirtualResult<String>
}

interface IVirtualClassLoaderManager {
    fun getClassLoader(identity: VirtualIdentity): VirtualResult<ClassLoader>
    fun releaseClassLoader(identity: VirtualIdentity): VirtualResult<Unit>
}

interface IVirtualSecurityManager {
    val policy: SecurityPolicy
    fun validateUserAccess(caller: VirtualUserId, target: VirtualUserId): SecurityValidationResult
    fun validatePackageAccess(caller: VirtualPackageId, target: VirtualPackageId): SecurityValidationResult
    fun validateInstanceAccess(caller: VirtualIdentity, target: VirtualIdentity): SecurityValidationResult
    fun validateProcessAccess(caller: VirtualIdentity, target: VirtualIdentity): SecurityValidationResult
    fun validateIdentity(caller: VirtualIdentity, target: VirtualIdentity): SecurityValidationResult
    fun validatePathAccess(caller: VirtualIdentity, targetFile: File): SecurityValidationResult
}

interface IVirtualDiagnostics {
    fun captureSnapshot(): DiagnosticSnapshot
    fun getSystemMetrics(): SystemMetrics
    fun logDiagnosticEvent(tag: String, message: String, isWarning: Boolean = false)
}

interface IVirtualizationBackend {
    val name: String
    fun initialize(): VirtualResult<Unit>
    fun shutdown(): VirtualResult<Unit>
    fun capabilities(): VirtualCapabilities
    fun diagnostics(): DiagnosticSnapshot
}
