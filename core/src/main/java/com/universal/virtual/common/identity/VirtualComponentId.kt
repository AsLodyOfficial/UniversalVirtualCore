package com.universal.virtual.common.identity

import java.io.Serializable

data class VirtualComponentId(
    val packageId: VirtualPackageId,
    val className: String
) : Serializable {
    init {
        require(className.isNotBlank()) { "Component className cannot be blank" }
    }

    val flattenedName: String get() = "${packageId.packageName}/$className"
    override fun toString(): String = flattenedName
}
