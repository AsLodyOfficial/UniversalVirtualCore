package com.universal.virtual.api

import java.io.Serializable

data class CoreVersion(
    val major: Int = 0,
    val minor: Int = 1,
    val patch: Int = 0,
    val suffix: String = "alpha-phase1"
) : Serializable {

    val formattedString: String
        get() = if (suffix.isNotBlank()) "$major.$minor.$patch-$suffix" else "$major.$minor.$patch"

    override fun toString(): String = formattedString

    companion object {
        val CURRENT = CoreVersion(0, 1, 0, "alpha-phase1")
        val API_VERSION = CoreVersion(1, 0, 0, "api")
        val BACKEND_VERSION = CoreVersion(1, 0, 0, "container")
    }
}
