package com.universal.virtual.integrity.engine

import com.universal.virtual.integrity.api.IntegrityCheck
import com.universal.virtual.integrity.api.IntegrityCheckRegistry
import com.universal.virtual.integrity.api.IntegrityChecker
import com.universal.virtual.integrity.api.IntegrityContext
import com.universal.virtual.integrity.models.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import java.util.UUID
import kotlin.coroutines.coroutineContext

class IntegrityScanEngine(
    private val registry: IntegrityCheckRegistry = IntegrityCheckRegistryImpl(),
    private val contextProvider: () -> IntegrityContext
) : IntegrityChecker {

    private val _progress = MutableStateFlow(IntegrityProgress(0, 0, "Idle", 0f))
    val progress: StateFlow<IntegrityProgress> = _progress.asStateFlow()

    override suspend fun check(scope: IntegrityScope): IntegrityReport {
        val baseContext = contextProvider()
        val context = baseContext.copy(scope = scope)
        return runScan(context)
    }

    suspend fun runScan(context: IntegrityContext): IntegrityReport {
        val reportId = UUID.randomUUID().toString()
        val startedAt = System.currentTimeMillis()
        val allChecks = registry.getChecks()
        val total = allChecks.size
        val results = mutableListOf<IntegrityCheckResult>()

        var passed = 0
        var info = 0
        var warnings = 0
        var errors = 0
        var critical = 0

        _progress.value = IntegrityProgress(0, total, "Starting scan...", 0f)

        for ((index, check) in allChecks.withIndex()) {
            if (!coroutineContext.isActive) break

            _progress.value = IntegrityProgress(
                completed = index,
                total = total,
                currentCheck = check.name,
                percentage = if (total > 0) (index.toFloat() / total.toFloat()) else 0f
            )

            val result = try {
                check.execute(context)
            } catch (e: Exception) {
                IntegrityCheckResult(
                    checkId = check.id,
                    category = check.category,
                    severity = IntegritySeverity.ERROR,
                    title = "Check Execution Error",
                    message = "Unexpected error during ${check.name}: ${e.message}",
                    evidence = mapOf("exception" to (e.message ?: "Unknown")),
                    durationMs = 0L
                )
            }

            results.add(result)
            when (result.severity) {
                IntegritySeverity.PASS -> passed++
                IntegritySeverity.INFO -> info++
                IntegritySeverity.WARNING -> warnings++
                IntegritySeverity.ERROR -> errors++
                IntegritySeverity.CRITICAL -> critical++
            }
        }

        val completedAt = System.currentTimeMillis()
        val durationMs = completedAt - startedAt

        val overallSeverity = when {
            critical > 0 -> IntegritySeverity.CRITICAL
            errors > 0 -> IntegritySeverity.ERROR
            warnings > 0 -> IntegritySeverity.WARNING
            info > 0 -> IntegritySeverity.INFO
            else -> IntegritySeverity.PASS
        }

        val environmentState = when {
            critical > 0 -> EnvironmentIntegrityState.CRITICAL
            errors > 2 -> EnvironmentIntegrityState.CORRUPTED
            errors > 0 -> EnvironmentIntegrityState.DEGRADED
            warnings > 0 -> EnvironmentIntegrityState.HEALTHY_WITH_WARNINGS
            else -> EnvironmentIntegrityState.HEALTHY
        }

        _progress.value = IntegrityProgress(total, total, "Completed", 1.0f)

        return IntegrityReport(
            reportId = reportId,
            startedAt = startedAt,
            completedAt = completedAt,
            durationMs = durationMs,
            environmentState = environmentState,
            overallSeverity = overallSeverity,
            checksExecuted = results.size,
            passed = passed,
            informational = info,
            warnings = warnings,
            errors = errors,
            critical = critical,
            results = results
        )
    }
}
