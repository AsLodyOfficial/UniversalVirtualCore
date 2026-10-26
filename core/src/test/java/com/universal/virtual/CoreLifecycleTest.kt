package com.universal.virtual

import android.content.Context
import com.google.common.truth.Truth.assertThat
import com.universal.virtual.api.*
import com.universal.virtual.common.result.VirtualErrorCode
import com.universal.virtual.runtime.VirtualCoreRuntime
import org.junit.Test
import java.io.File

class CoreLifecycleTest {

    private val dummyContext = org.mockito.Mockito.mock(Context::class.java)

    @Test
    fun testValidLifecycleTransitions() {
        val core = VirtualCoreRuntime.create()
        assertThat(core.state()).isEqualTo(CoreState.UNINITIALIZED)

        val config = VirtualCoreConfig.createDefault(File("test_storage"))
        val initResult = core.initialize(dummyContext, config)

        assertThat(initResult.isSuccess).isTrue()
        assertThat(core.state()).isEqualTo(CoreState.READY)
        assertThat(UniversalVirtualCore.isInitialized()).isTrue()
        assertThat(UniversalVirtualCore.get()).isSameInstanceAs(core)

        val shutdownResult = core.shutdown()
        assertThat(shutdownResult.isSuccess).isTrue()
        assertThat(core.state()).isEqualTo(CoreState.SHUTDOWN)
        assertThat(UniversalVirtualCore.isInitialized()).isFalse()
    }

    @Test
    fun testDoubleInitializationFails() {
        val core = VirtualCoreRuntime.create()
        val config = VirtualCoreConfig.createDefault(File("test_storage"))

        val first = core.initialize(dummyContext, config)
        assertThat(first.isSuccess).isTrue()

        val second = core.initialize(dummyContext, config)
        assertThat(second.isFailure).isTrue()
        assertThat(second.errorOrNull()?.code).isEqualTo(VirtualErrorCode.ALREADY_INITIALIZED)

        core.shutdown()
    }

    @Test
    fun testInvalidConfigTransitionsToFailed() {
        val core = VirtualCoreRuntime.create()
        val invalidConfig = VirtualCoreConfig(
            storageRoot = File("test_storage"),
            maxVirtualUsers = -5 // Invalid
        )

        val result = core.initialize(dummyContext, invalidConfig)
        assertThat(result.isFailure).isTrue()
        assertThat(result.errorOrNull()?.code).isEqualTo(VirtualErrorCode.INVALID_ARGUMENT)
        assertThat(core.state()).isEqualTo(CoreState.FAILED)
    }
}
