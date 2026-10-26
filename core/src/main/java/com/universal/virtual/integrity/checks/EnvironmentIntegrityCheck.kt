package com.universal.virtual.integrity.checks

import com.universal.virtual.integrity.api.IntegrityCheck
import com.universal.virtual.integrity.api.IntegrityContext
import com.universal.virtual.integrity.models.IntegrityCategory
import com.universal.virtual.integrity.models.IntegrityCheckResult
import com.universal.virtual.integrity.models.IntegritySeverity
import kotlin.system.measureTimeMillis

class EnvironmentIntegrityCheck : IntegrityCheck {
    override val id: String = "check.environment.core"
    override val name: String = "Environment & Core Subsystems Health"
    override val category: IntegrityCategory = IntegrityCategory.ENVIRONMENT

    override suspend fun execute(context: IntegrityContext): IntegrityCheckResult {
        var duration = 0L
        var isDbOk = false
        var isStorageOk = false

        duration = measureTimeMillis {
            isDbOk = try {
                context.database.isOpen || context.database.openHelper.readableDatabase.isOpen
            } catch (_: Exception) {
                false
            }
            isStorageOk = context.rootStorageDir.exists() && context.rootStorageDir.canWrite()
        }

        if (!isStorageOk) {
            return IntegrityCheckResult(
                checkId = id,
                category = category,
                severity = IntegritySeverity.CRITICAL,
                title = "Virtual Storage Root Inaccessible",
                message = "The virtual root storage directory at ${context.rootStorageDir.name} is missing or unwritable.",
                evidence = mapOf("storagePath" to context.rootStorageDir.name, "exists" to "${context.rootStorageDir.exists()}"),
                repairAvailable = true,
                durationMs = duration
            )
        }

        if (!isDbOk) {
            return IntegrityCheckResult(
                checkId = id,
                category = category,
                severity = IntegritySeverity.CRITICAL,
                title = "Virtual Database Unreachable",
                message = "Unable to open readable connection to Room Virtual Database.",
                evidence = mapOf("databaseOpen" to "false"),
                repairAvailable = false,
                durationMs = duration
            )
        }

        return IntegrityCheckResult(
            checkId = id,
            category = category,
            severity = IntegritySeverity.PASS,
            title = "Environment & Core Subsystems Healthy",
            message = "Virtual core runtime, storage root, and database are active and verified.",
            evidence = mapOf("storage" to "PASS", "database" to "PASS"),
            durationMs = duration
        )
    }
}
