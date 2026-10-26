package com.universal.virtual.api

import com.universal.virtual.common.result.VirtualErrorCode
import com.universal.virtual.common.result.VirtualResult
import java.io.File
import java.io.Serializable

data class VirtualCoreConfig(
    val storageRoot: File,
    val debug: Boolean = false,
    val loggingEnabled: Boolean = true,
    val diagnosticsEnabled: Boolean = true,
    val maxVirtualUsers: Int = 16,
    val maxInstancesPerPackage: Int = 32,
    val maxVirtualProcesses: Int = 8,
    val backendConfiguration: Map<String, String> = emptyMap()
) : Serializable {

    fun validate(): VirtualResult<Unit> {
        if (maxVirtualUsers <= 0) {
            return VirtualResult.failure(
                code = VirtualErrorCode.INVALID_ARGUMENT,
                message = "maxVirtualUsers must be greater than 0, got $maxVirtualUsers"
            )
        }
        if (maxInstancesPerPackage <= 0) {
            return VirtualResult.failure(
                code = VirtualErrorCode.INVALID_ARGUMENT,
                message = "maxInstancesPerPackage must be greater than 0, got $maxInstancesPerPackage"
            )
        }
        if (maxVirtualProcesses <= 0) {
            return VirtualResult.failure(
                code = VirtualErrorCode.INVALID_ARGUMENT,
                message = "maxVirtualProcesses must be greater than 0, got $maxVirtualProcesses"
            )
        }
        if (storageRoot.name.isBlank()) {
            return VirtualResult.failure(
                code = VirtualErrorCode.INVALID_ARGUMENT,
                message = "storageRoot cannot have an empty name"
            )
        }
        return VirtualResult.success(Unit)
    }

    companion object {
        fun createDefault(storageRoot: File): VirtualCoreConfig = VirtualCoreConfig(
            storageRoot = storageRoot,
            debug = false,
            loggingEnabled = true,
            diagnosticsEnabled = true,
            maxVirtualUsers = 16,
            maxInstancesPerPackage = 32,
            maxVirtualProcesses = 8
        )
    }
}
