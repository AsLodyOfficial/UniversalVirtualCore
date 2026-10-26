package com.universal.virtual.integrity.models

enum class EnvironmentIntegrityState {
    HEALTHY,
    HEALTHY_WITH_WARNINGS,
    DEGRADED,
    CORRUPTED,
    CRITICAL,
    UNKNOWN
}
