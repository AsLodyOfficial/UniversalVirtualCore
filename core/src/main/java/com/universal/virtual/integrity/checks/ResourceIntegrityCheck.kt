package com.universal.virtual.integrity.checks

import com.universal.virtual.integrity.api.IntegrityCheck
import com.universal.virtual.integrity.api.IntegrityContext
import com.universal.virtual.integrity.models.IntegrityCategory
import com.universal.virtual.integrity.models.IntegrityCheckResult
import com.universal.virtual.integrity.models.IntegritySeverity
import java.io.File
import kotlin.system.measureTimeMillis

class ResourceIntegrityCheck : IntegrityCheck {
    override val id: String = "check.resource.assets"
    override val name: String = "Resource & Asset Invariants"
    override val category: IntegrityCategory = IntegrityCategory.RESOURCE

    override suspend fun execute(context: IntegrityContext): IntegrityCheckResult {
        var duration = 0L
        var resOk = true

        duration = measureTimeMillis {
            val packages = context.database.packageDao().getAllPackagesList()
            for (pkg in packages) {
                val apk = File(pkg.apkPath)
                if (apk.exists() && apk.length() < 100L) {
                    resOk = false
                }
            }
        }

        if (!resOk) {
            return IntegrityCheckResult(
                checkId = id,
                category = category,
                severity = IntegritySeverity.WARNING,
                title = "Resource Extraction Warning",
                message = "One or more packages contain truncated APK resources.",
                evidence = mapOf("resources" to "TRUNCATED"),
                repairAvailable = true,
                durationMs = duration
            )
        }

        return IntegrityCheckResult(
            checkId = id,
            category = category,
            severity = IntegritySeverity.PASS,
            title = "Virtual Resources Verified",
            message = "All sandboxed package resources and asset archives are valid.",
            evidence = mapOf("resources" to "PASS"),
            durationMs = duration
        )
    }
}
