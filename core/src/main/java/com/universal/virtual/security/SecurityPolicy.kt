package com.universal.virtual.security

import java.io.Serializable

data class SecurityPolicy(
    val enforceUserIsolation: Boolean = true,
    val enforcePackageIsolation: Boolean = true,
    val enforceInstanceIsolation: Boolean = true,
    val enforceProcessIsolation: Boolean = true,
    val blockPathTraversal: Boolean = true
) : Serializable
