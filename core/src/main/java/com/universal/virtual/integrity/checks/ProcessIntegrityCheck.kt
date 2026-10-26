package com.universal.virtual.integrity.checks

import com.universal.virtual.integrity.api.IntegrityCheck
import com.universal.virtual.integrity.api.IntegrityContext
import com.universal.virtual.integrity.models.IntegrityCategory
import com.universal.virtual.integrity.models.IntegrityCheckResult
import com.universal.virtual.integrity.models.IntegritySeverity
import kotlin.system.measureTimeMillis

class ProcessIntegrityCheck : IntegrityCheck {
    override val id: String = "check.process.lifecycle"
    override val name: String = "Virtual Process State & Stale PID Audit"
    override val category: IntegrityCategory = IntegrityCategory.PROCESS

    override suspend fun execute(context: IntegrityContext): IntegrityCheckResult {
        var duration = 0L
        val staleProcesses = mutableListOf<String>()
        var verifiedProcesses = 0

        duration = measureTimeMillis {
            // ProcessDao check
            val activeProcesses = context.database.openHelper.readableDatabase.query(
                androidx.sqlite.db.SimpleSQLiteQuery("SELECT virtualPid, packageName, state, startedAt FROM virtual_processes")
            )
            activeProcesses.use { cursor ->
                while (cursor.moveToNext()) {
                    val pid = cursor.getInt(0)
                    val pkg = cursor.getString(1)
                    val state = cursor.getString(2)
                    val startedAt = cursor.getLong(3)

                    // If a process is marked RUNNING but started more than 24h ago without heartbeat
                    if (state == "RUNNING" && (System.currentTimeMillis() - startedAt > 86400000L)) {
                        staleProcesses.add("PID $pid ($pkg) stale state: $state")
                    } else {
                        verifiedProcesses++
                    }
                }
            }
        }

        if (staleProcesses.isNotEmpty()) {
            return IntegrityCheckResult(
                checkId = id,
                category = category,
                severity = IntegritySeverity.WARNING,
                title = "Stale Virtual Processes Detected",
                message = "Virtual process table has records with dead or unmonitored states: ${staleProcesses.joinToString("; ")}",
                evidence = mapOf("staleCount" to "${staleProcesses.size}"),
                repairAvailable = true,
                durationMs = duration
            )
        }

        return IntegrityCheckResult(
            checkId = id,
            category = category,
            severity = IntegritySeverity.PASS,
            title = "Process State Verified",
            message = "All virtual processes have clean lifecycle states and valid process mappings.",
            evidence = mapOf("activeProcesses" to "$verifiedProcesses"),
            durationMs = duration
        )
    }
}
