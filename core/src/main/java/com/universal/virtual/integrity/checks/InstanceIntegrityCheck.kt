package com.universal.virtual.integrity.checks

import com.universal.virtual.integrity.api.IntegrityCheck
import com.universal.virtual.integrity.api.IntegrityContext
import com.universal.virtual.integrity.models.IntegrityCategory
import com.universal.virtual.integrity.models.IntegrityCheckResult
import com.universal.virtual.integrity.models.IntegrityObject
import com.universal.virtual.integrity.models.IntegrityScope
import com.universal.virtual.integrity.models.IntegritySeverity
import kotlin.system.measureTimeMillis

class InstanceIntegrityCheck : IntegrityCheck {
    override val id: String = "check.instance.hierarchy"
    override val name: String = "Instance ↔ Package ↔ User Relationships"
    override val category: IntegrityCategory = IntegrityCategory.INSTANCE

    override suspend fun execute(context: IntegrityContext): IntegrityCheckResult {
        var duration = 0L
        val orphanInstances = mutableListOf<String>()
        var instanceCount = 0

        duration = measureTimeMillis {
            val instances = context.database.instanceDao().getAllInstancesList()
            val packages = context.database.packageDao().getAllPackagesList().map { it.packageName }.toSet()
            val users = context.database.userDao().getAllUsersList().map { it.userId }.toSet()

            val filtered = when (val s = context.scope) {
                is IntegrityScope.Instance -> instances.filter { it.instanceId.toString() == s.instanceId }
                is IntegrityScope.Package -> instances.filter { it.packageName == s.packageId }
                is IntegrityScope.User -> instances.filter { it.userId.toLong() == s.userId }
                else -> instances
            }

            instanceCount = filtered.size
            for (inst in filtered) {
                if (!packages.contains(inst.packageName)) {
                    orphanInstances.add("Instance #${inst.instanceId} references missing package '${inst.packageName}'")
                }
                if (inst.userId != 0 && !users.contains(inst.userId)) {
                    orphanInstances.add("Instance #${inst.instanceId} references missing User '${inst.userId}'")
                }
            }
        }

        if (orphanInstances.isNotEmpty()) {
            return IntegrityCheckResult(
                checkId = id,
                category = category,
                severity = IntegritySeverity.ERROR,
                title = "Orphaned Virtual Instances",
                message = "Virtual instances violate user or package relational integrity: ${orphanInstances.joinToString("; ")}",
                affectedObject = null,
                evidence = mapOf("orphans" to orphanInstances.joinToString(";")),
                repairAvailable = true,
                durationMs = duration
            )
        }

        return IntegrityCheckResult(
            checkId = id,
            category = category,
            severity = IntegritySeverity.PASS,
            title = "Virtual Instance Hierarchies Consistent",
            message = "All $instanceCount instances properly link to registered packages and users.",
            evidence = mapOf("verifiedInstances" to "$instanceCount"),
            durationMs = duration
        )
    }
}
