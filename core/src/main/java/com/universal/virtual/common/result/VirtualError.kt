package com.universal.virtual.common.result

import java.io.Serializable

data class VirtualError(
    val code: VirtualErrorCode,
    val message: String,
    val cause: Throwable? = null,
    val recoverable: Boolean = false
) : Serializable {
    override fun toString(): String =
        "VirtualError(code=$code, message='$message', recoverable=$recoverable)"
}
