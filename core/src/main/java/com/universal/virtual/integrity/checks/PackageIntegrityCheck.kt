package com.universal.virtual.integrity.checks

import com.universal.virtual.integrity.api.IntegrityCheck
import com.universal.virtual.integrity.api.IntegrityContext
import com.universal.virtual.integrity.models.IntegrityCategory
import com.universal.virtual.integrity.models.IntegrityCheckResult
import com.universal.virtual.integrity.models.IntegrityObject
import com.universal.virtual.integrity.models.IntegrityScope
import com.universal.virtual.integrity.models.IntegritySeverity
import com.universal.virtual.parser.ApkValidator
import java.io.File
import kotlin.system.measureTimeMillis

class PackageIntegrityCheck : IntegrityCheck {
    override val id: String = "check.package.integrity"
    override val name: String = "Package Registration & Metadata Integrity"
    override val category: IntegrityCategory = IntegrityCategory.PACKAGE

    override suspend fun execute(context: IntegrityContext): IntegrityCheckResult {
        var duration = 0L
        val invalidPackages = mutableListOf<String>()
        var totalChecked = 0
        val validator = ApkValidator()

        duration = measureTimeMillis {
            val packages = context.database.packageDao().getAllPackagesList()
            val filtered = when (val s = context.scope) {
                is IntegrityScope.Package -> packages.filter { it.packageName == s.packageId }
                else -> packages
            }

            for (pkg in filtered) {
                totalChecked++
                if (pkg.packageName.isBlank() || pkg.versionCode <= 0 || pkg.minSdk <= 0) {
                    invalidPackages.add("${pkg.packageName} (invalid metadata)")
                    continue
                }

                val apkFile = File(pkg.apkPath)
                if (apkFile.exists()) {
                    val validationRes = validator.validate(apkFile)
                    if (validationRes.isFailure) {
                        invalidPackages.add("${pkg.packageName} (${validationRes.errorOrNull()?.message})")
                    }
                }
            }
        }

        if (invalidPackages.isNotEmpty()) {
            return IntegrityCheckResult(
                checkId = id,
                category = category,
                severity = IntegritySeverity.ERROR,
                title = "Invalid Package Integrity",
                message = "Package metadata or APK structural validation failed: ${invalidPackages.joinToString("; ")}",
                affectedObject = invalidPackages.firstOrNull()?.let { IntegrityObject.Package(it, it) },
                evidence = mapOf("invalidList" to invalidPackages.joinToString(";")),
                repairAvailable = true,
                durationMs = duration
            )
        }

        return IntegrityCheckResult(
            checkId = id,
            category = category,
            severity = IntegritySeverity.PASS,
            title = "Package Integrity Verified",
            message = "All $totalChecked packages have valid metadata, SDK targets, and well-formed APK packages.",
            evidence = mapOf("checkedPackages" to "$totalChecked"),
            durationMs = duration
        )
    }
}
