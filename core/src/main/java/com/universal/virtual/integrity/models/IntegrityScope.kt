package com.universal.virtual.integrity.models

sealed interface IntegrityScope {
    data object EntireEnvironment : IntegrityScope

    data class Package(
        val packageId: String
    ) : IntegrityScope

    data class User(
        val userId: Long
    ) : IntegrityScope

    data class Instance(
        val instanceId: String
    ) : IntegrityScope

    data object Database : IntegrityScope
    data object Storage : IntegrityScope
    data object Runtime : IntegrityScope
    data object Native : IntegrityScope
    data object Backend : IntegrityScope
}
