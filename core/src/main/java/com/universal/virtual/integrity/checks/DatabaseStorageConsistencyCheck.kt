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

class DatabaseStorageConsistencyCheck : IntegrityCheck {
    override val id: String = "check.consistency.database_storage"
    override val name: String = "Database ↔ Storage Source Consistency"
    override val category: IntegrityCategory = IntegrityCategory.STORAGE

    override suspend fun execute(context: IntegrityContext): IntegrityCheckResult {
        var duration = 0L
        val missingApkPackages = mutableListOf<String>()
        val unreadableApkPackages = mutableListOf<String>()
        var totalChecked = 0

        duration = measureTimeMillis {
            val packages = context.database.packageDao().getAllPackagesList()
            val filtered = when (val s = context.scope) {
                is IntegrityScope.Package -> packages.filter { it.packageName == s.packageId }
                else -> packages
            }

            for (pkg in filtered) {
                totalChecked++
                val apkFile = File(pkg.apkPath)
                if (!apkFile.exists()) {
                    missingApkPackages.add(pkg.packageName)
                } else if (!apkFile.canRead() || apkFile.length() == 0L) {
                    unreadableApkPackages.add(pkg.packageName)
                }
            }
        }

        if (missingApkPackages.isNotEmpty()) {
            return IntegrityCheckResult(
                checkId = id,
                category = category,
                severity = IntegritySeverity.ERROR,
                title = "Database ↔ Storage Divergence",
                message = "Database records reference missing source.apk files for: ${missingApkPackages.joinToString(", ")}",
                affectedObject = missingApkPackages.firstOrNull()?.let { IntegrityObject.Package(it, it) },
                evidence = mapOf(
                    "missingCount" to "${missingApkPackages.size}",
                    "packages" to missingApkPackages.joinToString(",")
                ),
                repairAvailable = true,
                durationMs = duration
            )
        }

        if (unreadableApkPackages.isNotEmpty()) {
            return IntegrityCheckResult(
                checkId = id,
                category = category,
                severity = IntegritySeverity.ERROR,
                title = "Corrupt/Unreadable Source APKs",
                message = "Source APK files are unreadable or 0 bytes: ${unreadableApkPackages.joinToString(", ")}",
                affectedObject = unreadableApkPackages.firstOrNull()?.let { IntegrityObject.Package(it, it) },
                evidence = mapOf("unreadableCount" to "${unreadableApkPackages.size}"),
                repairAvailable = true,
                durationMs = duration
            )
        }

        return IntegrityCheckResult(
            checkId = id,
            category = category,
            severity = IntegritySeverity.PASS,
            title = "Database & Storage Fully Synchronized",
            message = "All $totalChecked registered packages have valid, readable source APKs in isolated storage.",
            evidence = mapOf("verifiedPackages" to "$totalChecked"),
            durationMs = duration
        )
    }
}
