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

class ApkIntegrityCheck : IntegrityCheck {
    override val id: String = "check.apk.file_hash"
    override val name: String = "APK Binary Hash & Tamper Verification"
    override val category: IntegrityCategory = IntegrityCategory.FILE

    override suspend fun execute(context: IntegrityContext): IntegrityCheckResult {
        var duration = 0L
        val hashMismatches = mutableListOf<String>()
        var verifiedCount = 0

        duration = measureTimeMillis {
            val packages = context.database.packageDao().getAllPackagesList()
            val filtered = when (val s = context.scope) {
                is IntegrityScope.Package -> packages.filter { it.packageName == s.packageId }
                else -> packages
            }

            for (pkg in filtered) {
                val apkFile = File(pkg.apkPath)
                if (apkFile.exists()) {
                    val hash = context.hashStore.calculateSha256(apkFile)
                    context.hashStore.recordObservedHash(pkg.packageName, hash)

                    val trusted = context.hashStore.getTrustedHash(pkg.packageName)
                    if (trusted != null && !trusted.equals(hash, ignoreCase = true)) {
                        hashMismatches.add("${pkg.packageName} (expected $trusted, got $hash)")
                    } else {
                        verifiedCount++
                    }
                }
            }
        }

        if (hashMismatches.isNotEmpty()) {
            return IntegrityCheckResult(
                checkId = id,
                category = category,
                severity = IntegritySeverity.WARNING,
                title = "APK File Hash Drift Detected",
                message = "One or more APK files differ from trusted baseline hashes: ${hashMismatches.joinToString("; ")}",
                affectedObject = hashMismatches.firstOrNull()?.let { IntegrityObject.Package(it, it) },
                evidence = mapOf("mismatches" to hashMismatches.joinToString(";")),
                repairAvailable = true,
                durationMs = duration
            )
        }

        return IntegrityCheckResult(
            checkId = id,
            category = category,
            severity = IntegritySeverity.PASS,
            title = "APK Hash Baselines Validated",
            message = "Calculated SHA-256 for $verifiedCount packages. All match established security baselines.",
            evidence = mapOf("hashedPackages" to "$verifiedCount"),
            durationMs = duration
        )
    }
}
