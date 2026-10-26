package com.universal.virtual.integrity.models

import java.io.Serializable

data class IntegrityCheckResult(
    val checkId: String,
    val category: IntegrityCategory,
    val severity: IntegritySeverity,
    val title: String,
    val message: String,
    val affectedObject: IntegrityObject? = null,
    val evidence: Map<String, String> = emptyMap(),
    val repairAvailable: Boolean = false,
    val durationMs: Long = 0L
) : Serializable
