package com.universal.virtual.common.result

import java.io.Serializable

sealed class VirtualResult<out T> : Serializable {

    data class Success<out T>(val data: T) : VirtualResult<T>()
    data class Failure(val error: VirtualError) : VirtualResult<Nothing>()

    val isSuccess: Boolean get() = this is Success
    val isFailure: Boolean get() = this is Failure

    fun getOrNull(): T? = when (this) {
        is Success -> data
        is Failure -> null
    }

    fun getOrThrow(): T = when (this) {
        is Success -> data
        is Failure -> throw VirtualException(error)
    }

    fun errorOrNull(): VirtualError? = when (this) {
        is Success -> null
        is Failure -> error
    }

    inline fun <R> map(transform: (T) -> R): VirtualResult<R> = when (this) {
        is Success -> Success(transform(data))
        is Failure -> this
    }

    inline fun <R> flatMap(transform: (T) -> VirtualResult<R>): VirtualResult<R> = when (this) {
        is Success -> transform(data)
        is Failure -> this
    }

    inline fun onSuccess(action: (T) -> Unit): VirtualResult<T> {
        if (this is Success) action(data)
        return this
    }

    inline fun onFailure(action: (VirtualError) -> Unit): VirtualResult<T> {
        if (this is Failure) action(error)
        return this
    }

    inline fun <R> fold(onSuccess: (T) -> R, onFailure: (VirtualError) -> R): R = when (this) {
        is Success -> onSuccess(data)
        is Failure -> onFailure(error)
    }

    companion object {
        fun <T> success(data: T): VirtualResult<T> = Success(data)

        fun failure(error: VirtualError): VirtualResult<Nothing> = Failure(error)

        fun failure(
            code: VirtualErrorCode,
            message: String,
            cause: Throwable? = null,
            recoverable: Boolean = false
        ): VirtualResult<Nothing> = Failure(
            VirtualError(
                code = code,
                message = message,
                cause = cause,
                recoverable = recoverable
            )
        )
    }
}
