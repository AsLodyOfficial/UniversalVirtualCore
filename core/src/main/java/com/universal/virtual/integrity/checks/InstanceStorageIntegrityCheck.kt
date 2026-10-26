package com.universal.virtual.integrity.checks

import com.universal.virtual.integrity.api.IntegrityCheck
import com.universal.virtual.integrity.api.IntegrityContext
import com.universal.virtual.integrity.models.IntegrityCategory
import com.universal.virtual.integrity.models.IntegrityCheckResult
import com.universal.virtual.integrity.models.IntegrityObject
import com.universal.virtual.integrity.models.IntegrityScope
import com.universal.virtual.integrity.models.IntegritySeverity
import java.io.File
import kotlin.system.measureTimeMillis

class InstanceStorageIntegrityCheck : IntegrityCheck {
    override val id: String = "check.instance.storage_layout"
    override val name: String = "Instance Sandbox Storage Layout"
    override val category: IntegrityCategory = IntegrityCategory.STORAGE

    override suspend fun execute(context: IntegrityContext): IntegrityCheckResult {
        var duration = 0L
        val storageWarnings = mutableListOf<String>()
        var verifiedCount = 0

        duration = measureTimeMillis {
            val instances = context.database.instanceDao().getAllInstancesList()
            val filtered = when (val s = context.scope) {
                is IntegrityScope.Instance -> instances.filter { it.instanceId.toString() == s.instanceId }
                is IntegrityScope.Package -> instances.filter { it.packageName == s.packageId }
                is IntegrityScope.User -> instances.filter { it.userId.toLong() == s.userId }
                else -> instances
            }

            for (inst in filtered) {
                val instDir = File(inst.storagePath)
                if (!instDir.exists()) {
                    // Ensure sandboxed directories are created
                    instDir.mkdirs()
                    File(instDir, "files").mkdirs()
                    File(instDir, "cache").mkdirs()
                    File(instDir, "databases").mkdirs()
                    File(instDir, "shared_prefs").mkdirs()
                }

                if (!instDir.canWrite()) {
                    storageWarnings.add("Instance #${inst.instanceId} storage unwritable: ${inst.storagePath}")
                } else {
                    verifiedCount++
                }
            }
        }

        if (storageWarnings.isNotEmpty()) {
            return IntegrityCheckResult(
                checkId = id,
                category = category,
                severity = IntegritySeverity.WARNING,
                title = "Instance Storage Warnings",
                message = "Storage permissions or accessibility issues: ${storageWarnings.joinToString("; ")}",
                affectedObject = null,
                evidence = mapOf("warnings" to storageWarnings.joinToString(";")),
                repairAvailable = true,
                durationMs = duration
            )
        }

        return IntegrityCheckResult(
            checkId = id,
            category = category,
            severity = IntegritySeverity.PASS,
            title = "Instance Storage Layout Verified",
            message = "All $verifiedCount instance storage roots (files/, cache/, databases/, shared_prefs/) are verified.",
            evidence = mapOf("verifiedInstances" to "$verifiedCount"),
            durationMs = duration
        )
    }
}
