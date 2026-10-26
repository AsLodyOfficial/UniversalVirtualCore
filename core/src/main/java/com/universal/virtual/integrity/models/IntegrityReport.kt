package com.universal.virtual.integrity.models

import java.io.Serializable

data class IntegrityReport(
    val reportId: String,
    val startedAt: Long,
    val completedAt: Long,
    val durationMs: Long,
    val environmentState: EnvironmentIntegrityState,
    val overallSeverity: IntegritySeverity,
    val checksExecuted: Int,
    val passed: Int,
    val informational: Int,
    val warnings: Int,
    val errors: Int,
    val critical: Int,
    val results: List<IntegrityCheckResult>
) : Serializable {

    val healthScore: Int
        get() {
            if (critical > 0) return 0
            if (errors > 0) {
                val penalty = (errors * 15) + (warnings * 5)
                return (100 - penalty).coerceIn(10, 69)
            }
            if (warnings > 0) {
                val penalty = warnings * 5
                return (100 - penalty).coerceIn(70, 99)
            }
            return 100
        }
}
