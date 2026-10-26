package com.universal.virtual.common.events

fun interface VirtualEventListener {
    fun onEvent(event: VirtualEvent)
}
