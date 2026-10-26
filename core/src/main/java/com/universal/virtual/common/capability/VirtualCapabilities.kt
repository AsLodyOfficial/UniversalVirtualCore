package com.universal.virtual.common.capability

import com.universal.virtual.common.result.VirtualErrorCode
import com.universal.virtual.common.result.VirtualResult
import java.io.Serializable

data class VirtualCapabilities(
    val supported: Set<VirtualCapability> = emptySet()
) : Serializable {

    fun isSupported(capability: VirtualCapability): Boolean =
        supported.contains(capability)

    fun hasAll(capabilities: Set<VirtualCapability>): Boolean =
        supported.containsAll(capabilities)

    fun validateSupport(capability: VirtualCapability): VirtualResult<Unit> {
        return if (isSupported(capability)) {
            VirtualResult.success(Unit)
        } else {
            VirtualResult.failure(
                code = VirtualErrorCode.UNSUPPORTED,
                message = "Capability $capability is not supported by the active backend."
            )
        }
    }

    companion object {
        val NONE = VirtualCapabilities(emptySet())
    }
}
