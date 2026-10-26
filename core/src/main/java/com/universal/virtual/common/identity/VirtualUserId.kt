package com.universal.virtual.common.identity

import java.io.Serializable

@JvmInline
value class VirtualUserId(val value: Int) : Serializable, Comparable<VirtualUserId> {
    init {
        require(value >= 0) { "VirtualUserId cannot be negative: $value" }
    }

    override fun compareTo(other: VirtualUserId): Int = value.compareTo(other.value)
    override fun toString(): String = "User $value"

    companion object {
        val SYSTEM = VirtualUserId(0)
    }
}
