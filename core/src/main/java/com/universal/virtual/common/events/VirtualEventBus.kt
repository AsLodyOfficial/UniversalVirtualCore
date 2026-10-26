package com.universal.virtual.common.events

interface VirtualEventBus {
    fun publishSync(event: VirtualEvent)
    fun publishAsync(event: VirtualEvent)
    fun subscribe(listener: VirtualEventListener)
    fun subscribe(eventType: VirtualEventType, listener: VirtualEventListener)
    fun unsubscribe(listener: VirtualEventListener)
    fun clear()
}
