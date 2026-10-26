package com.universal.virtual.api

import android.content.Context
import com.universal.virtual.api.interfaces.IVirtualPackageManager
import com.universal.virtual.api.interfaces.IVirtualSecurityManager
import com.universal.virtual.backend.VirtualizationBackend
import com.universal.virtual.common.events.VirtualEventBus
import com.universal.virtual.common.result.VirtualResult
import com.universal.virtual.diagnostics.VirtualDiagnostics

interface UniversalVirtualCore {

    fun initialize(context: Context, config: VirtualCoreConfig): VirtualResult<Unit>
    fun shutdown(): VirtualResult<Unit>
    fun state(): CoreState
    fun version(): CoreVersion
    fun config(): VirtualCoreConfig?
    fun backend(): VirtualizationBackend
    fun diagnostics(): VirtualDiagnostics
    fun eventBus(): VirtualEventBus
    fun securityManager(): IVirtualSecurityManager
    fun packageManager(): IVirtualPackageManager

    companion object {
        @Volatile
        private var instance: UniversalVirtualCore? = null

        fun get(): UniversalVirtualCore {
            return instance ?: throw IllegalStateException("UniversalVirtualCore is not initialized. Initialize the engine first.")
        }

        fun getOrNull(): UniversalVirtualCore? = instance

        fun isInitialized(): Boolean = instance?.state() == CoreState.READY

        @Synchronized
        fun registerInstance(core: UniversalVirtualCore) {
            instance = core
        }

        @Synchronized
        fun resetInstance() {
            instance = null
        }
    }
}