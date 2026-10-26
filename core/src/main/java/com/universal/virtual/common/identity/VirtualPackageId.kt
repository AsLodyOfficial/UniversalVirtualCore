package com.universal.virtual.common.identity

import java.io.Serializable

@JvmInline
value class VirtualPackageId(val packageName: String) : Serializable, Comparable<VirtualPackageId> {
    init {
        require(packageName.isNotBlank()) { "VirtualPackageId cannot be blank" }
        require(packageName.contains('.')) { "VirtualPackageId must contain at least one dot: $packageName" }
    }

    val value: String get() = packageName

    override fun compareTo(other: VirtualPackageId): Int = packageName.compareTo(other.packageName)
    override fun toString(): String = packageName

    companion object {
        fun of(packageName: String): VirtualPackageId = VirtualPackageId(packageName)
    }
}

