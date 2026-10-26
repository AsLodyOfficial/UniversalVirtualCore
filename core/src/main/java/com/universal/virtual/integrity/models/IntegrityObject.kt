package com.universal.virtual.integrity.models

import java.io.Serializable

sealed interface IntegrityObject : Serializable {

    data class Package(
        val packageId: String,
        val packageName: String
    ) : IntegrityObject

    data class User(
        val userId: Long
    ) : IntegrityObject

    data class Instance(
        val instanceId: String,
        val packageId: String,
        val userId: Long
    ) : IntegrityObject

    data class Process(
        val virtualPid: Long
    ) : IntegrityObject

    data class Component(
        val packageId: String,
        val className: String
    ) : IntegrityObject

    data class File(
        val relativePath: String
    ) : IntegrityObject
}
