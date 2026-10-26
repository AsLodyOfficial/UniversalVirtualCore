package com.universal.virtual.common.identity

import java.io.Serializable

data class VirtualIdentity(
    val userId: VirtualUserId,
    val packageId: VirtualPackageId,
    val instanceId: VirtualInstanceId = VirtualInstanceId.DEFAULT,
    val processId: VirtualProcessId = VirtualProcessId.UNASSIGNED
) : Serializable {

    val compositeKey: String
        get() = "${userId.value}:${packageId.packageName}:${instanceId.value}:${processId.value}"

    fun withProcess(processId: VirtualProcessId): VirtualIdentity =
        copy(processId = processId)

    fun withInstance(instanceId: VirtualInstanceId): VirtualIdentity =
        copy(instanceId = instanceId)

    override fun toString(): String =
        "VirtualIdentity(userId=$userId, packageId=$packageId, instanceId=$instanceId, processId=$processId)"

    companion object {
        fun of(
            userId: Int,
            packageName: String,
            instanceId: Int = 0,
            processId: Int = 0
        ): VirtualIdentity {
            return VirtualIdentity(
                userId = VirtualUserId(userId),
                packageId = VirtualPackageId(packageName),
                instanceId = VirtualInstanceId(instanceId),
                processId = VirtualProcessId(processId)
            )
        }
    }
}
