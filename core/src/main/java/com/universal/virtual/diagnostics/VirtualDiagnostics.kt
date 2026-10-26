package com.universal.virtual.diagnostics

import com.universal.virtual.api.CoreState
import com.universal.virtual.api.CoreVersion
import com.universal.virtual.api.interfaces.IVirtualDiagnostics
import com.universal.virtual.common.capability.VirtualCapabilities
import java.util.concurrent.CopyOnWriteArrayList

class VirtualDiagnostics(
    private val stateSupplier: () -> CoreState,
    private val versionSupplier: () -> CoreVersion,
    private val backendNameSupplier: () -> String,
    private val capabilitiesSupplier: () -> VirtualCapabilities,
    private val uptimeSupplier: () -> Long
) : IVirtualDiagnostics {

    private val eventLogs = CopyOnWriteArrayList<String>()

    override fun captureSnapshot(): DiagnosticSnapshot {
        return DiagnosticSnapshot(
            timestamp = System.currentTimeMillis(),
            coreState = stateSupplier(),
            coreVersion = versionSupplier(),
            backendName = backendNameSupplier(),
            backendCapabilities = capabilitiesSupplier(),
            virtualUserCount = 0,
            virtualPackageCount = 0,
            virtualInstanceCount = 0,
            virtualProcessCount = 0,
            systemMetrics = getSystemMetrics()
        )
    }

    override fun getSystemMetrics(): SystemMetrics {
        return SystemMetrics.capture(uptimeSupplier())
    }

    override fun logDiagnosticEvent(tag: String, message: String, isWarning: Boolean) {
        val level = if (isWarning) "WARN" else "INFO"
        val timestamp = System.currentTimeMillis()
        val entry = "[$timestamp][$level][$tag] $message"
        eventLogs.add(entry)
        if (eventLogs.size > 200) {
            eventLogs.removeAt(0)
        }
    }

    fun getLogs(): List<String> = eventLogs.toList()
}