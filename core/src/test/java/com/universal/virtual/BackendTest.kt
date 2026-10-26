package com.universal.virtual

import com.google.common.truth.Truth.assertThat
import com.universal.virtual.backend.FrameworkContainerBackend
import com.universal.virtual.common.capability.VirtualCapability
import org.junit.Test

class BackendTest {

    @Test
    fun testFrameworkContainerBackendLifecycle() {
        val backend = FrameworkContainerBackend()
        assertThat(backend.name).isEqualTo("FrameworkContainerBackend")

        val initResult = backend.initialize()
        assertThat(initResult.isSuccess).isTrue()

        val capabilities = backend.capabilities()
        assertThat(capabilities.isSupported(VirtualCapability.LIFECYCLE_ORCHESTRATION)).isTrue()
        assertThat(capabilities.isSupported(VirtualCapability.SECURITY_ISOLATION)).isTrue()
        assertThat(capabilities.isSupported(VirtualCapability.DIAGNOSTICS)).isTrue()
        assertThat(capabilities.isSupported(VirtualCapability.ACTIVITY_RUNTIME)).isFalse()

        val shutdownResult = backend.shutdown()
        assertThat(shutdownResult.isSuccess).isTrue()
    }
}
