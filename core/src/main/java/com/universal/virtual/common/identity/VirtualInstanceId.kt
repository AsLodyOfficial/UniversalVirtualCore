package com.universal.virtual.common.identity

import java.io.Serializable

@JvmInline
value class VirtualInstanceId(val value: Int) : Serializable, Comparable<VirtualInstanceId> {
    init {
        require(value >= 0) { "VirtualInstanceId cannot be negative: $value" }
    }

    override fun compareTo(other: VirtualInstanceId): Int = value.compareTo(other.value)
    override fun toString(): String = "Instance $value"

    companion object {
        val DEFAULT = VirtualInstanceId(0)
    }
}
