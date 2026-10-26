package com.universal.virtual.integrity.models

import java.io.Serializable

data class IntegrityDiff(
    val previousReportId: String?,
    val currentReportId: String,
    val newErrors: List<IntegrityCheckResult>,
    val resolvedErrors: List<IntegrityCheckResult>,
    val newWarnings: List<IntegrityCheckResult>,
    val resolvedWarnings: List<IntegrityCheckResult>,
    val persistentIssues: List<IntegrityCheckResult>,
    val overallHealthChanged: Boolean
) : Serializable
