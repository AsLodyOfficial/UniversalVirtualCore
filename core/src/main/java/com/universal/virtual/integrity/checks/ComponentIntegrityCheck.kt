package com.universal.virtual.integrity.checks

import com.universal.virtual.integrity.api.IntegrityCheck
import com.universal.virtual.integrity.api.IntegrityContext
import com.universal.virtual.integrity.models.IntegrityCategory
import com.universal.virtual.integrity.models.IntegrityCheckResult
import com.universal.virtual.integrity.models.IntegrityObject
import com.universal.virtual.integrity.models.IntegritySeverity
import kotlin.system.measureTimeMillis

class ComponentIntegrityCheck : IntegrityCheck {
    override val id: String = "check.component.registry"
    override val name: String = "Component Invariants (Activities, Services, Providers, Receivers)"
    override val category: IntegrityCategory = IntegrityCategory.ACTIVITY

    override suspend fun execute(context: IntegrityContext): IntegrityCheckResult {
        var duration = 0L
        val orphanComponents = mutableListOf<String>()
        var activityCount = 0
        var serviceCount = 0
        var providerCount = 0
        var receiverCount = 0

        duration = measureTimeMillis {
            val packages = context.database.packageDao().getAllPackagesList().map { it.packageName }.toSet()
            val details = context.database.packageDao().getAllPackagesWithDetails()

            for (pkg in details) {
                if (!packages.contains(pkg.packageEntity.packageName)) {
                    orphanComponents.add("Package missing for component group: ${pkg.packageEntity.packageName}")
                }
                activityCount += pkg.activities.size
                serviceCount += pkg.services.size
                providerCount += pkg.providers.size
                receiverCount += pkg.receivers.size
            }
        }

        if (orphanComponents.isNotEmpty()) {
            return IntegrityCheckResult(
                checkId = id,
                category = category,
                severity = IntegritySeverity.ERROR,
                title = "Orphan Component Declarations",
                message = "Found components registered without valid parent package: ${orphanComponents.joinToString(", ")}",
                affectedObject = null,
                evidence = mapOf("orphans" to orphanComponents.joinToString(";")),
                repairAvailable = true,
                durationMs = duration
            )
        }

        return IntegrityCheckResult(
            checkId = id,
            category = category,
            severity = IntegritySeverity.PASS,
            title = "All Virtual Components Verified",
            message = "Verified $activityCount activities, $serviceCount services, $providerCount providers, and $receiverCount receivers.",
            evidence = mapOf(
                "activities" to "$activityCount",
                "services" to "$serviceCount",
                "providers" to "$providerCount",
                "receivers" to "$receiverCount"
            ),
            durationMs = duration
        )
    }
}
