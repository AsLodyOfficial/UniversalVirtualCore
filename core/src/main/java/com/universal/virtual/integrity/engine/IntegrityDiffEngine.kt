package com.universal.virtual.integrity.engine

import com.universal.virtual.integrity.models.IntegrityCheckResult
import com.universal.virtual.integrity.models.IntegrityDiff
import com.universal.virtual.integrity.models.IntegrityReport
import com.universal.virtual.integrity.models.IntegritySeverity

class IntegrityDiffEngine {

    fun computeDiff(previous: IntegrityReport?, current: IntegrityReport): IntegrityDiff {
        if (previous == null) {
            return IntegrityDiff(
                previousReportId = null,
                currentReportId = current.reportId,
                newErrors = current.results.filter { it.severity == IntegritySeverity.ERROR || it.severity == IntegritySeverity.CRITICAL },
                resolvedErrors = emptyList(),
                newWarnings = current.results.filter { it.severity == IntegritySeverity.WARNING },
                resolvedWarnings = emptyList(),
                persistentIssues = emptyList(),
                overallHealthChanged = false
            )
        }

        val prevErrorMap = previous.results
            .filter { it.severity == IntegritySeverity.ERROR || it.severity == IntegritySeverity.CRITICAL }
            .associateBy { it.checkId }

        val currErrorMap = current.results
            .filter { it.severity == IntegritySeverity.ERROR || it.severity == IntegritySeverity.CRITICAL }
            .associateBy { it.checkId }

        val prevWarnMap = previous.results
            .filter { it.severity == IntegritySeverity.WARNING }
            .associateBy { it.checkId }

        val currWarnMap = current.results
            .filter { it.severity == IntegritySeverity.WARNING }
            .associateBy { it.checkId }

        val newErrors = currErrorMap.values.filter { !prevErrorMap.containsKey(it.checkId) }
        val resolvedErrors = prevErrorMap.values.filter { !currErrorMap.containsKey(it.checkId) }

        val newWarnings = currWarnMap.values.filter { !prevWarnMap.containsKey(it.checkId) }
        val resolvedWarnings = prevWarnMap.values.filter { !currWarnMap.containsKey(it.checkId) }

        val persistentIssues = currErrorMap.values.filter { prevErrorMap.containsKey(it.checkId) } +
                currWarnMap.values.filter { prevWarnMap.containsKey(it.checkId) }

        val healthChanged = previous.overallSeverity != current.overallSeverity ||
                previous.environmentState != current.environmentState

        return IntegrityDiff(
            previousReportId = previous.reportId,
            currentReportId = current.reportId,
            newErrors = newErrors,
            resolvedErrors = resolvedErrors,
            newWarnings = newWarnings,
            resolvedWarnings = resolvedWarnings,
            persistentIssues = persistentIssues,
            overallHealthChanged = healthChanged
        )
    }
}
