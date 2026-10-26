package com.universal.virtual

import com.google.common.truth.Truth.assertThat
import com.universal.virtual.common.events.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class EventBusTest {

    private lateinit var eventBus: VirtualEventBusImpl

    @Before
    fun setUp() {
        eventBus = VirtualEventBusImpl()
    }

    @After
    fun tearDown() {
        eventBus.shutdown()
    }

    @Test
    fun testSynchronousPublishAndSubscribe() {
        val received = mutableListOf<VirtualEvent>()
        val listener = VirtualEventListener { event -> received.add(event) }

        eventBus.subscribe(listener)

        val event = VirtualEvent(
            type = VirtualEventType.CORE_READY,
            description = "Engine ready"
        )
        eventBus.publishSync(event)

        assertThat(received).hasSize(1)
        assertThat(received[0].type).isEqualTo(VirtualEventType.CORE_READY)
        assertThat(received[0].description).isEqualTo("Engine ready")
    }

    @Test
    fun testTypedListenerReceivesOnlySubscribedType() {
        val readyEvents = mutableListOf<VirtualEvent>()
        val shutdownEvents = mutableListOf<VirtualEvent>()

        eventBus.subscribe(VirtualEventType.CORE_READY) { readyEvents.add(it) }
        eventBus.subscribe(VirtualEventType.CORE_SHUTDOWN) { shutdownEvents.add(it) }

        eventBus.publishSync(VirtualEvent(type = VirtualEventType.CORE_READY))
        eventBus.publishSync(VirtualEvent(type = VirtualEventType.CORE_STOPPING))

        assertThat(readyEvents).hasSize(1)
        assertThat(shutdownEvents).isEmpty()
    }

    @Test
    fun testAsynchronousPublish() {
        val received = CopyOnWriteArrayList<VirtualEvent>()
        val latch = CountDownLatch(1)

        eventBus.subscribe { event ->
            received.add(event)
            latch.countDown()
        }

        eventBus.publishAsync(VirtualEvent(type = VirtualEventType.CORE_READY))

        val success = latch.await(2, TimeUnit.SECONDS)
        assertThat(success).isTrue()
        assertThat(received).hasSize(1)
    }

    @Test
    fun testUnsubscribeStopsReceivingEvents() {
        val received = mutableListOf<VirtualEvent>()
        val listener = VirtualEventListener { received.add(it) }

        eventBus.subscribe(listener)
        eventBus.publishSync(VirtualEvent(type = VirtualEventType.CORE_READY))
        assertThat(received).hasSize(1)

        eventBus.unsubscribe(listener)
        eventBus.publishSync(VirtualEvent(type = VirtualEventType.CORE_STOPPING))
        assertThat(received).hasSize(1)
    }
}
