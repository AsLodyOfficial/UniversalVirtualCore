package com.universal.virtual.backend

import com.universal.virtual.api.CoreState
import com.universal.virtual.api.CoreVersion
import com.universal.virtual.common.capability.VirtualCapabilities
import com.universal.virtual.common.capability.VirtualCapability
import com.universal.virtual.common.result.VirtualErrorCode
import com.universal.virtual.common.result.VirtualResult
import com.universal.virtual.diagnostics.DiagnosticSnapshot
import com.universal.virtual.diagnostics.SystemMetrics

/**
 * Framework-container virtualization backend.
 *
 * NOTE: Guest execution is not implemented in Phase 1.
 * Phase 1 only establishes the container lifecycle, security boundaries, and diagnostics.
 */
class FrameworkContainerBackend : VirtualizationBackend {

    override val name: String = "FrameworkContainerBackend"

    private var isInitialized = false
    private var initTimeMs: Long = 0L

    private val supportedCapabilities = VirtualCapabilities(
        setOf(
            VirtualCapability.LIFECYCLE_ORCHESTRATION,
            VirtualCapability.SECURITY_ISOLATION,
            VirtualCapability.DIAGNOSTICS
        )
    )

    override fun initialize(): VirtualResult<Unit> {
        if (isInitialized) {
            return VirtualResult.failure(
                code = VirtualErrorCode.ALREADY_INITIALIZED,
                message = "FrameworkContainerBackend is already initialized."
            )
        }
        isInitialized = true
        initTimeMs = System.currentTimeMillis()
        return VirtualResult.success(Unit)
    }

    override fun shutdown(): VirtualResult<Unit> {
        isInitialized = false
        initTimeMs = 0L
        return VirtualResult.success(Unit)
    }

    override fun capabilities(): VirtualCapabilities = supportedCapabilities

    override fun diagnostics(): DiagnosticSnapshot {
        return DiagnosticSnapshot(
            timestamp = System.currentTimeMillis(),
            coreState = if (isInitialized) CoreState.READY else CoreState.UNINITIALIZED,
            coreVersion = CoreVersion.CURRENT,
            backendName = name,
            backendCapabilities = supportedCapabilities,
            virtualUserCount = 0,
            virtualPackageCount = 0,
            virtualInstanceCount = 0,
            virtualProcessCount = 0,
            systemMetrics = SystemMetrics.capture(initTimeMs)
        )
    }
}
