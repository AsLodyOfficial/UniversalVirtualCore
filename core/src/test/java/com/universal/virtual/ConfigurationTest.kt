package com.universal.virtual

import com.google.common.truth.Truth.assertThat
import com.universal.virtual.api.VirtualCoreConfig
import com.universal.virtual.common.result.VirtualErrorCode
import org.junit.Test
import java.io.File

class ConfigurationTest {

    @Test
    fun testValidConfigurationPasses() {
        val config = VirtualCoreConfig.createDefault(File("test_storage"))
        val result = config.validate()
        assertThat(result.isSuccess).isTrue()
    }

    @Test
    fun testInvalidConfigurationFails() {
        val invalidConfig = VirtualCoreConfig(
            storageRoot = File("test_storage"),
            maxVirtualProcesses = 0
        )
        val result = invalidConfig.validate()
        assertThat(result.isFailure).isTrue()
        assertThat(result.errorOrNull()?.code).isEqualTo(VirtualErrorCode.INVALID_ARGUMENT)
    }
}
