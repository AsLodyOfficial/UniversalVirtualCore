package com.universal.virtual.common.events

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class VirtualEventBusImpl(
    private val asyncExecutor: ExecutorService = Executors.newCachedThreadPool { r ->
        Thread(r, "VirtualEventBus-AsyncWorker").apply { isDaemon = true }
    }
) : VirtualEventBus {

    private val globalListeners = CopyOnWriteArraySet<VirtualEventListener>()
    private val typedListeners = ConcurrentHashMap<VirtualEventType, CopyOnWriteArraySet<VirtualEventListener>>()

    override fun publishSync(event: VirtualEvent) {
        dispatchToListeners(event)
    }

    override fun publishAsync(event: VirtualEvent) {
        if (!asyncExecutor.isShutdown) {
            asyncExecutor.submit {
                dispatchToListeners(event)
            }
        }
    }

    private fun dispatchToListeners(event: VirtualEvent) {
        for (listener in globalListeners) {
            try {
                listener.onEvent(event)
            } catch (_: Throwable) {
                // Prevent listener errors from breaking event bus
            }
        }
        val specific = typedListeners[event.type]
        if (specific != null) {
            for (listener in specific) {
                try {
                    listener.onEvent(event)
                } catch (_: Throwable) {
                    // Prevent listener errors from breaking event bus
                }
            }
        }
    }

    override fun subscribe(listener: VirtualEventListener) {
        globalListeners.add(listener)
    }

    override fun subscribe(eventType: VirtualEventType, listener: VirtualEventListener) {
        typedListeners.computeIfAbsent(eventType) { CopyOnWriteArraySet() }.add(listener)
    }

    override fun unsubscribe(listener: VirtualEventListener) {
        globalListeners.remove(listener)
        typedListeners.values.forEach { it.remove(listener) }
    }

    override fun clear() {
        globalListeners.clear()
        typedListeners.clear()
    }

    fun shutdown() {
        clear()
        asyncExecutor.shutdown()
        try {
            if (!asyncExecutor.awaitTermination(500, TimeUnit.MILLISECONDS)) {
                asyncExecutor.shutdownNow()
            }
        } catch (_: InterruptedException) {
            asyncExecutor.shutdownNow()
        }
    }
}
