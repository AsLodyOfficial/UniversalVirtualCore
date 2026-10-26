package com.universal.virtual.api

enum class CoreState {
    UNINITIALIZED,
    INITIALIZING,
    READY,
    STOPPING,
    SHUTDOWN,
    FAILED;

    fun canTransitionTo(target: CoreState): Boolean {
        return when (this) {
            UNINITIALIZED -> target == INITIALIZING
            INITIALIZING -> target == READY || target == FAILED
            READY -> target == STOPPING
            STOPPING -> target == SHUTDOWN || target == FAILED
            SHUTDOWN -> target == INITIALIZING
            FAILED -> target == INITIALIZING || target == SHUTDOWN
        }
    }
}
