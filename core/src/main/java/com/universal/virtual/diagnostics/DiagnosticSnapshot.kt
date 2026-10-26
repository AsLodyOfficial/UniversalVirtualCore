package com.universal.virtual.diagnostics

import com.universal.virtual.api.CoreState
import com.universal.virtual.api.CoreVersion
import com.universal.virtual.common.capability.VirtualCapabilities
import java.io.Serializable

data class DiagnosticSnapshot(
    val timestamp: Long,
    val coreState: CoreState,
    val coreVersion: CoreVersion,
    val backendName: String,
    val backendCapabilities: VirtualCapabilities,
    val virtualUserCount: Int,
    val virtualPackageCount: Int,
    val virtualInstanceCount: Int,
    val virtualProcessCount: Int,
    val systemMetrics: SystemMetrics
) : Serializable
