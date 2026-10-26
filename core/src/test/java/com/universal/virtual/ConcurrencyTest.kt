package com.universal.virtual

import android.content.Context
import com.google.common.truth.Truth.assertThat
import com.universal.virtual.api.CoreState
import com.universal.virtual.api.VirtualCoreConfig
import com.universal.virtual.common.events.VirtualEvent
import com.universal.virtual.common.events.VirtualEventBusImpl
import com.universal.virtual.common.events.VirtualEventType
import com.universal.virtual.common.identity.VirtualIdentity
import com.universal.virtual.common.identity.VirtualUserId
import com.universal.virtual.runtime.VirtualCoreRuntime
import com.universal.virtual.security.SecurityValidationResult
import com.universal.virtual.security.VirtualSecurityManager
import org.junit.Test
import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class ConcurrencyTest {

    private val dummyContext = org.mockito.Mockito.mock(Context::class.java)

    @Test
    fun testSimultaneousInitializeIsThreadSafe() {
        val core = VirtualCoreRuntime.create()
        val config = VirtualCoreConfig.createDefault(File("test_storage"))
        val threadCount = 8
        val executor = Executors.newFixedThreadPool(threadCount)
        val latch = CountDownLatch(threadCount)
        val results = ConcurrentLinkedQueue<Boolean>()

        for (i in 0 until threadCount) {
            executor.submit {
                val res = core.initialize(dummyContext, config)
                results.add(res.isSuccess)
                latch.countDown()
            }
        }

        latch.await(3, TimeUnit.SECONDS)
        executor.shutdown()

        // Exactly one should succeed, others fail due to ALREADY_INITIALIZED
        val successCount = results.count { it }
        assertThat(successCount).isEqualTo(1)
        assertThat(core.state()).isEqualTo(CoreState.READY)

        core.shutdown()
    }

    @Test
    fun testConcurrentEventBusDispatch() {
        val eventBus = VirtualEventBusImpl()
        val receivedCount = java.util.concurrent.atomic.AtomicInteger(0)
        val publishCount = 100
        val threadCount = 4
        val executor = Executors.newFixedThreadPool(threadCount)
        val latch = CountDownLatch(publishCount)

        eventBus.subscribe { _ ->
            receivedCount.incrementAndGet()
            latch.countDown()
        }

        for (i in 0 until publishCount) {
            executor.submit {
                eventBus.publishAsync(VirtualEvent(type = VirtualEventType.CORE_READY))
            }
        }

        val completed = latch.await(3, TimeUnit.SECONDS)
        executor.shutdown()
        eventBus.shutdown()

        assertThat(completed).isTrue()
        assertThat(receivedCount.get()).isEqualTo(publishCount)
    }

    @Test
    fun testConcurrentSecurityValidation() {
        val securityManager = VirtualSecurityManager()
        val threadCount = 8
        val iterations = 500
        val executor = Executors.newFixedThreadPool(threadCount)
        val latch = CountDownLatch(threadCount)
        val failureDetected = java.util.concurrent.atomic.AtomicBoolean(false)

        for (i in 0 until threadCount) {
            executor.submit {
                for (j in 0 until iterations) {
                    val userA = VirtualUserId(1)
                    val userB = VirtualUserId(2)
                    val res = securityManager.validateUserAccess(userA, userB)
                    if (res != SecurityValidationResult.DENIED_USER_ISOLATION) {
                        failureDetected.set(true)
                    }

                    val idA = VirtualIdentity.of(1, "com.app.a", 0)
                    val idB = VirtualIdentity.of(1, "com.app.b", 0)
                    val resPkg = securityManager.validateInstanceAccess(idA, idB)
                    if (resPkg != SecurityValidationResult.DENIED_PACKAGE_IMPERSONATION) {
                        failureDetected.set(true)
                    }
                }
                latch.countDown()
            }
        }

        latch.await(3, TimeUnit.SECONDS)
        executor.shutdown()

        assertThat(failureDetected.get()).isFalse()
    }
}
