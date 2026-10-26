package com.universal.virtual

import com.google.common.truth.Truth.assertThat
import com.universal.virtual.common.capability.VirtualCapabilities
import com.universal.virtual.common.capability.VirtualCapability
import com.universal.virtual.common.result.VirtualErrorCode
import org.junit.Test

class CapabilityTest {

    @Test
    fun testCapabilityVerification() {
        val caps = VirtualCapabilities(
            setOf(VirtualCapability.LIFECYCLE_ORCHESTRATION, VirtualCapability.DIAGNOSTICS)
        )

        assertThat(caps.isSupported(VirtualCapability.LIFECYCLE_ORCHESTRATION)).isTrue()
        assertThat(caps.isSupported(VirtualCapability.DIAGNOSTICS)).isTrue()
        assertThat(caps.isSupported(VirtualCapability.PACKAGE_MANAGEMENT)).isFalse()

        val supportedValidation = caps.validateSupport(VirtualCapability.DIAGNOSTICS)
        assertThat(supportedValidation.isSuccess).isTrue()

        val unsupportedValidation = caps.validateSupport(VirtualCapability.PACKAGE_MANAGEMENT)
        assertThat(unsupportedValidation.isFailure).isTrue()
        assertThat(unsupportedValidation.errorOrNull()?.code).isEqualTo(VirtualErrorCode.UNSUPPORTED)
    }
}
