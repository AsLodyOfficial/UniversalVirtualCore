package com.universal.virtual

import com.google.common.truth.Truth.assertThat
import com.universal.virtual.api.CoreState
import com.universal.virtual.api.CoreVersion
import com.universal.virtual.common.capability.VirtualCapabilities
import com.universal.virtual.common.capability.VirtualCapability
import com.universal.virtual.diagnostics.VirtualDiagnostics
import org.junit.Test

class DiagnosticsTest {

    @Test
    fun testDiagnosticsSnapshotCollection() {
        val capabilities = VirtualCapabilities(setOf(VirtualCapability.LIFECYCLE_ORCHESTRATION, VirtualCapability.DIAGNOSTICS))
        val diagnostics = VirtualDiagnostics(
            stateSupplier = { CoreState.READY },
            versionSupplier = { CoreVersion.CURRENT },
            backendNameSupplier = { "TestBackend" },
            capabilitiesSupplier = { capabilities },
            uptimeSupplier = { System.currentTimeMillis() - 5000L }
        )

        val snapshot = diagnostics.captureSnapshot()

        assertThat(snapshot.coreState).isEqualTo(CoreState.READY)
        assertThat(snapshot.backendName).isEqualTo("TestBackend")
        assertThat(snapshot.backendCapabilities.isSupported(VirtualCapability.DIAGNOSTICS)).isTrue()
        assertThat(snapshot.systemMetrics.maxMemoryBytes).isGreaterThan(0L)
        assertThat(snapshot.systemMetrics.runtimeUptimeMs).isGreaterThan(0L)
    }

    @Test
    fun testDiagnosticsLogging() {
        val diagnostics = VirtualDiagnostics(
            stateSupplier = { CoreState.READY },
            versionSupplier = { CoreVersion.CURRENT },
            backendNameSupplier = { "TestBackend" },
            capabilitiesSupplier = { VirtualCapabilities.NONE },
            uptimeSupplier = { 0L }
        )

        diagnostics.logDiagnosticEvent("INIT", "Initialized successfully", false)
        diagnostics.logDiagnosticEvent("SECURITY", "Suspicious probe detected", true)

        val logs = diagnostics.getLogs()
        assertThat(logs).hasSize(2)
        assertThat(logs[0]).contains("[INFO][INIT] Initialized successfully")
        assertThat(logs[1]).contains("[WARN][SECURITY] Suspicious probe detected")
    }
}
