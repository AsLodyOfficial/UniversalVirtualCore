package com.universal.virtual.common.identity

import java.io.Serializable

@JvmInline
value class VirtualProcessId(val value: Int) : Serializable, Comparable<VirtualProcessId> {
    init {
        require(value >= 0) { "VirtualProcessId cannot be negative: $value" }
    }

    override fun compareTo(other: VirtualProcessId): Int = value.compareTo(other.value)
    override fun toString(): String = "Process $value"

    companion object {
        val UNASSIGNED = VirtualProcessId(0)
    }
}
