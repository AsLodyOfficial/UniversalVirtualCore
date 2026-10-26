package com.universal.virtual.integrity.checks

import com.universal.virtual.integrity.api.IntegrityCheck
import com.universal.virtual.integrity.api.IntegrityContext
import com.universal.virtual.integrity.models.IntegrityCategory
import com.universal.virtual.integrity.models.IntegrityCheckResult
import com.universal.virtual.integrity.models.IntegrityObject
import com.universal.virtual.integrity.models.IntegrityScope
import com.universal.virtual.integrity.models.IntegritySeverity
import com.universal.virtual.parser.ApkParser
import java.io.File
import kotlin.system.measureTimeMillis

class PackageMetadataIntegrityCheck : IntegrityCheck {
    override val id: String = "check.package.metadata_alignment"
    override val name: String = "APK Manifest ↔ Database Metadata Alignment"
    override val category: IntegrityCategory = IntegrityCategory.PACKAGE

    override suspend fun execute(context: IntegrityContext): IntegrityCheckResult {
        var duration = 0L
        val mismatches = mutableListOf<String>()
        var verified = 0
        val parser = ApkParser()

        duration = measureTimeMillis {
            val packages = context.database.packageDao().getAllPackagesList()
            val filtered = when (val s = context.scope) {
                is IntegrityScope.Package -> packages.filter { it.packageName == s.packageId }
                else -> packages
            }

            for (pkg in filtered) {
                val apkFile = File(pkg.apkPath)
                if (apkFile.exists()) {
                    val parseRes = parser.parse(apkFile)
                    if (parseRes.isSuccess) {
                        val parsed = parseRes.getOrNull()!!
                        if (parsed.packageName != pkg.packageName) {
                            mismatches.add("${pkg.packageName}: package name mismatch (${parsed.packageName})")
                        } else if (parsed.versionCode != pkg.versionCode) {
                            mismatches.add("${pkg.packageName}: version code mismatch (DB: ${pkg.versionCode}, APK: ${parsed.versionCode})")
                        } else {
                            verified++
                        }
                    }
                }
            }
        }

        if (mismatches.isNotEmpty()) {
            return IntegrityCheckResult(
                checkId = id,
                category = category,
                severity = IntegritySeverity.ERROR,
                title = "Package Metadata Mismatches",
                message = "Metadata in Room database diverges from physical APK manifests: ${mismatches.joinToString("; ")}",
                affectedObject = mismatches.firstOrNull()?.let { IntegrityObject.Package(it, it) },
                evidence = mapOf("mismatches" to mismatches.joinToString(";")),
                repairAvailable = true,
                durationMs = duration
            )
        }

        return IntegrityCheckResult(
            checkId = id,
            category = category,
            severity = IntegritySeverity.PASS,
            title = "Package Manifests & Database Aligned",
            message = "Parsed manifest metadata matches Room database records for all $verified packages.",
            evidence = mapOf("alignedPackages" to "$verified"),
            durationMs = duration
        )
    }
}
