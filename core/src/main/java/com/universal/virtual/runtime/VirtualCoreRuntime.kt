package com.universal.virtual.runtime

import android.content.Context
import com.universal.virtual.api.CoreState
import com.universal.virtual.api.CoreVersion
import com.universal.virtual.api.UniversalVirtualCore
import com.universal.virtual.api.VirtualCoreConfig
import com.universal.virtual.api.interfaces.IVirtualPackageManager
import com.universal.virtual.api.interfaces.IVirtualSecurityManager
import com.universal.virtual.backend.FrameworkContainerBackend
import com.universal.virtual.backend.VirtualizationBackend
import com.universal.virtual.common.events.*
import com.universal.virtual.common.result.VirtualErrorCode
import com.universal.virtual.common.result.VirtualResult
import com.universal.virtual.database.UniversalVirtualDatabase
import com.universal.virtual.diagnostics.VirtualDiagnostics
import com.universal.virtual.parser.ApkParser
import com.universal.virtual.pkg.DefaultVirtualPackageManager
import com.universal.virtual.pkg.PackageStorageManager
import com.universal.virtual.repository.VirtualPackageRepository
import com.universal.virtual.security.VirtualSecurityManager
import java.io.File
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class VirtualCoreRuntime private constructor(
    private val backendInstance: VirtualizationBackend = FrameworkContainerBackend(),
    private val securityManagerInstance: IVirtualSecurityManager = VirtualSecurityManager()
) : UniversalVirtualCore {

    private val lifecycleLock = ReentrantLock()

    @Volatile
    private var currentState: CoreState = CoreState.UNINITIALIZED

    @Volatile
    private var activeConfig: VirtualCoreConfig? = null

    private val eventBusInstance: VirtualEventBus = VirtualEventBusImpl()
    private var initTimestampMs: Long = 0L

    private var packageManagerInstance: IVirtualPackageManager? = null

    private val diagnosticsInstance: VirtualDiagnostics = VirtualDiagnostics(
        stateSupplier = { currentState },
        versionSupplier = { CoreVersion.CURRENT },
        backendNameSupplier = { backendInstance.name },
        capabilitiesSupplier = { backendInstance.capabilities() },
        uptimeSupplier = { initTimestampMs }
    )

    override fun state(): CoreState = currentState

    override fun version(): CoreVersion = CoreVersion.CURRENT

    override fun config(): VirtualCoreConfig? = activeConfig

    override fun backend(): VirtualizationBackend = backendInstance

    override fun diagnostics(): VirtualDiagnostics = diagnosticsInstance

    override fun eventBus(): VirtualEventBus = eventBusInstance

    override fun securityManager(): IVirtualSecurityManager = securityManagerInstance

    override fun packageManager(): IVirtualPackageManager {
        return packageManagerInstance
            ?: throw IllegalStateException("PackageManager is unavailable when core is not initialized.")
    }

    override fun initialize(context: Context, config: VirtualCoreConfig): VirtualResult<Unit> {
        lifecycleLock.withLock {
            if (currentState == CoreState.READY) {
                return VirtualResult.failure(
                    code = VirtualErrorCode.ALREADY_INITIALIZED,
                    message = "UniversalVirtualCore is already running in state READY."
                )
            }
            if (currentState == CoreState.INITIALIZING) {
                return VirtualResult.failure(
                    code = VirtualErrorCode.ALREADY_INITIALIZED,
                    message = "Initialization is already in progress."
                )
            }
            if (currentState == CoreState.STOPPING) {
                return VirtualResult.failure(
                    code = VirtualErrorCode.INVALID_STATE_TRANSITION,
                    message = "Cannot initialize while core is shutting down."
                )
            }

            val configValidation = config.validate()
            if (configValidation.isFailure) {
                currentState = CoreState.FAILED
                eventBusInstance.publishSync(
                    VirtualEvent(
                        type = VirtualEventType.CORE_FAILED,
                        description = "Config validation failed: " + (configValidation.errorOrNull()?.message ?: "")
                    )
                )
                return configValidation
            }

            currentState = CoreState.INITIALIZING
            activeConfig = config
            initTimestampMs = System.currentTimeMillis()

            eventBusInstance.publishSync(
                VirtualEvent(
                    type = VirtualEventType.CORE_INITIALIZING,
                    description = "Initializing UniversalVirtualCore with backend " + backendInstance.name
                )
            )

            val backendResult = backendInstance.initialize()
            if (backendResult.isFailure) {
                currentState = CoreState.FAILED
                eventBusInstance.publishSync(
                    VirtualEvent(
                        type = VirtualEventType.CORE_FAILED,
                        description = "Backend initialization failed: " + (backendResult.errorOrNull()?.message ?: "")
                    )
                )
                return backendResult
            }

            try {
                // Initialize database and Package Manager
                val db = try {
                    UniversalVirtualDatabase.getInstance(context)
                } catch (_: Throwable) {
                    null
                }
                val baseDir = try {
                    context.filesDir ?: config.storageRoot
                } catch (_: Throwable) {
                    config.storageRoot
                }
                val storageManager = PackageStorageManager(baseDir)
                if (db != null) {
                    val pkgRepo = VirtualPackageRepository(db)
                    val parser = ApkParser(context)
                    packageManagerInstance = DefaultVirtualPackageManager(
                        storageManager = storageManager,
                        repository = pkgRepo,
                        apkParser = parser,
                        eventBus = eventBusInstance
                    )
                }
            } catch (e: Exception) {
                currentState = CoreState.FAILED
                eventBusInstance.publishSync(
                    VirtualEvent(
                        type = VirtualEventType.CORE_FAILED,
                        description = "Package subsystem initialization failed: " + e.message
                    )
                )
                return VirtualResult.failure(
                    code = VirtualErrorCode.INTERNAL_ERROR,
                    message = "Failed to initialize virtual package subsystem: " + e.message,
                    cause = e
                )
            }

            currentState = CoreState.READY
            UniversalVirtualCore.registerInstance(this)

            eventBusInstance.publishSync(
                VirtualEvent(
                    type = VirtualEventType.CORE_READY,
                    description = "UniversalVirtualCore is now in READY state."
                )
            )

            return VirtualResult.success(Unit)
        }
    }

    override fun shutdown(): VirtualResult<Unit> {
        lifecycleLock.withLock {
            if (currentState == CoreState.SHUTDOWN || currentState == CoreState.UNINITIALIZED) {
                return VirtualResult.success(Unit)
            }
            if (currentState != CoreState.READY && currentState != CoreState.FAILED) {
                return VirtualResult.failure(
                    code = VirtualErrorCode.INVALID_STATE_TRANSITION,
                    message = "Cannot shutdown from state " + currentState
                )
            }

            currentState = CoreState.STOPPING
            eventBusInstance.publishSync(
                VirtualEvent(
                    type = VirtualEventType.CORE_STOPPING,
                    description = "UniversalVirtualCore is stopping."
                )
            )

            backendInstance.shutdown()
            packageManagerInstance = null

            currentState = CoreState.SHUTDOWN
            eventBusInstance.publishSync(
                VirtualEvent(
                    type = VirtualEventType.CORE_SHUTDOWN,
                    description = "UniversalVirtualCore has shutdown."
                )
            )

            eventBusInstance.clear()
            UniversalVirtualCore.resetInstance()

            return VirtualResult.success(Unit)
        }
    }

 companion object {
 fun create(
 backend: VirtualizationBackend = FrameworkContainerBackend(),
 securityManager: IVirtualSecurityManager = VirtualSecurityManager()
 ): UniversalVirtualCore = VirtualCoreRuntime(backend, securityManager)
 }
}