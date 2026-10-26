package com.universal.virtual.common.events

import com.universal.virtual.common.identity.VirtualIdentity
import java.io.Serializable

data class VirtualEvent(
    val type: VirtualEventType,
    val identity: VirtualIdentity? = null,
    val timestampMs: Long = System.currentTimeMillis(),
    val payload: Map<String, String> = emptyMap(),
    val description: String = ""
) : Serializable
