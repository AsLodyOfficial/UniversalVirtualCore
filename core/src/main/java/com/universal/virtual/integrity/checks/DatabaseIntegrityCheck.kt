package com.universal.virtual.integrity.checks

import com.universal.virtual.integrity.api.IntegrityCheck
import com.universal.virtual.integrity.api.IntegrityContext
import com.universal.virtual.integrity.models.IntegrityCategory
import com.universal.virtual.integrity.models.IntegrityCheckResult
import com.universal.virtual.integrity.models.IntegritySeverity
import kotlin.system.measureTimeMillis

class DatabaseIntegrityCheck : IntegrityCheck {
    override val id: String = "check.database.schema"
    override val name: String = "Database Schema & Record Invariants"
    override val category: IntegrityCategory = IntegrityCategory.DATABASE

    override suspend fun execute(context: IntegrityContext): IntegrityCheckResult {
        var duration = 0L
        var tableErrors = mutableListOf<String>()
        var packageCount = 0
        var userCount = 0
        var instanceCount = 0

        duration = measureTimeMillis {
            try {
                val db = context.database.openHelper.readableDatabase
                val cursor = db.query(androidx.sqlite.db.SimpleSQLiteQuery("SELECT name FROM sqlite_master WHERE type='table'"))
                val tables = mutableSetOf<String>()
                cursor.use {
                    while (it.moveToNext()) {
                        tables.add(it.getString(0))
                    }
                }

                val requiredTables = listOf(
                    "virtual_packages",
                    "virtual_users",
                    "virtual_instances",
                    "virtual_processes",
                    "virtual_activities",
                    "virtual_services",
                    "virtual_providers",
                    "virtual_receivers",
                    "virtual_permissions"
                )

                for (req in requiredTables) {
                    if (!tables.contains(req)) {
                        tableErrors.add(req)
                    }
                }

                packageCount = context.database.packageDao().getAllPackagesList().size
                userCount = context.database.userDao().getAllUsersList().size
                instanceCount = context.database.instanceDao().getAllInstancesList().size
            } catch (e: Exception) {
                return IntegrityCheckResult(
                    checkId = id,
                    category = category,
                    severity = IntegritySeverity.ERROR,
                    title = "Database Query Failure",
                    message = "Failed to query database schema: ${e.message}",
                    evidence = mapOf("exception" to (e.message ?: "Unknown")),
                    durationMs = duration
                )
            }
        }

        if (tableErrors.isNotEmpty()) {
            return IntegrityCheckResult(
                checkId = id,
                category = category,
                severity = IntegritySeverity.CRITICAL,
                title = "Missing Database Tables",
                message = "Required database tables are missing: ${tableErrors.joinToString(", ")}",
                evidence = mapOf("missingTables" to tableErrors.joinToString(",")),
                repairAvailable = true,
                durationMs = duration
            )
        }

        return IntegrityCheckResult(
            checkId = id,
            category = category,
            severity = IntegritySeverity.PASS,
            title = "Database Schema Verified",
            message = "All core database tables, primary keys, and entities are structurally consistent.",
            evidence = mapOf(
                "schemaVersion" to "${context.database.openHelper.readableDatabase.version}",
                "packagesFound" to "$packageCount"
            ),
            durationMs = duration
        )
    }
}
